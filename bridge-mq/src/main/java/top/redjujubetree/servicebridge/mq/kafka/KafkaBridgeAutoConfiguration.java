package top.redjujubetree.servicebridge.mq.kafka;

import top.redjujubetree.servicebridge.mq.BridgeInboundHandler;
import top.redjujubetree.servicebridge.mq.BridgeTransport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Configuration
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
@ConditionalOnProperty(prefix = "service.bridge.mq", name = "provider", havingValue = "kafka")
public class KafkaBridgeAutoConfiguration {
    @Bean
    public BridgeTransport kafkaBridgeTransport(KafkaTemplate<String, String> template) {
        return (topic, key, payload) -> {
            try {
                template.send(topic, key, payload).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Kafka send interrupted", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("Kafka send failed", e);
            }
        };
    }

    @Bean
    public SmartLifecycle kafkaBridgeConsumers(ConsumerFactory<String, String> factory,
            KafkaTemplate<String, String> template, List<BridgeInboundHandler> handlers) {
        return new SmartLifecycle() {
            private final List<KafkaMessageListenerContainer<String, String>> containers = new ArrayList<>();
            private boolean running;

            @Override
            public void start() {
                for (BridgeInboundHandler handler : handlers) {
                    for (String topic : handler.topics()) {
                        ContainerProperties properties = new ContainerProperties(topic);
                        properties.setGroupId(handler.consumerGroup(topic));
                        properties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
                        properties.setMessageListener((AcknowledgingMessageListener<String, String>) (record, ack) -> {
                            try {
                                handler.handle(topic, record.value());
                                ack.acknowledge();
                            } catch (Exception e) {
                                throw new IllegalStateException("Bridge message handling failed", e);
                            }
                        });
                        KafkaMessageListenerContainer<String, String> container =
                                new KafkaMessageListenerContainer<>(factory, properties);
                        DefaultErrorHandler errors = new DefaultErrorHandler(
                                new DeadLetterPublishingRecoverer(template), new FixedBackOff(1000L, 3L));
                        errors.setCommitRecovered(true);
                        container.setCommonErrorHandler(errors);
                        container.start();
                        containers.add(container);
                    }
                }
                running = true;
            }

            @Override
            public void stop() {
                for (KafkaMessageListenerContainer<String, String> container : containers) {
                    container.stop();
                }
                containers.clear();
                running = false;
            }

            @Override
            public boolean isRunning() {
                return running;
            }
        };
    }
}
