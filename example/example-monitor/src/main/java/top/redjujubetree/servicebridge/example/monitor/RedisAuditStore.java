package top.redjujubetree.servicebridge.example.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RedisAuditStore {

    private static final Logger log =
            LoggerFactory.getLogger(RedisAuditStore.class);

    public static final String STREAM_KEY =
            "service-bridge:example:monitor:audit";

    private final StringRedisTemplate redisTemplate;

    public RedisAuditStore(
            StringRedisTemplate redisTemplate) {

        this.redisTemplate = redisTemplate;
    }

    public void save(
            String eventType,
            String channel,
            String payload,
            Instant timestamp) {

        Map<String, String> fields =
                new LinkedHashMap<String, String>();

        fields.put(
                "eventType",
                eventType);

        fields.put(
                "channel",
                channel);

        fields.put(
                "payload",
                payload);

        fields.put(
                "timestamp",
                timestamp.toString());

        redisTemplate
                .opsForStream()
                .add(STREAM_KEY, fields);

        log.debug(
                "[MONITOR][AUDIT] saved eventType={} stream={}",
                eventType,
                STREAM_KEY);
    }
}