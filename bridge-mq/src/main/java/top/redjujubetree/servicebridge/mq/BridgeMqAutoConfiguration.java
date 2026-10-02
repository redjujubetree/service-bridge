package top.redjujubetree.servicebridge.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.ClassUtils;

@Configuration
@EnableScheduling
public class BridgeMqAutoConfiguration {
    @Bean
    public static BeanFactoryPostProcessor bridgeProviderDependencyValidator(Environment environment) {
        return beanFactory -> {
            String provider = environment.getProperty("service.bridge.mq.provider");
            if (provider == null || provider.trim().isEmpty()) {
                throw new IllegalStateException("Set service.bridge.mq.provider to bedrock, kafka or rocketmq");
            }
            String requiredClass;
            switch (provider.trim()) {
                case "bedrock":
                    requiredClass = "top.redjujubetree.bedrock.mq.producer.MessageProducer";
                    break;
                case "kafka":
                    requiredClass = "org.springframework.kafka.core.KafkaTemplate";
                    break;
                case "rocketmq":
                    requiredClass = "org.apache.rocketmq.client.producer.DefaultMQProducer";
                    break;
                default:
                    throw new IllegalStateException("Unsupported service.bridge.mq.provider: " + provider);
            }
            if (!ClassUtils.isPresent(requiredClass, beanFactory.getBeanClassLoader())) {
                throw new IllegalStateException("service.bridge.mq.provider=" + provider
                        + " requires MQ client dependency containing " + requiredClass);
            }
        };
    }

    @Bean
    public BridgeMessagePublisher bridgeMessagePublisher(BridgeTransport transport, ObjectMapper mapper) {
        return new JsonBridgeMessagePublisher(transport, mapper);
    }
}
