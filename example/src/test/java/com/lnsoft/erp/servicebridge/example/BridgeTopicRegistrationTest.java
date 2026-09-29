package com.lnsoft.erp.servicebridge.example;

import com.lnsoft.erp.servicebridge.client.autoconfigure.BridgeClientConsumerRegistrar;
import com.lnsoft.erp.servicebridge.client.redis.BridgeResponseBedrockConsumer;
import com.lnsoft.erp.servicebridge.protocol.BridgeTopicProperties;
import com.lnsoft.erp.servicebridge.protocol.ServiceBridgeProtocol;
import com.lnsoft.erp.servicebridge.server.autoconfigure.BridgeServerConsumerRegistrar;
import com.lnsoft.erp.servicebridge.server.config.ServiceBridgeServerProperties;
import com.lnsoft.erp.servicebridge.server.consumer.BridgeRecoveryBedrockConsumer;
import com.lnsoft.erp.servicebridge.server.consumer.BridgeRequestBedrockConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.redjujubetree.bedrock.mq.consumer.BedrockConsumerRegistry;
import top.redjujubetree.bedrock.mq.mapper.BedrockSubscriptionMapper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BridgeTopicRegistrationTest {

    @Test
    void registersRequestAndResponseConsumersOnConfiguredTopics() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            BedrockConsumerRegistry registry = context.getBean(BedrockConsumerRegistry.class);
            Set<String> keys = registry.getRegisteredKeys();

            assertEquals(3, keys.size());
            assertTrue(keys.contains(BedrockConsumerRegistry.key(
                    "custom.request", ServiceBridgeProtocol.REQUEST_CONSUMER)));
            assertTrue(keys.contains(BedrockConsumerRegistry.key(
                    "custom.response", ServiceBridgeProtocol.RESPONSE_CONSUMER)));
            assertTrue(keys.contains(BedrockConsumerRegistry.key(
                    ServiceBridgeProtocol.RECOVERY_TOPIC, ServiceBridgeProtocol.RECOVERY_CONSUMER)));
            assertFalse(keys.contains(BedrockConsumerRegistry.key(
                    ServiceBridgeProtocol.DEFAULT_REQUEST_TOPIC, ServiceBridgeProtocol.REQUEST_CONSUMER)));

            BedrockSubscriptionMapper mapper = context.getBean(BedrockSubscriptionMapper.class);
            verify(mapper).upsert("custom.request", ServiceBridgeProtocol.REQUEST_CONSUMER, 3);
            verify(mapper).upsert("custom.response", ServiceBridgeProtocol.RESPONSE_CONSUMER, 10);
        }
    }

    @Configuration
    static class TestConfiguration {

        @Bean
        static BridgeClientConsumerRegistrar clientRegistrar(ApplicationContext context) {
            return new BridgeClientConsumerRegistrar(context);
        }

        @Bean
        static BridgeServerConsumerRegistrar serverRegistrar(ApplicationContext context) {
            return new BridgeServerConsumerRegistrar(context);
        }

        @Bean
        BridgeTopicProperties topics() {
            BridgeTopicProperties topics = new BridgeTopicProperties();
            topics.setRequestTopic("custom.request");
            topics.setResponseTopic("custom.response");
            return topics;
        }

        @Bean
        ServiceBridgeServerProperties serverProperties() {
            return new ServiceBridgeServerProperties();
        }

        @Bean
        BedrockSubscriptionMapper subscriptionMapper() {
            return mock(BedrockSubscriptionMapper.class);
        }

        @Bean
        BedrockConsumerRegistry registry(ApplicationContext context,
                                         BedrockSubscriptionMapper mapper) {
            return new BedrockConsumerRegistry(context, mapper);
        }

        @Bean
        BridgeRequestBedrockConsumer requestConsumer() {
            return mock(BridgeRequestBedrockConsumer.class);
        }

        @Bean
        BridgeResponseBedrockConsumer responseConsumer() {
            return mock(BridgeResponseBedrockConsumer.class);
        }

        @Bean
        BridgeRecoveryBedrockConsumer recoveryConsumer() {
            return mock(BridgeRecoveryBedrockConsumer.class);
        }
    }
}
