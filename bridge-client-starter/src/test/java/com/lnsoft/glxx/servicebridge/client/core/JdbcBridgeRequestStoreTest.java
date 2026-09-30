package com.lnsoft.glxx.servicebridge.client.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnsoft.glxx.servicebridge.client.model.BridgeRequestRecord;
import com.lnsoft.glxx.servicebridge.client.model.BridgeStoredResult;
import com.lnsoft.glxx.servicebridge.client.store.jdbc.JdbcBridgeRequestStore;
import com.lnsoft.glxx.servicebridge.protocol.BridgeProcessStatus;
import com.lnsoft.glxx.servicebridge.protocol.BridgeWaitStatus;
import com.lnsoft.glxx.servicebridge.protocol.ServiceBridgeProtocol;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        jdbc.update("UPDATE service_bridge_request SET process_status='SUCCESS', result_payload='{\"ok\":true}' WHERE request_id='request-1'");
        Optional<BridgeStoredResult> stored = store.find("request-1");

        assertTrue(stored.isPresent());
        assertEquals(BridgeProcessStatus.SUCCESS, stored.get().getProcessStatus());
        assertTrue(store.markRespondedIfWaiting("request-1", LocalDateTime.now()));
        assertEquals(BridgeWaitStatus.RESPONDED, store.find("request-1").get().getWaitStatus());
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
