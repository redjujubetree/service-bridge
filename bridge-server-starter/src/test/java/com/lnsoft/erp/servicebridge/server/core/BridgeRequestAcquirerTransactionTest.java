package com.lnsoft.erp.servicebridge.server.core;

import com.lnsoft.erp.servicebridge.protocol.ServiceBridgeProtocol;
import com.lnsoft.erp.servicebridge.server.config.ServiceBridgeServerProperties;
import com.lnsoft.erp.servicebridge.server.store.BridgeResultStore;
import com.lnsoft.erp.servicebridge.server.store.jdbc.JdbcBridgeResultStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import top.redjujubetree.bedrock.mq.entity.BedrockSubscription;
import top.redjujubetree.bedrock.mq.mapper.BedrockSubscriptionMapper;
import top.redjujubetree.bedrock.mq.producer.MessageProducer;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BridgeRequestAcquirerTransactionTest {

    @Test
    void failedDelayedSendRollsBackProcessingAcquisition() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            jdbc.execute("CREATE TABLE service_bridge_request (request_id VARCHAR(64) PRIMARY KEY, "
                    + "process_status VARCHAR(20), processing_token VARCHAR(64), "
                    + "processing_started_at TIMESTAMP, processing_expires_at TIMESTAMP, "
                    + "updated_at TIMESTAMP, version INT DEFAULT 0)");
            jdbc.update("INSERT INTO service_bridge_request (request_id, process_status) "
                    + "VALUES ('request-1', 'PENDING')");
            when(context.getBean(MessageProducer.class).sendDelayed(
                    eq(ServiceBridgeProtocol.RECOVERY_TOPIC), any(), any(), eq(Duration.ofMinutes(15))))
                    .thenThrow(new IllegalStateException("Delayed send failed"));

            assertThrows(IllegalStateException.class, () -> context.getBean(BridgeRequestAcquirer.class)
                    .acquire("request-1", "token-a", LocalDateTime.now()));

            assertEquals("PENDING", jdbc.queryForObject(
                    "SELECT process_status FROM service_bridge_request WHERE request_id = 'request-1'",
                    String.class));
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:acquirer-transaction;MODE=MySQL;DB_CLOSE_DELAY=-1");
            return dataSource;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        BridgeResultStore resultStore(DataSource dataSource) {
            return new JdbcBridgeResultStore(new NamedParameterJdbcTemplate(dataSource));
        }

        @Bean
        MessageProducer messageProducer() {
            return mock(MessageProducer.class);
        }

        @Bean
        BedrockSubscriptionMapper subscriptionMapper() {
            BedrockSubscriptionMapper mapper = mock(BedrockSubscriptionMapper.class);
            BedrockSubscription subscription = new BedrockSubscription();
            subscription.setConsumer(ServiceBridgeProtocol.RECOVERY_CONSUMER);
            when(mapper.findEnabledByTopic(ServiceBridgeProtocol.RECOVERY_TOPIC))
                    .thenReturn(Collections.singletonList(subscription));
            return mapper;
        }

        @Bean
        ServiceBridgeServerProperties properties() {
            return new ServiceBridgeServerProperties();
        }

        @Bean
        BridgeRequestAcquirer acquirer(BridgeResultStore resultStore,
                                       MessageProducer producer,
                                       BedrockSubscriptionMapper subscriptionMapper,
                                       ServiceBridgeServerProperties properties) {
            return new BridgeRequestAcquirer(resultStore, producer,
                    subscriptionMapper, properties);
        }
    }
}
