package top.redjujubetree.servicebridge.mq.bedrock;

import top.redjujubetree.servicebridge.mq.BridgeInboundHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.redjujubetree.bedrock.mq.consumer.BedrockConsumerRegistry;
import top.redjujubetree.bedrock.mq.entity.BedrockMessage;
import top.redjujubetree.bedrock.mq.entity.BedrockSubscription;
import top.redjujubetree.bedrock.mq.mapper.BedrockSubscriptionMapper;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BedrockBridgeRegistrationTest {
    @Test
    void registersGenericHandlerOnConfiguredTopic() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            assertTrue(context.getBean(BedrockConsumerRegistry.class).getRegisteredKeys()
                    .contains(BedrockConsumerRegistry.key("custom.request", "bridge-server-custom")));
            assertTrue(context.getBean(BedrockConsumerRegistry.class).getRegisteredKeys()
                    .contains(BedrockConsumerRegistry.key("another.request", "bridge-server-another")));
            verify(context.getBean(BedrockSubscriptionMapper.class))
                    .upsert("custom.request", "bridge-server-custom", 10);
            verify(context.getBean(BedrockSubscriptionMapper.class))
                    .upsert("another.request", "bridge-server-another", 10);
        }
    }

    @Test
    void registeredConsumerDelegatesPayloadToHandler() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            BedrockMessage message = new BedrockMessage();
            message.setPayload("request-123");
            context.getBean(BedrockConsumerRegistry.class)
                    .getConsumer("custom.request", "bridge-server-custom").consume(message);
            assertEquals("request-123", TestConfiguration.receivedPayload.get());
        } finally {
            TestConfiguration.receivedPayload.set(null);
        }
    }

    @Test
    void registersConsumerWithExistingSubscription() {
        BedrockSubscription existing = new BedrockSubscription();
        existing.setTopic("custom.request");
        existing.setConsumer("bridge-server-custom");
        TestConfiguration.existing = existing;
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            assertTrue(context.getBean(BedrockConsumerRegistry.class).getRegisteredKeys()
                    .contains(BedrockConsumerRegistry.key("custom.request", "bridge-server-custom")));
            verify(context.getBean(BedrockSubscriptionMapper.class))
                    .upsert("custom.request", "bridge-server-custom", 10);
            verify(context.getBean(BedrockSubscriptionMapper.class))
                    .upsert("another.request", "bridge-server-another", 10);
        } finally {
            TestConfiguration.existing = null;
        }
    }

    @Configuration
    static class TestConfiguration {
        static BedrockSubscription existing;
        static final AtomicReference<String> receivedPayload = new AtomicReference<>();

        @Bean
        static BeanPostProcessor registrar(ApplicationContext context) {
            return BedrockBridgeAutoConfiguration.bedrockBridgeRegistrar(context);
        }

        @Bean
        BedrockSubscriptionMapper subscriptions() {
            BedrockSubscriptionMapper mapper = mock(BedrockSubscriptionMapper.class);
            if (existing != null) {
                when(mapper.findAll()).thenReturn(java.util.Collections.singletonList(existing));
            }
            return mapper;
        }

        @Bean
        BedrockConsumerRegistry registry(ApplicationContext context, BedrockSubscriptionMapper mapper) {
            return new BedrockConsumerRegistry(context, mapper);
        }

        @Bean
        BridgeInboundHandler handler() {
            return new BridgeInboundHandler() {
                public String topic() { return "custom.request"; }
                public String consumerGroup() { return "bridge-server"; }
                public Collection<String> topics() {
                    return Arrays.asList("custom.request", "another.request");
                }
                public String consumerGroup(String topic) {
                    return topic.equals("custom.request") ? "bridge-server-custom" : "bridge-server-another";
                }
                public void handle(String payload) { receivedPayload.set(payload); }
            };
        }
    }
}
