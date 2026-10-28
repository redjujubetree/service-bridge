package top.redjujubetree.servicebridge.example.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RedisAuditStore {

    private static final Logger log =
            LoggerFactory.getLogger(RedisAuditStore.class);

    public static final String STREAM_KEY =
            "service-bridge:example:monitor:audit";

    private static final String FIELD_TIMESTAMP =
            "timestamp";

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
                FIELD_TIMESTAMP,
                timestamp.toString());

        redisTemplate
                .opsForStream()
                .add(STREAM_KEY, fields);

        log.debug(
                "[MONITOR][AUDIT] saved eventType={} stream={}",
                eventType,
                STREAM_KEY);
    }

    /**
     * 当前积压的审计记录条数。
     */
    public long count() {

        Long size = redisTemplate
                .opsForStream()
                .size(STREAM_KEY);

        return size == null ? 0L : size;
    }

    /**
     * 清空整个审计 Stream（连同 key 一起删除）。
     *
     * @return 被删除的记录条数（key 不存在时返回 0）
     */
    public long deleteAll() {

        long size = count();

        Boolean deleted = redisTemplate.delete(STREAM_KEY);

        if (!Boolean.TRUE.equals(deleted)) {
            return 0L;
        }

        log.info(
                "[MONITOR][AUDIT] cleared stream={} deleted={}",
                STREAM_KEY,
                size);

        return size;
    }

    /**
     * 删除 timestamp 字段早于给定时间的审计记录。
     *
     * <p>timestamp 无法解析的历史脏数据会一并清理。</p>
     */
    public long deleteBefore(
            Instant threshold) {

        List<MapRecord<String, Object, Object>> records =
                redisTemplate
                        .opsForStream()
                        .range(STREAM_KEY, Range.unbounded());

        if (records == null || records.isEmpty()) {
            return 0L;
        }

        String[] recordIds = records
                .stream()
                .filter(record -> isBefore(record, threshold))
                .map(record -> record.getId().getValue())
                .toArray(String[]::new);

        if (recordIds.length == 0) {
            return 0L;
        }

        Long deleted = redisTemplate
                .opsForStream()
                .delete(STREAM_KEY, recordIds);

        long result = deleted == null ? 0L : deleted;

        log.info(
                "[MONITOR][AUDIT] trimmed stream={} before={} deleted={}",
                STREAM_KEY,
                threshold,
                result);

        return result;
    }

    private boolean isBefore(
            MapRecord<String, Object, Object> record,
            Instant threshold) {

        Object timestamp = record
                .getValue()
                .get(FIELD_TIMESTAMP);

        if (timestamp == null) {
            return true;
        }

        try {
            return Instant
                    .parse(timestamp.toString())
                    .isBefore(threshold);
        } catch (DateTimeParseException ex) {
            return true;
        }
    }
}
