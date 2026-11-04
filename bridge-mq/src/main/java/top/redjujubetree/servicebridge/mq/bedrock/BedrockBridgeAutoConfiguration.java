package top.redjujubetree.servicebridge.mq.bedrock;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.redjujubetree.servicebridge.mq.BridgeInboundHandler;
import top.redjujubetree.servicebridge.mq.BridgeTransport;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import top.redjujubetree.bedrock.mq.consumer.BedrockConsumerRegistry;
import top.redjujubetree.bedrock.mq.consumer.BedrockMessageConsumer;
import top.redjujubetree.bedrock.mq.mapper.BedrockSubscriptionMapper;
import top.redjujubetree.bedrock.mq.producer.MessageProducer;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Configuration
@AutoConfigureBefore(name = "top.redjujubetree.bedrock.mq.config.BedrockAutoConfiguration")
@ConditionalOnClass(name = "top.redjujubetree.bedrock.mq.producer.MessageProducer")
@ConditionalOnProperty(prefix = "service.bridge.mq", name = "provider", havingValue = "bedrock")
public class BedrockBridgeAutoConfiguration {
    @Bean
    public BridgeTransport bedrockBridgeTransport(MessageProducer producer, ObjectMapper mapper,
            BedrockSubscriptionMapper subscriptions,
            PlatformTransactionManager transactionManager,
            Environment environment) {
        String source = resolveMessageSource(environment);
        TransactionTemplate sendTransaction = new TransactionTemplate(transactionManager);
        sendTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return (topic, key, payload) -> {
            requireBedrockTopic(topic);
            java.util.List<top.redjujubetree.bedrock.mq.entity.BedrockSubscription> active =
                    subscriptions.findEnabledByTopic(topic);
            if (active == null || active.isEmpty()) {
                throw new IllegalStateException("No active BedrockMQ subscriber for " + topic);
            }
            try {
                Object json = mapper.readTree(payload);
                sendTransaction.execute(status -> producer.send(topic, source, json));
            } catch (java.io.IOException e) {
                throw new IllegalArgumentException("Invalid bridge message JSON", e);
            }
        };
    }

    static String resolveMessageSource(Environment environment) {
        String configured = environment.getProperty("service.bridge.mq.bedrock.message-source");
        if (StringUtils.hasText(configured)) {
            return configured.trim();
        }
        String applicationName = environment.getProperty("spring.application.name");
        if (StringUtils.hasText(applicationName)) {
            return applicationName.trim();
        }
        return "bridge-client-starter";
    }

    @Bean
    public static BeanPostProcessor bedrockBridgeRegistrar(ApplicationContext context) {
        return new BeanPostProcessor() {
            @Override
            @SuppressWarnings("unchecked")
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (!(bean instanceof BedrockConsumerRegistry)) {
                    return bean;
                }
                BedrockConsumerRegistry registry = (BedrockConsumerRegistry) bean;
                Field field = ReflectionUtils.findField(BedrockConsumerRegistry.class, "registry");
                if (field == null) {
                    throw new IllegalStateException("BedrockMQ consumer registry is incompatible");
                }
                ReflectionUtils.makeAccessible(field);
                Map<String, BedrockMessageConsumer> consumers =
                        (Map<String, BedrockMessageConsumer>) ReflectionUtils.getField(field, registry);
                BedrockSubscriptionMapper subscriptions = context.getBean(BedrockSubscriptionMapper.class);
                Set<String> existingSubscriptions = new HashSet<>();
                subscriptions.findAll().forEach(subscription -> existingSubscriptions.add(
                        BedrockConsumerRegistry.key(subscription.getTopic(), subscription.getConsumer())));
                for (BridgeInboundHandler handler : context.getBeansOfType(BridgeInboundHandler.class).values()) {
                    for (String topic : handler.topics()) {
                        requireBedrockTopic(topic);
                        String group = handler.consumerGroup(topic);
                        String registrationKey = BedrockConsumerRegistry.key(topic, group);
                        if (consumers == null || consumers.putIfAbsent(registrationKey,
                                message -> handler.handle(topic, message.getPayload())) != null) {
                            throw new IllegalStateException("Duplicate BedrockMQ consumer: " + registrationKey);
                        }
                        if (existingSubscriptions.add(registrationKey)) {
                            subscriptions.upsert(topic, group, 10);
                        }
                    }
                }
                return bean;
            }
        };
    }

    private static void requireBedrockTopic(String topic) {
        if (topic.indexOf(':') >= 0) {
            throw new IllegalArgumentException("BedrockMQ topic must not contain ':' : " + topic);
        }
    }
}
