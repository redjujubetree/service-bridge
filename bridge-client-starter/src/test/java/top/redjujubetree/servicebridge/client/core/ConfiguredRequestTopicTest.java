package top.redjujubetree.servicebridge.client.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.redjujubetree.servicebridge.client.config.ServiceBridgeClientProperties;
import top.redjujubetree.servicebridge.client.model.BridgeRequestRecord;
import top.redjujubetree.servicebridge.client.store.BridgeRequestStore;
import top.redjujubetree.servicebridge.mq.BridgeMessagePublisher;
import top.redjujubetree.servicebridge.protocol.config.BridgeTopicProperties;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfiguredRequestTopicTest {

    @Test
    void sendsRequestToConfiguredTopic() throws Exception {
        BridgeTopicProperties topics = new BridgeTopicProperties();
        topics.setRequestTopic("custom.request");
        BridgeRequestIdGenerator ids = mock(BridgeRequestIdGenerator.class);
        when(ids.nextId()).thenReturn("request-1");
        BridgeMessagePublisher publisher = mock(BridgeMessagePublisher.class);
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:configured-request-topic;DB_CLOSE_DELAY=-1");
        BridgeRequestStore store = mock(BridgeRequestStore.class);
        DefaultServiceBridgeClient client = new DefaultServiceBridgeClient(
                new ServiceBridgeClientProperties(), ids, store,
                new TaskWaitRegistry(10), mock(BridgeCompletionCoordinator.class),
                mock(DefaultBridgeResponseHandler.class), publisher, topics,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));

        String traceId = new String(new char[127]).replace('\0', 'x');
        client.request("TEST", new ObjectMapper().readTree("{\"text\":\"中文\"}"), traceId);

        ArgumentCaptor<BridgeRequestRecord> record = ArgumentCaptor.forClass(BridgeRequestRecord.class);
        verify(store).create(record.capture());
        verify(publisher).send("custom.request", "request-1", "request-1");
        Assertions.assertEquals("{\"text\":\"中文\"}", record.getValue().getRequestPayload());
        Assertions.assertEquals(traceId, record.getValue().getTraceId());
    }

    @Test
    void routesEachBusinessTypeAndRejectsUnknownType() throws Exception {
        BridgeTopicProperties topics = new BridgeTopicProperties();
        topics.getRequestTopics().put("ORDER", "orders.request");
        topics.getRequestTopics().put("PAYMENT", "payments.request");
        BridgeRequestIdGenerator ids = mock(BridgeRequestIdGenerator.class);
        when(ids.nextId()).thenReturn("request-1", "request-2");
        BridgeMessagePublisher publisher = mock(BridgeMessagePublisher.class);
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:multiple-request-topics;DB_CLOSE_DELAY=-1");
        BridgeRequestStore store = mock(BridgeRequestStore.class);
        DefaultServiceBridgeClient client = new DefaultServiceBridgeClient(
                new ServiceBridgeClientProperties(), ids, store,
                new TaskWaitRegistry(10), mock(BridgeCompletionCoordinator.class),
                mock(DefaultBridgeResponseHandler.class), publisher, topics,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));

        client.request("ORDER", new ObjectMapper().readTree("{}"));
        client.request("PAYMENT", new ObjectMapper().readTree("{}"));

        verify(publisher).send("orders.request", "request-1", "request-1");
        verify(publisher).send("payments.request", "request-2", "request-2");
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> client.request("UNKNOWN", new ObjectMapper().readTree("{}")));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> client.request("ORDER", new ObjectMapper().readTree("{}"),
                        new String(new char[128]).replace('\0', 'x')));
    }
}
