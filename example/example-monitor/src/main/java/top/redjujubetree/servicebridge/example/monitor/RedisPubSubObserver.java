package top.redjujubetree.servicebridge.example.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Configuration
public class RedisPubSubObserver {

    private static final Logger log =
            LoggerFactory.getLogger(RedisPubSubObserver.class);

    private final String redisChannel;

    private final RedisAuditStore auditStore;

    public RedisPubSubObserver(
            @Value("${example.monitor.redis-channel}")
            String redisChannel,
            RedisAuditStore auditStore) {

        this.redisChannel = redisChannel;
        this.auditStore = auditStore;
    }

    @Bean
    public RedisMessageListenerContainer redisObserverContainer(
            RedisConnectionFactory connectionFactory) {

        RedisMessageListenerContainer container =
                new RedisMessageListenerContainer();

        container.setConnectionFactory(connectionFactory);

        container.addMessageListener(
                new ObserverListener(),
                new ChannelTopic(redisChannel));

        log.info(
                "[MONITOR][PUBSUB] subscribing channel={}",
                redisChannel);

        return container;
    }

    private class ObserverListener
            implements MessageListener {

        @Override
        public void onMessage(
                Message message,
                byte[] pattern) {

            String payload =
                    new String(
                            message.getBody(),
                            StandardCharsets.UTF_8);

            String channel =
                    new String(
                            message.getChannel(),
                            StandardCharsets.UTF_8);

            Instant receivedAt =
                    Instant.now();

            log.info(
                    "[MONITOR][PUBSUB] received " +
                            "channel={} payload={}",
                    channel,
                    payload);

            auditStore.save(
                    "PUBSUB_RECEIVED",
                    channel,
                    payload,
                    receivedAt);
        }
    }
}