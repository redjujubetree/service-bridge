package top.redjujubetree.servicebridge.mq.rocketmq;

import top.redjujubetree.servicebridge.mq.BridgeInboundHandler;
import top.redjujubetree.servicebridge.mq.BridgeTransport;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Configuration
@ConditionalOnClass(name = "org.apache.rocketmq.client.producer.DefaultMQProducer")
@ConditionalOnProperty(prefix = "service.bridge.mq", name = "provider", havingValue = "rocketmq")
public class RocketBridgeAutoConfiguration {
    @Bean(initMethod = "start", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(DefaultMQProducer.class)
    public DefaultMQProducer bridgeRocketProducer(
            @Value("${service.bridge.mq.rocketmq.namesrv-addr}") String namesrv) {
        DefaultMQProducer producer = new DefaultMQProducer("service-bridge-producer");
        producer.setNamesrvAddr(namesrv);
        return producer;
    }

    @Bean
    public BridgeTransport rocketBridgeTransport(DefaultMQProducer producer) {
        return (topic, key, payload) -> {
            Message message = new Message(topic, payload.getBytes(StandardCharsets.UTF_8));
            message.setKeys(key);
            try {
                if (producer.send(message).getSendStatus() != SendStatus.SEND_OK) {
                    throw new IllegalStateException("RocketMQ did not confirm send");
                }
            } catch (MQClientException | RemotingException | MQBrokerException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException("RocketMQ send failed", e);
            }
        };
    }

    @Bean
    public SmartLifecycle rocketBridgeConsumers(List<BridgeInboundHandler> handlers,
            @Value("${service.bridge.mq.rocketmq.namesrv-addr}") String namesrv) {
        return new SmartLifecycle() {
            private final List<DefaultMQPushConsumer> consumers = new ArrayList<>();
            private boolean running;

            @Override
            public void start() {
                try {
                    for (BridgeInboundHandler handler : handlers) {
                        for (String topic : handler.topics()) {
                            DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(handler.consumerGroup(topic));
                            consumer.setNamesrvAddr(namesrv);
                            consumer.subscribe(topic, "*");
                            consumer.setMaxReconsumeTimes(10);
                            consumer.registerMessageListener((org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently)
                                    (messages, context) -> {
                                        try {
                                            for (org.apache.rocketmq.common.message.MessageExt message : messages) {
                                                handler.handle(topic, new String(message.getBody(), StandardCharsets.UTF_8));
                                            }
                                            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                                        } catch (Exception e) {
                                            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                                        }
                                    });
                            consumer.start();
                            consumers.add(consumer);
                        }
                    }
                    running = true;
                } catch (MQClientException e) {
                    stop();
                    throw new IllegalStateException("RocketMQ consumer startup failed", e);
                }
            }

            @Override
            public void stop() {
                for (DefaultMQPushConsumer consumer : consumers) {
                    consumer.shutdown();
                }
                consumers.clear();
                running = false;
            }

            @Override
            public boolean isRunning() {
                return running;
            }
        };
    }
}
