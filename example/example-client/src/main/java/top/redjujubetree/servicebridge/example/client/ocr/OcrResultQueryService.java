package top.redjujubetree.servicebridge.example.client.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OcrResultQueryService {

    private static final Logger log =
            LoggerFactory.getLogger(OcrResultQueryService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OcrResultQueryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 上传完成后创建 OCR 结果占位记录。
     * 完整 OCR 结果后续由 Server 写入同一条记录。
     */
    @Transactional
    public void initializePending(String fileId ,String sha256) {
        jdbcTemplate.update(
                "INSERT INTO ocr_result (file_id, sha256, status) VALUES (?, ?, 'PENDING')",
                fileId, sha256
        );
    }

    /**
     * 记录提交阶段明确发生的同步异常。
     */
    @Transactional
    public void markSubmitFailed(
            String fileId,
            String errorCode,
            String errorMessage) {

        jdbcTemplate.update(
                "UPDATE ocr_result " +
                        "SET status = 'FAILED', error_code = ?, error_message = ?, " +
                        "finished_at = CURRENT_TIMESTAMP(3) " +
                        "WHERE file_id = ? AND status = 'PENDING'",
                errorCode,
                errorMessage,
                fileId
        );
    }

    /**
     * 按 sha256 查询独立 OCR 结果表。
     */
    public Map<String, Object> findBySha256(String sha256) {
        String sql = "SELECT file_id, request_id, status, result_type, " +
                "result_content, error_code, error_message, " +
                "started_at, finished_at " +
                "FROM ocr_result WHERE sha256 = ? AND status = 'SUCCESS' LIMIT 1";

        List<Map<String, Object>> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
            log.info("rs.getString(\"result_content\") : {}", rs.getString("result_content"));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileId", rs.getString("file_id"));
            result.put("requestId", rs.getString("request_id"));
            result.put("status", rs.getString("status"));
            result.put("resultType", rs.getString("result_type"));

            // 完整 OCR 识别结果
            result.put("resultContent", parseContent(rs.getString("result_content")));

            result.put("errorCode", rs.getString("error_code"));
            result.put("errorMessage", rs.getString("error_message"));
            result.put("startedAt", rs.getTimestamp("started_at"));
            result.put("finishedAt", rs.getTimestamp("finished_at"));
            return result;
        }, sha256);

        return rows.isEmpty() ? null : rows.get(0);
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