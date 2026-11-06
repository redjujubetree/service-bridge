package top.redjujubetree.servicebridge.server.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.redjujubetree.servicebridge.server.model.AcquireResult;
import top.redjujubetree.servicebridge.protocol.message.BridgeRequestMessage;
import top.redjujubetree.servicebridge.protocol.ServiceBridgeProtocol;
import top.redjujubetree.servicebridge.server.store.jdbc.JdbcBridgeResultStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcBridgeResultStoreTest {

    private JdbcTemplate jdbc;
    private JdbcBridgeResultStore store;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:server-store;MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS service_bridge_request");
        jdbc.execute("CREATE TABLE service_bridge_request ("
                + "request_id VARCHAR(64) PRIMARY KEY, protocol_version VARCHAR(16), "
                + "business_type VARCHAR(128), request_payload LONGTEXT, trace_id VARCHAR(127), "
                + "created_at TIMESTAMP, "
                + "wait_status VARCHAR(20), client_deadline_at TIMESTAMP, "
                + "process_status VARCHAR(20), result_payload LONGTEXT, error_code VARCHAR(64), "
                + "error_message VARCHAR(1000), processing_token VARCHAR(64), "
                + "processing_started_at TIMESTAMP, processing_expires_at TIMESTAMP, "
                + "process_completed_at TIMESTAMP, updated_at TIMESTAMP, version INT DEFAULT 0)");
        jdbc.update("INSERT INTO service_bridge_request(request_id,business_type,process_status,updated_at) "
                + "VALUES('request-1','TEST','PENDING',CURRENT_TIMESTAMP)");
        store = new JdbcBridgeResultStore(new NamedParameterJdbcTemplate(dataSource), new ObjectMapper());
    }

    @Test
    void loadsRequestDataById() {
        LocalDateTime createdAt = LocalDateTime.now().withNano(0);
        jdbc.update("UPDATE service_bridge_request SET protocol_version = ?, request_payload = ?, "
                        + "trace_id = ?, created_at = ?, client_deadline_at = ? WHERE request_id = ?",
                ServiceBridgeProtocol.VERSION, "{\"text\":\"中文\"}", "trace-1",
                java.sql.Timestamp.valueOf(createdAt),
                java.sql.Timestamp.valueOf(createdAt.plusMinutes(1)), "request-1");

        BridgeRequestMessage request = store.findRequest("request-1").get();
        assertEquals("TEST", request.getBusinessType());
        assertEquals("中文", request.getPayload().get("text").asText());
        assertEquals("trace-1", request.getTraceId());
        assertEquals(java.sql.Timestamp.valueOf(createdAt).getTime(), request.getCreatedAt());
        assertTrue(!store.findRequest("missing").isPresent());
    }

    @Test
    void staleAttemptCannotOverwriteNewAttempt() {
        LocalDateTime now = LocalDateTime.now();
        assertEquals(AcquireResult.ACQUIRED,
                store.tryAcquire("request-1", "token-a", now, now.plusMinutes(1)));
        assertEquals(AcquireResult.IN_PROGRESS,
                store.tryAcquire("request-1", "token-b", now, now.plusMinutes(1)));
        assertEquals(AcquireResult.ACQUIRED,
                store.tryAcquire("request-1", "token-b", now.plusMinutes(2), now.plusMinutes(3)));

        assertFalse(store.completeSuccess("request-1", "token-a", "{}", now));
        assertTrue(store.completeSuccess("request-1", "token-b", "{}", now));
        assertEquals(AcquireResult.ALREADY_SUCCESS,
                store.tryAcquire("request-1", "token-c", now, now.plusMinutes(1)));
    }

    @Test
    void retryableAttemptReleasesOnlyItsOwnToken() {
        LocalDateTime now = LocalDateTime.now();
        assertEquals(AcquireResult.ACQUIRED,
                store.tryAcquire("request-1", "token-a", now, now.plusMinutes(1)));
        assertFalse(store.releaseForRetry("request-1", "wrong-token", now));
        assertEquals(AcquireResult.IN_PROGRESS,
                store.tryAcquire("request-1", "token-b", now, now.plusMinutes(1)));
        assertTrue(store.releaseForRetry("request-1", "token-a", now));
        assertEquals(AcquireResult.ACQUIRED,
                store.tryAcquire("request-1", "token-b", now, now.plusMinutes(1)));
    }

    @Test
    void notificationRequiresWaitingStatusAndUnexpiredDeadline() {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("UPDATE service_bridge_request SET wait_status = 'WAITING', "
                + "client_deadline_at = ? WHERE request_id = 'request-1'",
                java.sql.Timestamp.valueOf(now.plusMinutes(1)));
        assertTrue(store.isClientWaiting("request-1", now));

        jdbc.update("UPDATE service_bridge_request SET wait_status = 'TIMED_OUT' "
                + "WHERE request_id = 'request-1'");
        assertFalse(store.isClientWaiting("request-1", now));

        jdbc.update("UPDATE service_bridge_request SET wait_status = 'WAITING', "
                + "client_deadline_at = ? WHERE request_id = 'request-1'",
                java.sql.Timestamp.valueOf(now));
        assertFalse(store.isClientWaiting("request-1", now));
    }

    @Test
    void delayedRecoveryRequiresMatchingTokenAndExpiredDeadline() {
        LocalDateTime now = LocalDateTime.now();
        assertEquals(AcquireResult.ACQUIRED,
                store.tryAcquire("request-1", "token-a", now, now.plusMinutes(1)));
        assertFalse(store.completeExpiredFailure("request-1", "token-a",
                "PROCESSING_TIMEOUT", "Expired", now));

        assertEquals(AcquireResult.IN_PROGRESS,
                store.tryAcquire("request-1", "token-b", now, now.plusMinutes(2)));
        assertEquals(1, store.findExpired(now.plusMinutes(2), 100).size());
        assertEquals(AcquireResult.ACQUIRED,
                store.tryAcquire("request-1", "token-b", now.plusMinutes(1), now.plusMinutes(2)));
        assertFalse(store.completeExpiredFailure("request-1", "token-a",
                "PROCESSING_TIMEOUT", "Expired", now.plusMinutes(3)));
        assertFalse(store.completeExpiredFailure("request-1", "token-b",
                "PROCESSING_TIMEOUT", "Expired", now.plusMinutes(1)));
        assertTrue(store.completeExpiredFailure("request-1", "token-b",
                "PROCESSING_TIMEOUT", "Expired", now.plusMinutes(3)));
        assertEquals(AcquireResult.ALREADY_FAILED,
                store.tryAcquire("request-1", "token-c", now, now.plusMinutes(4)));
    }
}
