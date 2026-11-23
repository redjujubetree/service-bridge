package top.redjujubetree.servicebridge.mq.bedrock;

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
import org.springframework.util.StringUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import top.redjujubetree.bedrock.mq.consumer.BedrockConsumerRegistry;
import top.redjujubetree.bedrock.mq.mapper.BedrockSubscriptionMapper;
import top.redjujubetree.bedrock.mq.producer.MessageProducer;

@Configuration
@AutoConfigureBefore(name = "top.redjujubetree.bedrock.mq.config.BedrockAutoConfiguration")
@ConditionalOnClass(name = "top.redjujubetree.bedrock.mq.producer.MessageProducer")
@ConditionalOnProperty(prefix = "service.bridge.mq", name = "provider", havingValue = "bedrock")
public class BedrockBridgeAutoConfiguration {
    @Bean
    public BridgeTransport bedrockBridgeTransport(MessageProducer producer,
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
            sendTransaction.execute(status -> producer.send(topic, source, payload));
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

    /**
     * 将 ServiceBridge 的入站处理器接入 BedrockMQ 的消费者注册表。
     * 每个处理器可以订阅多个 Topic，消费组由处理器按 Topic 确定。
     * 注册处理器时由 BedrockMQ 创建订阅并启动对应轮询。
     */
    @Bean
    public static BeanPostProcessor bedrockBridgeRegistrar(ApplicationContext context) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (!(bean instanceof BedrockConsumerRegistry)) {
                    return bean;
                }
                BedrockConsumerRegistry registry = (BedrockConsumerRegistry) bean;
                for (BridgeInboundHandler handler : context.getBeansOfType(BridgeInboundHandler.class).values()) {
                    for (String topic : handler.topics()) {
                        requireBedrockTopic(topic);
                        String group = handler.consumerGroup(topic);
                        registry.registerConsumer(topic, group, 10,
                                message -> handler.handle(topic, message.getPayload()));
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
