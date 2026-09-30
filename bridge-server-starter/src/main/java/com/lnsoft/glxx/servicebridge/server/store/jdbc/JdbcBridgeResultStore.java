package com.lnsoft.glxx.servicebridge.server.store.jdbc;

import com.lnsoft.glxx.servicebridge.protocol.BridgeProcessStatus;
import com.lnsoft.glxx.servicebridge.server.model.AcquireResult;
import com.lnsoft.glxx.servicebridge.server.model.BridgeProcessingRecord;
import com.lnsoft.glxx.servicebridge.server.store.BridgeResultStore;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class JdbcBridgeResultStore implements BridgeResultStore {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcBridgeResultStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AcquireResult tryAcquire(String requestId,
                                    String attemptToken,
                                    LocalDateTime startedAt,
                                    LocalDateTime expiresAt) {
        String sql = "UPDATE service_bridge_request "
                + "SET process_status = 'PROCESSING', processing_token = :attemptToken, "
                + "processing_started_at = :startedAt, processing_expires_at = :expiresAt, "
                + "updated_at = :startedAt, version = version + 1 "
                + "WHERE request_id = :requestId AND process_status IN ('PENDING', 'PROCESSING')";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("requestId", requestId)
                .addValue("attemptToken", attemptToken)
                .addValue("startedAt", Timestamp.valueOf(startedAt))
                .addValue("expiresAt", Timestamp.valueOf(expiresAt));
        if (jdbc.update(sql, params) == 1) {
            return AcquireResult.ACQUIRED;
        }
        Optional<BridgeProcessingRecord> current = find(requestId);
        if (!current.isPresent()) {
            return AcquireResult.REQUEST_NOT_FOUND;
        }
        if (current.get().getProcessStatus() == BridgeProcessStatus.SUCCESS) {
            return AcquireResult.ALREADY_SUCCESS;
        }
        if (current.get().getProcessStatus() == BridgeProcessStatus.FAILED) {
            return AcquireResult.ALREADY_FAILED;
        }
        throw new IllegalStateException("Unable to acquire request in status "
                + current.get().getProcessStatus() + ": " + requestId);
    }

    @Override
    public boolean completeSuccess(String requestId,
                                   String attemptToken,
                                   String resultPayload,
                                   LocalDateTime completedAt) {
        String sql = "UPDATE service_bridge_request "
                + "SET process_status = 'SUCCESS', result_payload = :resultPayload, "
                + "error_code = NULL, error_message = NULL, process_completed_at = :completedAt, "
                + "processing_token = NULL, processing_started_at = NULL, processing_expires_at = NULL, "
                + "updated_at = :completedAt, version = version + 1 "
                + "WHERE request_id = :requestId AND process_status = 'PROCESSING' "
                + "AND processing_token = :attemptToken";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("requestId", requestId)
                .addValue("attemptToken", attemptToken)
                .addValue("resultPayload", resultPayload)
                .addValue("completedAt", Timestamp.valueOf(completedAt));
        return jdbc.update(sql, params) == 1;
    }

    @Override
    public boolean completeFailure(String requestId,
                                   String attemptToken,
                                   String errorCode,
                                   String errorMessage,
                                   LocalDateTime completedAt) {
        return updateFailure(requestId, attemptToken, errorCode, errorMessage, completedAt, false);
    }

    @Override
    public boolean completeExpiredFailure(String requestId,
                                          String attemptToken,
                                          String errorCode,
                                          String errorMessage,
                                          LocalDateTime completedAt) {
        return updateFailure(requestId, attemptToken, errorCode, errorMessage, completedAt, true);
    }

    private boolean updateFailure(String requestId,
                                  String attemptToken,
                                  String errorCode,
                                  String errorMessage,
                                  LocalDateTime completedAt,
                                  boolean requireExpired) {
        String sql = "UPDATE service_bridge_request "
                + "SET process_status = 'FAILED', result_payload = NULL, "
                + "error_code = :errorCode, error_message = :errorMessage, "
                + "process_completed_at = :completedAt, processing_token = NULL, "
                + "processing_started_at = NULL, processing_expires_at = NULL, "
                + "updated_at = :completedAt, version = version + 1 "
                + "WHERE request_id = :requestId AND process_status = 'PROCESSING' "
                + "AND processing_token = :attemptToken";
        if (requireExpired) {
            sql += " AND processing_expires_at <= :completedAt";
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("requestId", requestId)
                .addValue("attemptToken", attemptToken)
                .addValue("errorCode", truncate(errorCode, 64))
                .addValue("errorMessage", truncate(errorMessage, 1000))
                .addValue("completedAt", Timestamp.valueOf(completedAt));
        return jdbc.update(sql, params) == 1;
    }

    @Override
    public boolean isClientWaiting(String requestId, LocalDateTime now) {
        String sql = "SELECT COUNT(*) FROM service_bridge_request "
                + "WHERE request_id = :requestId AND wait_status = 'WAITING' "
                + "AND client_deadline_at > :now";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("requestId", requestId)
                .addValue("now", Timestamp.valueOf(now));
        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return count != null && count > 0;
    }

    @Override
    public Optional<BridgeProcessingRecord> find(String requestId) {
        String sql = "SELECT request_id, business_type, process_status, processing_token, "
                + "processing_expires_at FROM service_bridge_request WHERE request_id = :requestId";
        List<BridgeProcessingRecord> rows = jdbc.query(
                sql,
                Collections.singletonMap("requestId", requestId),
                rowMapper());
        return rows.isEmpty() ? Optional.<BridgeProcessingRecord>empty() : Optional.of(rows.get(0));
    }

    private RowMapper<BridgeProcessingRecord> rowMapper() {
        return new RowMapper<BridgeProcessingRecord>() {
            @Override
            public BridgeProcessingRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
                BridgeProcessingRecord record = new BridgeProcessingRecord();
                record.setRequestId(rs.getString("request_id"));
                record.setBusinessType(rs.getString("business_type"));
                record.setProcessStatus(BridgeProcessStatus.valueOf(rs.getString("process_status")));
                record.setProcessingToken(rs.getString("processing_token"));
                Timestamp expiresAt = rs.getTimestamp("processing_expires_at");
                record.setProcessingExpiresAt(expiresAt == null ? null : expiresAt.toLocalDateTime());
                return record;
            }
        };
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
