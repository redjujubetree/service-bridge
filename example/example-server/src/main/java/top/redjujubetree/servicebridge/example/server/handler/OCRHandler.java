package top.redjujubetree.servicebridge.example.server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import top.redjujubetree.servicebridge.example.server.ocr.InvoiceParserClient;
import top.redjujubetree.servicebridge.example.server.ocr.OcrBodyTrimmer;
import top.redjujubetree.servicebridge.example.server.ocr.OcrEngineClient;
import top.redjujubetree.servicebridge.example.server.ocr.OcrFileStorageService;
import top.redjujubetree.servicebridge.example.server.ocr.OcrResultService;
import top.redjujubetree.servicebridge.server.core.BridgeHandlerResult;
import top.redjujubetree.servicebridge.server.core.BridgeRequestContext;
import top.redjujubetree.servicebridge.server.core.BridgeRequestHandler;

@Component
public class OCRHandler implements BridgeRequestHandler {

    private static final Logger log =
            LoggerFactory.getLogger(OCRHandler.class);

    private final OcrFileStorageService fileStorageService;
    private final OcrEngineClient ocrEngineClient;
    private final OcrResultService resultService;
    private final InvoiceParserClient invoiceParserClient;
    private final OcrBodyTrimmer ocrBodyTrimmer;
    private final ObjectMapper objectMapper;

    public OCRHandler(
            OcrFileStorageService fileStorageService,
            OcrEngineClient ocrEngineClient,
            OcrResultService resultService,
            InvoiceParserClient invoiceParserClient,
            OcrBodyTrimmer ocrBodyTrimmer,
            ObjectMapper objectMapper) {
        this.fileStorageService = fileStorageService;
        this.ocrEngineClient = ocrEngineClient;
        this.resultService = resultService;
        this.invoiceParserClient = invoiceParserClient;
        this.ocrBodyTrimmer = ocrBodyTrimmer;
        this.objectMapper = objectMapper;
    }

    @Override
    public String businessType() {
        return "OCR";
    }

    @Override
    public BridgeHandlerResult handle(BridgeRequestContext context)
            throws Exception {

        JsonNode payload = context.getPayload();
        String fileId = payload.path("fileId").asText(null);

        if (fileId == null || fileId.trim().isEmpty()) {
            return BridgeHandlerResult.failure("OCR_FILE_ID_MISSING", "请求缺少fileId");
        }

        String requestId = context.getRequestId();

        try {
            resultService.markProcessing(fileId, requestId);

            // 从数据库读取并校验原始文件。
            OcrFileStorageService.StoredFile file = fileStorageService.load(fileId);
            if(file.getDeleted()==1){
                resultService.markFailed(fileId, requestId, "OCR_PROCESSING_FAILED", "图片不存在，请重新上传");
                return BridgeHandlerResult.failure("OCR_PROCESSING_FAILED", "图片不存在，请重新上传");
            }

            // 调用远程OCR服务。
            OcrEngineClient.OcrResponse ocrResponse = ocrEngineClient.recognize(file);
            if (ocrResponse.getHttpStatus() < 200 || ocrResponse.getHttpStatus() >= 300) {
                resultService.markFailed(fileId, requestId, "OCR_HTTP_" + ocrResponse.getHttpStatus(), "OCR服务返回非成功HTTP状态");
                return BridgeHandlerResult.failure("OCR_PROCESSING_FAILED", "OCR处理失败");
            }

            // OCR响应里会带图片base64，解析用不到，先流式裁掉再提取信息。
            String ocrBody = ocrBodyTrimmer.trim(ocrResponse.getBody());

            // 机器上没装Python时这里直接抛异常，由下面的catch统一转成失败响应。
            String invoiceJson = invoiceParserClient.parse(ocrBody);
            log.info("invoice_parser output : {}", invoiceJson);

            // 将OCR结果保存到独立业务表。
            resultService.markSuccess(fileId,requestId,ocrResponse.getContentType(),invoiceJson);

            //ServiceBridge响应，结果塞进request_payload或响应结果。
            JsonNode node;
            try {
                node = objectMapper.readTree(invoiceJson);
            } catch (JsonProcessingException e) {
                node = objectMapper.getNodeFactory().textNode(invoiceJson);
            }
            return BridgeHandlerResult.success(node);

        }  catch (Exception e) {
            log.error("OCR处理失败, fileId={}, requestId={}, errorType={}",fileId,requestId,e.getClass().getSimpleName());
            resultService.markFailed(fileId,requestId, "OCR_PROCESSING_FAILED", e.getClass().getSimpleName());
            return BridgeHandlerResult.failure("OCR_PROCESSING_FAILED", "OCR处理失败");
        }finally {
            //删除数据库原始文件
            fileStorageService.deleteData(fileId);
        }
    }
}
