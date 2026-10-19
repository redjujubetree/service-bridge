
package top.redjujubetree.servicebridge.example.client.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

import top.redjujubetree.servicebridge.client.core.ServiceBridgeClient;
import top.redjujubetree.servicebridge.client.model.BridgeHttpResponse;
import top.redjujubetree.servicebridge.client.model.BridgeStoredResult;
import top.redjujubetree.servicebridge.example.client.ocr.OcrFileStorageService;
import top.redjujubetree.servicebridge.example.client.ocr.OcrResultQueryService;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/ocr")
public class OCRController {

    private final OcrFileStorageService fileStorageService;
    private final OcrResultQueryService resultQueryService;
    private final ServiceBridgeClient serviceBridgeClient;
    private final ObjectMapper objectMapper;

    public OCRController(
            OcrFileStorageService fileStorageService,
            OcrResultQueryService resultQueryService,
            ServiceBridgeClient serviceBridgeClient,
            ObjectMapper objectMapper) {
        this.fileStorageService = fileStorageService;
        this.resultQueryService = resultQueryService;
        this.serviceBridgeClient = serviceBridgeClient;
        this.objectMapper = objectMapper;
    }

    /**
     * OCR 文件上传与识别接口。
     *
     * 完整链路：
     * 1. 接收 multipart/form-data 文件。
     * 2. 将原始文件及元数据保存到数据库。
     * 3. 初始化 OCR 业务结果记录。
     * 4. 将 fileId 放入 JSON payload。
     * 5. 调用现有 ServiceBridgeClient。
     * 6. 直接返回 DeferredResult，由现有 ServiceBridge
     *    通过 MQ、Redis 通知和数据库兜底完成 HTTP 响应。
     *
     * 不在这里等待 DeferredResult 完成。
     * 不拆分为提交接口和结果轮询接口。
     */
    @PostMapping(consumes = "multipart/form-data")
    public DeferredResult<ResponseEntity<Map<String, Object>>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId)
            throws IOException {

        // ── 第一步：文件持久化 ──
        OcrFileStorageService.StoredFile stored = fileStorageService.save(file);

        // ── 第二步：构建 Bridge payload ──
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fileId", stored.getFileId());
        payload.put("originalName", stored.getOriginalName());
        payload.put("contentType", stored.getContentType());
        payload.put("fileSize", stored.getFileSize());
        JsonNode jsonPayload = objectMapper.valueToTree(payload);

        // ── 第三步：初始化 ocr_result 占位记录 ──
        resultQueryService.initializePending(stored.getFileId());

        // ── 第四步：发送 Bridge 请求 ──
        final DeferredResult<ResponseEntity<BridgeHttpResponse>> bridge;
        try {
            if (traceId != null && !traceId.trim().isEmpty()) {
                bridge = serviceBridgeClient.request("OCR", jsonPayload, traceId);
            } else {
                bridge = serviceBridgeClient.request("OCR", jsonPayload);
            }
        } catch (RuntimeException e) {
            resultQueryService.markSubmitFailed(
                    stored.getFileId(), "SUBMIT_FAILED", e.getClass().getSimpleName());
            throw e;
        }

        // ── 第五步：用 setResultHandler 在 bridge 结果回来时，合并 OCR 数据再返回 ──
        final DeferredResult<ResponseEntity<Map<String, Object>>> result = new DeferredResult<>();

        bridge.setResultHandler(bridgeResultObj -> {
            @SuppressWarnings("unchecked")
            ResponseEntity<BridgeHttpResponse> bridgeResp =
                    (ResponseEntity<BridgeHttpResponse>) bridgeResultObj;

            int httpStatus = bridgeResp != null
                    ? bridgeResp.getStatusCodeValue()
                    : 500;

            BridgeHttpResponse bridgeBody = bridgeResp != null
                    ? bridgeResp.getBody()
                    : null;

            // 此时 Server 侧事务已提交，ocr_result 表一定已写入
            Map<String, Object> ocrData =
                    resultQueryService.findByFileId(stored.getFileId());

            Map<String, Object> finalBody = new LinkedHashMap<>();
            finalBody.put("bridge", bridgeBody);
            finalBody.put("ocr", ocrData);

            result.setResult(ResponseEntity.status(httpStatus).body(finalBody));
        });

        return result;
    }

    /**
     * ServiceBridge 结果查询兜底接口。
     *
     * 与现有 ReportController 的 GET /reports/{requestId} 一样，
     * 它查询的是 ServiceBridge 请求结果，而不是让调用方正常轮询 OCR。
     *
     * requestId 必须是 ServiceBridge 的请求 ID，
     * 不是这里生成的 fileId。
     */
    @GetMapping("/{requestId}")
    public Optional<BridgeStoredResult> query(
            @PathVariable("requestId") String requestId) {

        return serviceBridgeClient.query(requestId);
    }
}
