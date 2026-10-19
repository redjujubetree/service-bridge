
package top.redjujubetree.servicebridge.example.server.handler;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import top.redjujubetree.servicebridge.example.server.ocr.OcrEngineClient;
import top.redjujubetree.servicebridge.example.server.ocr.OcrFileStorageService;
import top.redjujubetree.servicebridge.example.server.ocr.OcrResultService;
import top.redjujubetree.servicebridge.server.core.BridgeHandlerResult;
import top.redjujubetree.servicebridge.server.core.BridgeRequestContext;
import top.redjujubetree.servicebridge.server.core.BridgeRequestHandler;

// 以下ServiceBridge类型的包名和getter沿用你当前已运行的Handler。

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OCRHandler implements BridgeRequestHandler {

    private static final Logger log =
            LoggerFactory.getLogger(OCRHandler.class);

    private final OcrFileStorageService fileStorageService;
    private final OcrEngineClient ocrEngineClient;
    private final OcrResultService resultService;

    public OCRHandler(
            OcrFileStorageService fileStorageService,
            OcrEngineClient ocrEngineClient,
            OcrResultService resultService) {
        this.fileStorageService = fileStorageService;
        this.ocrEngineClient = ocrEngineClient;
        this.resultService = resultService;
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
            return BridgeHandlerResult.failure(
                    "OCR_FILE_ID_MISSING",
                    "请求缺少fileId"
            );
        }

        String requestId = context.getRequestId();

        try {
            resultService.markProcessing(fileId, requestId);

            // 从数据库读取并校验原始文件。
            OcrFileStorageService.StoredFile file =
                    fileStorageService.load(fileId);

            //TODO 实现大文件提交识别时，另存到公共文件系统中，地址传给OCR。

            // OCR协议适配层负责调用当前multipart OCR服务。
            OcrEngineClient.OcrResponse ocrResponse =
                    ocrEngineClient.recognize(file);

            if (ocrResponse.getHttpStatus() < 200 ||
                    ocrResponse.getHttpStatus() >= 300) {

                resultService.markFailed(
                        fileId,
                        requestId,
                        "OCR_HTTP_" + ocrResponse.getHttpStatus(),
                        "OCR服务返回非成功HTTP状态"
                );

                return BridgeHandlerResult.failure(
                        "OCR_ENGINE_FAILED",
                        "OCR服务返回非成功HTTP状态"
                );
            }

            log.info("ocrResponse.getContentType() : {}", ocrResponse.getContentType());
            log.info("ocrResponse.getBody() : {}", ocrResponse.getBody());
            // 第一步：将完整OCR结果保存到独立业务表。
            resultService.markSuccess(
                    fileId,
                    requestId,
                    ocrResponse.getContentType(),
                    ocrResponse.getBody()
            );

            /*
             * 第二步：ServiceBridge响应只携带结果引用。
             * 不把OCR完整响应再次塞进request_payload或响应结果。
             */
            Map<String, Object> reference = new LinkedHashMap<>();
            reference.put("fileId", fileId);
            reference.put("status", "SUCCESS");

            //TODO 删除数据库原始文件

            return BridgeHandlerResult.success(reference);

        } catch (Exception e) {
            log.error(
                    "OCR处理失败, fileId={}, requestId={}, errorType={}",
                    fileId,
                    requestId,
                    e.getClass().getSimpleName()
            );

            try {
                resultService.markFailed(
                        fileId,
                        requestId,
                        "OCR_PROCESSING_FAILED",
                        e.getClass().getSimpleName()
                );
            } catch (Exception dbException) {
                log.error(
                        "保存OCR失败状态失败, fileId={}",
                        fileId
                );
            }

            return BridgeHandlerResult.failure(
                    "OCR_PROCESSING_FAILED",
                    "OCR处理失败"
            );
        }
    }
}
