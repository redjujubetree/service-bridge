
package top.redjujubetree.servicebridge.example.server.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OcrResultService {

    private static final Logger log =
            LoggerFactory.getLogger(OcrResultService.class);

    private final JdbcTemplate jdbcTemplate;

    public OcrResultService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 开始处理OCR任务。
     */
    public void markProcessing(String fileId, String requestId) {
        jdbcTemplate.update(
                "UPDATE ocr_result " +
                        "SET status = 'PROCESSING', request_id = ?, " +
                        "started_at = COALESCE(started_at, CURRENT_TIMESTAMP(3)), " +
                        "error_code = NULL, error_message = NULL " +
                        "WHERE file_id = ? AND status = 'PENDING' ",
                requestId, fileId
        );
    }

    /**
     * 保存OCR结果到独立的ocr_result表。
     */
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
                        "WHERE file_id = ? AND status='PROCESSING' ",
                requestId, contentType, body, fileId
        );
    }

    /**
     * 保存OCR处理失败信息。
     */
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
                        "WHERE file_id = ? AND status='PROCESSING' ",
                requestId, errorCode, message, fileId
        );
    }
}
