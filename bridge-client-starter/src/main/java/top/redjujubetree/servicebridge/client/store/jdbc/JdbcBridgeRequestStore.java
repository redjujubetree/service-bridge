package top.redjujubetree.servicebridge.client.store.jdbc;

import top.redjujubetree.servicebridge.client.model.BridgeRequestRecord;
import top.redjujubetree.servicebridge.client.model.BridgeStoredResult;
import top.redjujubetree.servicebridge.client.store.BridgeRequestStore;
import top.redjujubetree.servicebridge.protocol.status.BridgeProcessStatus;
import top.redjujubetree.servicebridge.protocol.status.BridgeWaitStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcBridgeRequestStore implements BridgeRequestStore {

    private static final String SELECT_COLUMNS =
            "request_id, business_type, wait_status, process_status, "
                    + "result_payload, error_code, error_message";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcBridgeRequestStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void create(BridgeRequestRecord request) {
        String sql = "INSERT INTO service_bridge_request ("
                + "request_id, protocol_version, business_type, request_payload, trace_id, "
                + "wait_status, process_status, client_deadline_at, created_at, updated_at, version"
                + ") VALUES ("
                + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)";

        String requestJson = request.getRequestPayload();
        try (StringReader reader = new StringReader(requestJson)) {
            jdbc.getJdbcTemplate().update(sql, ps -> {
                ps.setString(1, request.getRequestId());
                ps.setString(2, request.getProtocolVersion());
                ps.setString(3, request.getBusinessType());
                ps.setCharacterStream(4, reader, requestJson.length());
                ps.setString(5, request.getTraceId());
                ps.setString(6, request.getWaitStatus().name());
                ps.setString(7, request.getProcessStatus().name());
                ps.setTimestamp(8, Timestamp.valueOf(request.getClientDeadlineAt()));
                Timestamp createdAt = Timestamp.valueOf(request.getCreatedAt());
                ps.setTimestamp(9, createdAt);
                ps.setTimestamp(10, createdAt);
            });
        }
    }

    @Override
    public boolean markSubmissionFailed(String requestId, String errorMessage, LocalDateTime failedAt) {
        String sql = "UPDATE service_bridge_request SET process_status = 'FAILED', "
                + "responded_at = CASE WHEN wait_status = 'WAITING' THEN :failedAt ELSE responded_at END, "
                + "wait_status = CASE WHEN wait_status = 'WAITING' THEN 'RESPONDED' ELSE wait_status END, "
                + "error_code = 'MQ_SEND_FAILED', error_message = :errorMessage, "
                + "result_payload = NULL, processing_token = NULL, processing_started_at = NULL, "
                + "processing_expires_at = NULL, process_completed_at = :failedAt, "
                + "updated_at = :failedAt, version = version + 1 "
                + "WHERE request_id = :requestId AND process_status IN ('PENDING', 'PROCESSING')";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("requestId", requestId)
                .addValue("errorMessage", errorMessage == null ? "MQ send failed"
                        : errorMessage.substring(0, Math.min(errorMessage.length(), 1000)))
                .addValue("failedAt", Timestamp.valueOf(failedAt));
        return jdbc.update(sql, params) == 1;
    }

    @Override
    public Optional<BridgeStoredResult> find(String requestId) {
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM service_bridge_request WHERE request_id = :requestId";
        List<BridgeStoredResult> rows = jdbc.query(
                sql,
                Collections.singletonMap("requestId", requestId),
                storedResultRowMapper());
        return rows.isEmpty() ? Optional.<BridgeStoredResult>empty() : Optional.of(rows.get(0));
    }

    @Override
    public boolean markRespondedIfWaiting(String requestId, LocalDateTime respondedAt) {
        String sql = "UPDATE service_bridge_request "
                + "SET wait_status = 'RESPONDED', responded_at = :respondedAt, "
                + "updated_at = :respondedAt, version = version + 1 "
                + "WHERE request_id = :requestId AND wait_status = 'WAITING' "
                + "AND process_status IN ('SUCCESS', 'FAILED')";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("requestId", requestId)
                .addValue("respondedAt", Timestamp.valueOf(respondedAt));
        return jdbc.update(sql, params) == 1;
    }

    @Override
    public boolean markTimedOutIfWaitingAndUnfinished(String requestId, LocalDateTime timedOutAt) {
        String sql = "UPDATE service_bridge_request "
                + "SET wait_status = 'TIMED_OUT', responded_at = :timedOutAt, "
                + "updated_at = :timedOutAt, version = version + 1 "
                + "WHERE request_id = :requestId AND wait_status = 'WAITING' "
                + "AND process_status IN ('PENDING', 'PROCESSING')";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("requestId", requestId)
                .addValue("timedOutAt", Timestamp.valueOf(timedOutAt));
        return jdbc.update(sql, params) == 1;
    }

    @Override
    public Map<String, BridgeStoredResult> findTerminal(Collection<String> requestIds) {
        if (requestIds == null || requestIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM service_bridge_request "
                + "WHERE request_id IN (:requestIds) AND process_status IN ('SUCCESS', 'FAILED')";
        List<BridgeStoredResult> rows = jdbc.query(
                sql,
                Collections.singletonMap("requestIds", requestIds),
                storedResultRowMapper());
        Map<String, BridgeStoredResult> result = new LinkedHashMap<String, BridgeStoredResult>();
        for (BridgeStoredResult row : rows) {
            result.put(row.getRequestId(), row);
        }
        return result;
    }

    private RowMapper<BridgeStoredResult> storedResultRowMapper() {
        return new RowMapper<BridgeStoredResult>() {
            @Override
            public BridgeStoredResult mapRow(ResultSet rs, int rowNum) throws SQLException {
                BridgeStoredResult result = new BridgeStoredResult();
                result.setRequestId(rs.getString("request_id"));
                result.setBusinessType(rs.getString("business_type"));
                result.setWaitStatus(BridgeWaitStatus.valueOf(rs.getString("wait_status")));
                result.setProcessStatus(BridgeProcessStatus.valueOf(rs.getString("process_status")));
                result.setResultPayload(readCharacterStream(rs, "result_payload"));
                result.setErrorCode(rs.getString("error_code"));
                result.setErrorMessage(rs.getString("error_message"));
                return result;
            }
        };
    }

    private static String readCharacterStream(ResultSet rs, String column) throws SQLException {
        try (Reader reader = rs.getCharacterStream(column)) {
            if (reader == null) {
                return null;
            }
            StringBuilder value = new StringBuilder();
            char[] buffer = new char[8192];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                value.append(buffer, 0, count);
            }
            return value.toString();
        } catch (IOException e) {
            throw new SQLException("Failed to read " + column + " character stream", e);
        }
    }
}
