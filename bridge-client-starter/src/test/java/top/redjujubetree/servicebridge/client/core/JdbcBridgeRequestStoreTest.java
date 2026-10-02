package top.redjujubetree.servicebridge.client.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.redjujubetree.servicebridge.client.model.BridgeRequestRecord;
import top.redjujubetree.servicebridge.client.model.BridgeStoredResult;
import top.redjujubetree.servicebridge.client.store.jdbc.JdbcBridgeRequestStore;
import top.redjujubetree.servicebridge.protocol.BridgeProcessStatus;
import top.redjujubetree.servicebridge.protocol.BridgeWaitStatus;
import top.redjujubetree.servicebridge.protocol.ServiceBridgeProtocol;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.io.StringReader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcBridgeRequestStoreTest {

    private JdbcTemplate jdbc;
    private JdbcBridgeRequestStore store;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:client-store;MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS service_bridge_request");
        jdbc.execute(TestSchema.CREATE_TABLE);
        store = new JdbcBridgeRequestStore(
                new NamedParameterJdbcTemplate(dataSource), new ObjectMapper());
    }

    @Test
    void createsFindsAndMarksTerminalResponse() throws Exception {
        BridgeRequestRecord request = new BridgeRequestRecord();
        request.setRequestId("request-1");
        request.setProtocolVersion(ServiceBridgeProtocol.VERSION);
        request.setBusinessType("TEST");
        request.setRequestPayload(new ObjectMapper().readTree("{\"a\":1}"));
        request.setWaitStatus(BridgeWaitStatus.WAITING);
        request.setProcessStatus(BridgeProcessStatus.PENDING);
        request.setCreatedAt(LocalDateTime.now());
        request.setClientDeadlineAt(LocalDateTime.now().plusMinutes(1));
        store.create(request);

        assertEquals("{\"a\":1}", jdbc.queryForObject(
                "SELECT request_payload FROM service_bridge_request WHERE request_id='request-1'", String.class));
        jdbc.update("UPDATE service_bridge_request SET process_status='SUCCESS', result_payload='{\"ok\":true}' WHERE request_id='request-1'");
        Optional<BridgeStoredResult> stored = store.find("request-1");

        assertTrue(stored.isPresent());
        assertEquals(BridgeProcessStatus.SUCCESS, stored.get().getProcessStatus());
        assertEquals("{\"ok\":true}", stored.get().getResultPayload());
        assertTrue(store.markRespondedIfWaiting("request-1", LocalDateTime.now()));
        assertEquals(BridgeWaitStatus.RESPONDED, store.find("request-1").get().getWaitStatus());
    }

    @Test
    void bindsRequestJsonAsCharacterStream() throws Exception {
        NamedParameterJdbcTemplate namedJdbc = mock(NamedParameterJdbcTemplate.class);
        JdbcTemplate jdbcOperations = mock(JdbcTemplate.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(namedJdbc.getJdbcTemplate()).thenReturn(jdbcOperations);
        doAnswer(invocation -> {
            invocation.getArgument(1, PreparedStatementSetter.class).setValues(statement);
            return 1;
        }).when(jdbcOperations).update(anyString(), any(PreparedStatementSetter.class));

        BridgeRequestRecord request = new BridgeRequestRecord();
        request.setRequestId("request-2");
        request.setProtocolVersion(ServiceBridgeProtocol.VERSION);
        request.setBusinessType("TEST");
        request.setRequestPayload(new ObjectMapper().readTree("{\"text\":\"中文\"}"));
        request.setWaitStatus(BridgeWaitStatus.WAITING);
        request.setProcessStatus(BridgeProcessStatus.PENDING);
        request.setCreatedAt(LocalDateTime.now());
        request.setClientDeadlineAt(LocalDateTime.now().plusMinutes(1));

        new JdbcBridgeRequestStore(namedJdbc, new ObjectMapper()).create(request);

        verify(statement).setCharacterStream(
                org.mockito.ArgumentMatchers.eq(4), any(StringReader.class),
                org.mockito.ArgumentMatchers.eq("{\"text\":\"中文\"}".length()));
    }

    @Test
    void readsResultJsonAsCharacterStream() throws Exception {
        NamedParameterJdbcTemplate namedJdbc = mock(NamedParameterJdbcTemplate.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("request_id")).thenReturn("request-3");
        when(resultSet.getString("business_type")).thenReturn("TEST");
        when(resultSet.getString("wait_status")).thenReturn("WAITING");
        when(resultSet.getString("process_status")).thenReturn("SUCCESS");
        when(resultSet.getCharacterStream("result_payload"))
                .thenReturn(new StringReader("{\"message\":\"中文\"}"));
        doAnswer(invocation -> Collections.singletonList(
                invocation.getArgument(2, RowMapper.class).mapRow(resultSet, 0)))
                .when(namedJdbc).query(anyString(), any(Map.class), any(RowMapper.class));

        BridgeStoredResult result = new JdbcBridgeRequestStore(namedJdbc, new ObjectMapper())
                .find("request-3").get();

        assertEquals("{\"message\":\"中文\"}", result.getResultPayload());
        verify(resultSet).getCharacterStream("result_payload");
    }

    static final class TestSchema {
        static final String CREATE_TABLE = "CREATE TABLE service_bridge_request ("
                + "request_id VARCHAR(64) PRIMARY KEY, protocol_version VARCHAR(16), business_type VARCHAR(128), "
                + "request_payload LONGTEXT, wait_status VARCHAR(20), process_status VARCHAR(20), "
                + "result_payload LONGTEXT, error_code VARCHAR(64), error_message VARCHAR(1000), "
                + "processing_token VARCHAR(64), processing_started_at TIMESTAMP, processing_expires_at TIMESTAMP, "
                + "client_deadline_at TIMESTAMP, responded_at TIMESTAMP, process_completed_at TIMESTAMP, "
                + "created_at TIMESTAMP, updated_at TIMESTAMP, version INT DEFAULT 0)";
    }
}
