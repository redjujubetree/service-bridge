
package top.redjujubetree.servicebridge.example.server.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OcrResultService {

    private static final Logger log =
            LoggerFactory.getLogger(OcrResultService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OcrResultService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 开始处理OCR任务。
     *
     * resultId 在初始化业务结果记录时生成，
     * 这里仅更新状态和关联的ServiceBridge requestId。
     */
    @Transactional
    public void markProcessing(String fileId, String requestId) {
        jdbcTemplate.update(
                "UPDATE ocr_result " +
                        "SET status = 'PROCESSING', request_id = ?, " +
                        "started_at = COALESCE(started_at, CURRENT_TIMESTAMP(3)), " +
                        "error_code = NULL, error_message = NULL " +
                        "WHERE file_id = ?",
                requestId, fileId
        );
    }

    /**
     * 保存完整OCR结果到独立的ocr_result表。
     *
     * OCR原始响应只写入result_content，
     * 不放入ServiceBridge请求payload或响应payload。
     */
    @Transactional
    public void markSuccess(
            String fileId,
            String requestId,
            String contentType,
            String body) {

        jdbcTemplate.update(
                "UPDATE ocr_result " +
                        "SET status = 'SUCCESS', request_id = ?, " +
                        "result_type = ?, result_content = ?, " +
                        "error_code = NULL, error_message = NULL, " +
                        "finished_at = CURRENT_TIMESTAMP(3) " +
                        "WHERE file_id = ?",
                requestId, contentType, body, fileId
        );
    }

    /**
     * 保存OCR处理失败信息。
     * 不把异常堆栈或完整文件内容写入业务结果表。
     */
    @Transactional
    public void markFailed(
            String fileId,
            String requestId,
            String errorCode,
            String message) {

        jdbcTemplate.update(
                "UPDATE ocr_result " +
                        "SET status = 'FAILED', request_id = ?, " +
                        "error_code = ?, error_message = ?, " +
                        "finished_at = CURRENT_TIMESTAMP(3) " +
                        "WHERE file_id = ?",
                requestId, errorCode, message, fileId
        );
    }

    /**
     * 查询OCR结果表中已经保存的结果。
     * 返回完整结果供业务层使用，不经过ServiceBridge payload。
     */
    public Map<String, Object> findResult(String fileId) {
        return jdbcTemplate.query(
                "SELECT file_id, request_id, status, result_type, " +
                        "result_content, error_code, error_message, " +
                        "started_at, finished_at " +
                        "FROM ocr_result WHERE file_id = ?",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("fileId", rs.getString("file_id"));
                    result.put("requestId", rs.getString("request_id"));
                    result.put("status", rs.getString("status"));
                    result.put("resultType", rs.getString("result_type"));            result.put("resultContent", parseContent(rs.getString("result_content")));
                    result.put("resultContent", parseContent(rs.getString("result_content")));
                    result.put("errorCode", rs.getString("error_code"));
                    result.put("errorMessage", rs.getString("error_message"));
                    result.put("startedAt", rs.getTimestamp("started_at"));
                    result.put("finishedAt", rs.getTimestamp("finished_at"));

                    return result;
                },
                fileId
        );
    }

    private Object parseContent(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            log.warn("result_content 不是合法 JSON，按原始字符串返回");
            return raw;
        }
    }
}
