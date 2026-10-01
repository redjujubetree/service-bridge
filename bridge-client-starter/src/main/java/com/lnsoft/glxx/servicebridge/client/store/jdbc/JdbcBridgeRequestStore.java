package com.lnsoft.glxx.servicebridge.client.store.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnsoft.glxx.servicebridge.client.model.BridgeRequestRecord;
import com.lnsoft.glxx.servicebridge.client.model.BridgeStoredResult;
import com.lnsoft.glxx.servicebridge.client.store.BridgeRequestStore;
import com.lnsoft.glxx.servicebridge.protocol.BridgeProcessStatus;
import com.lnsoft.glxx.servicebridge.protocol.BridgeWaitStatus;
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
    private final ObjectMapper objectMapper;

    public JdbcBridgeRequestStore(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void create(BridgeRequestRecord request) {
        String sql = "INSERT INTO service_bridge_request ("
                + "request_id, protocol_version, business_type, request_payload, "
                + "wait_status, process_status, client_deadline_at, created_at, updated_at, version"
                + ") VALUES ("
                + "?, ?, ?, ?, ?, ?, ?, ?, ?, 0)";

        String requestJson = toJson(request);
        try (StringReader reader = new StringReader(requestJson)) {
            jdbc.getJdbcTemplate().update(sql, ps -> {
                ps.setString(1, request.getRequestId());
                ps.setString(2, request.getProtocolVersion());
                ps.setString(3, request.getBusinessType());
                ps.setCharacterStream(4, reader, requestJson.length());
                ps.setString(5, request.getWaitStatus().name());
                ps.setString(6, request.getProcessStatus().name());
                ps.setTimestamp(7, Timestamp.valueOf(request.getClientDeadlineAt()));
                Timestamp createdAt = Timestamp.valueOf(request.getCreatedAt());
                ps.setTimestamp(8, createdAt);
                ps.setTimestamp(9, createdAt);
            });
        }
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

    private String toJson(BridgeRequestRecord request) {
        try {
            return objectMapper.writeValueAsString(request.getRequestPayload());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize request payload", e);
        }
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
