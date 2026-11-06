package top.redjujubetree.servicebridge.client.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.redjujubetree.servicebridge.client.config.ServiceBridgeClientProperties;
import top.redjujubetree.servicebridge.client.store.jdbc.JdbcBridgeRequestStore;
import top.redjujubetree.servicebridge.mq.BridgeMessagePublisher;
import top.redjujubetree.servicebridge.protocol.status.BridgeProcessStatus;
import top.redjujubetree.servicebridge.protocol.config.BridgeTopicProperties;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestMqFailureTest {
    @Test
    void failedRequestSendMarksPersistedRequestFailed() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:request-mq-failure;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS service_bridge_request");
        jdbc.execute(JdbcBridgeRequestStoreTest.TestSchema.CREATE_TABLE);
        ObjectMapper mapper = new ObjectMapper();
        JdbcBridgeRequestStore store = new JdbcBridgeRequestStore(
                new NamedParameterJdbcTemplate(dataSource));
        BridgeMessagePublisher publisher = mock(BridgeMessagePublisher.class);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(publisher).send(any(), any(), any());
        BridgeRequestIdGenerator ids = mock(BridgeRequestIdGenerator.class);
        when(ids.nextId()).thenReturn("request-1", "message-1");
        DefaultServiceBridgeClient client = new DefaultServiceBridgeClient(
                new ServiceBridgeClientProperties(), ids, store,
                new TaskWaitRegistry(10), mock(BridgeCompletionCoordinator.class),
                new DefaultBridgeResponseHandler(mapper), publisher, new BridgeTopicProperties(),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));

        ResponseEntity<?> response = (ResponseEntity<?>) client.request("TEST", mapper.readTree("{}"))
                .getResult();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(BridgeProcessStatus.FAILED, store.find("request-1").get().getProcessStatus());
        assertEquals("MQ_SEND_FAILED", store.find("request-1").get().getErrorCode());
    }
}
