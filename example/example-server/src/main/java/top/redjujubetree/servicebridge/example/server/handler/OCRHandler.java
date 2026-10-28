
package top.redjujubetree.servicebridge.example.server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import top.redjujubetree.servicebridge.example.server.ocr.OcrEngineClient;
import top.redjujubetree.servicebridge.example.server.ocr.OcrFileStorageService;
import top.redjujubetree.servicebridge.example.server.ocr.OcrResultService;
import top.redjujubetree.servicebridge.server.core.BridgeHandlerResult;
import top.redjujubetree.servicebridge.server.core.BridgeRequestContext;
import top.redjujubetree.servicebridge.server.core.BridgeRequestHandler;
import top.redjujubetree.servicebridge.server.exception.BridgeRetryableException;

// 以下ServiceBridge类型的包名和getter沿用你当前已运行的Handler。

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OCRHandler implements BridgeRequestHandler {

    private static final Logger log =
            LoggerFactory.getLogger(OCRHandler.class);

    private final OcrFileStorageService fileStorageService;
    private final OcrEngineClient ocrEngineClient;
    private final OcrResultService resultService;
    private final ObjectMapper objectMapper;

    public OCRHandler(
            OcrFileStorageService fileStorageService,
            OcrEngineClient ocrEngineClient,
            OcrResultService resultService,
            ObjectMapper objectMapper) {
        this.fileStorageService = fileStorageService;
        this.ocrEngineClient = ocrEngineClient;
        this.resultService = resultService;
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
                log.info("=======file.getDeleted()==1");
                return BridgeHandlerResult.failure("OCR_PROCESSING_FAILED", "OCR处理失败");
            }

            // OCR协议适配层负责调用当前multipart OCR服务。
            OcrEngineClient.OcrResponse ocrResponse = ocrEngineClient.recognize(file);

            if (ocrResponse.getHttpStatus() < 200 || ocrResponse.getHttpStatus() >= 300) {
                resultService.markFailed(fileId, requestId, "OCR_HTTP_" + ocrResponse.getHttpStatus(), "OCR服务返回非成功HTTP状态");
                return BridgeHandlerResult.failure("OCR_PROCESSING_FAILED", "OCR处理失败");
            }

            log.info("ocrResponse.getContentType() : {}", ocrResponse.getContentType());
            log.info("ocrResponse.getBody() : {}", ocrResponse.getBody());
            // 第一步：将完整OCR结果保存到独立业务表。
            resultService.markSuccess(fileId,requestId,ocrResponse.getContentType(),ocrResponse.getBody());
            //第二步：ServiceBridge响应，结果塞进request_payload或响应结果。
            JsonNode node;
            try {
                node = objectMapper.readTree(ocrResponse.getBody());
            } catch (JsonProcessingException e) {
                node = objectMapper.getNodeFactory().textNode(ocrResponse.getBody());
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
