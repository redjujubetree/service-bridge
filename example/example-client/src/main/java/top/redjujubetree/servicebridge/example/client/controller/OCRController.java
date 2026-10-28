
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

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
     */
    @PostMapping(consumes = "multipart/form-data")
    public DeferredResult<ResponseEntity<BridgeHttpResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId)
            throws IOException {

        byte[] bytes = file.getBytes();
        String sha256 = sha256(bytes);

        //── 对比现有结果，判断是否需要重新识别 ──
        Map<String, Object> existing = resultQueryService.findBySha256(sha256);
        if (existing != null) {
            DeferredResult<ResponseEntity<BridgeHttpResponse>> deferred = new DeferredResult<>();
            deferred.setResult(ResponseEntity.ok(
                    BridgeHttpResponse.of(
                            null,
                            "SUCCESS",
                            null,
                            objectMapper.valueToTree(existing.get("resultContent")))));
            return deferred;
        }

        // ── 第一步：文件持久化 ──
        OcrFileStorageService.StoredFile stored = fileStorageService.save(file, bytes, sha256);

        // ── 第二步：构建 Bridge payload ──
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fileId", stored.getFileId());
        JsonNode jsonPayload = objectMapper.valueToTree(payload);

        // ── 第三步：初始化 ocr_result 占位记录 ──
        resultQueryService.initializePending(stored.getFileId() ,stored.getSha256());

        // ── 第四步：发送 Bridge 请求 ──
        DeferredResult<ResponseEntity<BridgeHttpResponse>> result;
        try {
            if (traceId != null && !traceId.trim().isEmpty()) {
                result = serviceBridgeClient.request("OCR", jsonPayload, traceId);
            } else {
                result = serviceBridgeClient.request("OCR", jsonPayload);
            }
        } catch (RuntimeException e) {
            resultQueryService.markSubmitFailed(
                    stored.getFileId(), "SUBMIT_FAILED", e.getClass().getSimpleName());
            throw e;
        }

        return result;
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                result.append(String.format("%02x", b & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前环境不支持SHA-256", e);
        }
    }

    /**
     * ServiceBridge 结果查询兜底接口。
     */
    @GetMapping("/{requestId}")
    public Optional<BridgeStoredResult> query(
            @PathVariable("requestId") String requestId) {

        return serviceBridgeClient.query(requestId);
    }
}
