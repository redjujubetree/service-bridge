package top.redjujubetree.servicebridge.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.redjujubetree.servicebridge.mq.bedrock.BedrockBridgeAutoConfiguration;
import top.redjujubetree.servicebridge.mq.kafka.KafkaBridgeAutoConfiguration;
import top.redjujubetree.servicebridge.mq.rocketmq.RocketBridgeAutoConfiguration;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import top.redjujubetree.bedrock.mq.mapper.BedrockSubscriptionMapper;
import top.redjujubetree.bedrock.mq.producer.MessageProducer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BridgeProviderIsolationTest {
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        BridgeMqAutoConfiguration.class,
                        BedrockBridgeAutoConfiguration.class,
                        KafkaBridgeAutoConfiguration.class,
                        RocketBridgeAutoConfiguration.class))
                .withBean(ObjectMapper.class, ObjectMapper::new);
    }

    @Test
    @SuppressWarnings("unchecked")
    void kafkaStartsWithoutBedrockOrRocketClasses() {
        runner().withClassLoader(new FilteredClassLoader(
                        "top.redjujubetree.bedrock", "org.apache.rocketmq"))
                .withPropertyValues("service.bridge.mq.provider=kafka")
                .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
                .withBean(ConsumerFactory.class, () -> mock(ConsumerFactory.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(BridgeTransport.class);
                    assertThat(context).hasBean("kafkaBridgeTransport");
                    assertThat(context).doesNotHaveBean("bedrockBridgeTransport");
                    assertThat(context).doesNotHaveBean("rocketBridgeTransport");
                });
    }

    @Test
    void bedrockStartsWithoutKafkaOrRocketClasses() {
        runner().withClassLoader(new FilteredClassLoader(
                        "org.springframework.kafka", "org.apache.rocketmq"))
                .withPropertyValues("service.bridge.mq.provider=bedrock",
                        "spring.application.name=bridge-provider-test")
                .withBean(MessageProducer.class, () -> mock(MessageProducer.class))
                .withBean(BedrockSubscriptionMapper.class, () -> mock(BedrockSubscriptionMapper.class))
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(BridgeTransport.class);
                    assertThat(context).hasBean("bedrockBridgeTransport");
                    assertThat(context).doesNotHaveBean("kafkaBridgeTransport");
                    assertThat(context).doesNotHaveBean("rocketBridgeTransport");
                });
    }

    @Test
    void rocketStartsWithoutBedrockOrKafkaClasses() {
        runner().withClassLoader(new FilteredClassLoader(
                        "top.redjujubetree.bedrock", "org.springframework.kafka"))
                .withPropertyValues("service.bridge.mq.provider=rocketmq",
                        "service.bridge.mq.rocketmq.namesrv-addr=localhost:9876")
                .withBean(DefaultMQProducer.class, () -> mock(DefaultMQProducer.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(BridgeTransport.class);
                    assertThat(context).hasBean("rocketBridgeTransport");
                    assertThat(context).doesNotHaveBean("bedrockBridgeTransport");
                    assertThat(context).doesNotHaveBean("kafkaBridgeTransport");
                });
    }

    @Test
    void missingSelectedDependencyFailsClearly() {
        runner().withClassLoader(new FilteredClassLoader("org.springframework.kafka"))
                .withPropertyValues("service.bridge.mq.provider=kafka")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining("requires MQ client dependency"));
    }
}
