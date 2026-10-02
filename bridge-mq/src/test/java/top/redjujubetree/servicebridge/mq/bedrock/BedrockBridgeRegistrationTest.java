package top.redjujubetree.servicebridge.mq.bedrock;

import top.redjujubetree.servicebridge.mq.BridgeInboundHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.redjujubetree.bedrock.mq.consumer.BedrockConsumerRegistry;
import top.redjujubetree.bedrock.mq.mapper.BedrockSubscriptionMapper;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

    @Configuration
    static class TestConfiguration {
        @Bean
        static BeanPostProcessor registrar(ApplicationContext context) {
            return BedrockBridgeAutoConfiguration.bedrockBridgeRegistrar(context);
        }

        @Bean
        BedrockSubscriptionMapper subscriptions() {
            return mock(BedrockSubscriptionMapper.class);
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
                public void handle(String payload) { }
            };
        }
    }
}
