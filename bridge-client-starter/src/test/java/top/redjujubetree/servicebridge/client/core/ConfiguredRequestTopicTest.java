package top.redjujubetree.servicebridge.client.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.redjujubetree.servicebridge.client.config.ServiceBridgeClientProperties;
import top.redjujubetree.servicebridge.client.store.BridgeRequestStore;
import top.redjujubetree.servicebridge.mq.BridgeMessagePublisher;
import top.redjujubetree.servicebridge.protocol.config.BridgeTopicProperties;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
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
        when(ids.nextId()).thenReturn("request-1", "message-1");
        BridgeMessagePublisher publisher = mock(BridgeMessagePublisher.class);
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:configured-request-topic;DB_CLOSE_DELAY=-1");
        DefaultServiceBridgeClient client = new DefaultServiceBridgeClient(
                new ServiceBridgeClientProperties(), ids, mock(BridgeRequestStore.class),
                new TaskWaitRegistry(10), mock(BridgeCompletionCoordinator.class),
                mock(BridgeResponseFactory.class), publisher, topics,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));

        client.request("TEST", new ObjectMapper().readTree("{}"));

        verify(publisher).send(eq("custom.request"), eq("request-1"), any());
    }

    @Test
    void routesEachBusinessTypeAndRejectsUnknownType() throws Exception {
        BridgeTopicProperties topics = new BridgeTopicProperties();
        topics.getRequestTopics().put("ORDER", "orders.request");
        topics.getRequestTopics().put("PAYMENT", "payments.request");
        BridgeRequestIdGenerator ids = mock(BridgeRequestIdGenerator.class);
        when(ids.nextId()).thenReturn("request-1", "message-1", "request-2", "message-2");
        BridgeMessagePublisher publisher = mock(BridgeMessagePublisher.class);
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:multiple-request-topics;DB_CLOSE_DELAY=-1");
        BridgeRequestStore store = mock(BridgeRequestStore.class);
        DefaultServiceBridgeClient client = new DefaultServiceBridgeClient(
                new ServiceBridgeClientProperties(), ids, store,
                new TaskWaitRegistry(10), mock(BridgeCompletionCoordinator.class),
                mock(BridgeResponseFactory.class), publisher, topics,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));

        client.request("ORDER", new ObjectMapper().readTree("{}"));
        client.request("PAYMENT", new ObjectMapper().readTree("{}"));

        verify(publisher).send(eq("orders.request"), eq("request-1"), any());
        verify(publisher).send(eq("payments.request"), eq("request-2"), any());
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> client.request("UNKNOWN", new ObjectMapper().readTree("{}")));
    }
}
