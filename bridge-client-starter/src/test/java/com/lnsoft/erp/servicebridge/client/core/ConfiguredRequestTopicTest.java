package com.lnsoft.erp.servicebridge.client.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnsoft.erp.servicebridge.client.config.ServiceBridgeClientProperties;
import com.lnsoft.erp.servicebridge.client.store.BridgeRequestStore;
import com.lnsoft.erp.servicebridge.protocol.BridgeTopicProperties;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.redjujubetree.bedrock.mq.producer.MessageProducer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfiguredRequestTopicTest {

    @Test
    void sendsRequestToConfiguredTopicWithoutSubscriberCheck() throws Exception {
        BridgeTopicProperties topics = new BridgeTopicProperties();
        topics.setRequestTopic("custom.request");
        BridgeRequestIdGenerator ids = mock(BridgeRequestIdGenerator.class);
        when(ids.nextId()).thenReturn("request-1", "message-1");
        MessageProducer producer = mock(MessageProducer.class);
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:configured-request-topic;DB_CLOSE_DELAY=-1");
        DefaultServiceBridgeClient client = new DefaultServiceBridgeClient(
                new ServiceBridgeClientProperties(), ids, mock(BridgeRequestStore.class),
                new TaskWaitRegistry(10), mock(BridgeCompletionCoordinator.class),
                mock(BridgeResponseFactory.class), producer, topics,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));

        client.request("TEST", new ObjectMapper().readTree("{}"));

        verify(producer).send(eq("custom.request"), eq("service-bridge-client"), any());
    }
}
