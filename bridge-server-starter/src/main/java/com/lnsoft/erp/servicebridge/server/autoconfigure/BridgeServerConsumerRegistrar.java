package com.lnsoft.erp.servicebridge.server.autoconfigure;

import com.lnsoft.erp.servicebridge.protocol.BridgeTopicProperties;
import com.lnsoft.erp.servicebridge.protocol.ServiceBridgeProtocol;
import com.lnsoft.erp.servicebridge.server.config.ServiceBridgeServerProperties;
import com.lnsoft.erp.servicebridge.server.consumer.BridgeRecoveryBedrockConsumer;
import com.lnsoft.erp.servicebridge.server.consumer.BridgeRequestBedrockConsumer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ReflectionUtils;
import top.redjujubetree.bedrock.mq.consumer.BedrockConsumerRegistry;
import top.redjujubetree.bedrock.mq.consumer.BedrockMessageConsumer;
import top.redjujubetree.bedrock.mq.mapper.BedrockSubscriptionMapper;

import java.lang.reflect.Field;
import java.util.Map;

public class BridgeServerConsumerRegistrar implements BeanPostProcessor {

    private final ApplicationContext applicationContext;

    public BridgeServerConsumerRegistrar(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof BedrockConsumerRegistry) {
            BedrockConsumerRegistry registry = (BedrockConsumerRegistry) bean;
            BridgeTopicProperties topics = applicationContext.getBean(BridgeTopicProperties.class);
            register(registry, topics.getRequestTopic(), ServiceBridgeProtocol.REQUEST_CONSUMER,
                    applicationContext.getBean(BridgeRequestBedrockConsumer.class), 3);
            if (applicationContext.getBean(ServiceBridgeServerProperties.class).isRecoveryEnabled()) {
                register(registry, ServiceBridgeProtocol.RECOVERY_TOPIC,
                        ServiceBridgeProtocol.RECOVERY_CONSUMER,
                        applicationContext.getBean(BridgeRecoveryBedrockConsumer.class), 10);
            }
        }
        return bean;
    }

    @SuppressWarnings("unchecked")
    private void register(BedrockConsumerRegistry registry, String topic, String consumerName,
                          BedrockMessageConsumer consumer, int maxRetry) {
        Field field = ReflectionUtils.findField(BedrockConsumerRegistry.class, "registry");
        if (field == null) {
            throw new IllegalStateException("BedrockMQ consumer registry is incompatible");
        }
        ReflectionUtils.makeAccessible(field);
        Map<String, BedrockMessageConsumer> consumers =
                (Map<String, BedrockMessageConsumer>) ReflectionUtils.getField(field, registry);
        String key = BedrockConsumerRegistry.key(topic, consumerName);
        if (consumers == null || consumers.putIfAbsent(key, consumer) != null) {
            throw new IllegalStateException("Duplicate BedrockMQ consumer registration: " + key);
        }
        applicationContext.getBean(BedrockSubscriptionMapper.class)
                .upsert(topic, consumerName, maxRetry);
    }
}
