package top.redjujubetree.servicebridge.example.monitor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/monitor")
public class RedisMonitorController {

    private static final Logger log =
            LoggerFactory.getLogger(RedisMonitorController.class);

    private static final String FIELD_PAYLOAD =
            "payload";

    private final StringRedisTemplate redisTemplate;

    private final RedisAuditStore auditStore;

    private final ObjectMapper objectMapper;

    public RedisMonitorController(
            StringRedisTemplate redisTemplate,
            RedisAuditStore auditStore,
            ObjectMapper objectMapper) {

        this.redisTemplate = redisTemplate;
        this.auditStore = auditStore;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/audit")
    public List<AuditRecordView> audit() {

        List<MapRecord<String, Object, Object>> records =
                redisTemplate
                        .opsForStream()
                        .range(
                                RedisAuditStore.STREAM_KEY,
                                Range.unbounded());

        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        List<AuditRecordView> views =
                new ArrayList<>(records.size());

        for (MapRecord<String, Object, Object> record : records) {
            views.add(toView(record));
        }

        return views;
    }

    /**
     * 把 Stream 记录转成返回结构。
     *
     * <p>payload 按 JSON 解析成对象，避免接口直接返回
     * 带转义符的字符串。</p>
     */
    private AuditRecordView toView(
            MapRecord<String, Object, Object> record) {

        Map<String, Object> value =
                new LinkedHashMap<>();

        for (Map.Entry<Object, Object> entry : record.getValue().entrySet()) {

            String field = String.valueOf(entry.getKey());

            value.put(
                    field,
                    FIELD_PAYLOAD.equals(field)
                            ? parseJson(entry.getValue())
                            : entry.getValue());
        }

        return new AuditRecordView(
                RedisAuditStore.STREAM_KEY,
                value,
                record.getId());
    }

    /**
     * payload 解析成 JSON；解析不了（比如纯文本）就按原样返回。
     */
    private Object parseJson(
            Object payload) {

        if (payload == null) {
            return null;
        }

        String text = payload.toString();

        if (text.trim().isEmpty()) {
            return text;
        }

        try {
            JsonNode node = objectMapper.readTree(text);

            return node == null ? text : node;
        } catch (JsonProcessingException ex) {
            return text;
        }
    }

    /**
     * 删除审计数据。
     *
     * <ul>
     *   <li>{@code DELETE /monitor/audit}：清空全部审计记录</li>
     *   <li>{@code DELETE /monitor/audit?before=2026-10-01}：只删除该日期（不含当天）之前的记录</li>
     * </ul>
     */
    @DeleteMapping("/audit")
    public AuditDeleteResult deleteAudit(
            @RequestParam(name = "before", required = false)
            String before) {

        if (before == null || before.trim().isEmpty()) {
            return deleteAllAudit();
        }

        Instant threshold = parseDate(before);

        long deleted = auditStore.deleteBefore(threshold);

        log.info(
                "[MONITOR][AUDIT] delete by before={} deleted={}",
                before.trim(),
                deleted);

        return new AuditDeleteResult(
                deleted,
                auditStore.count(),
                "before=" + before.trim());
    }

    private AuditDeleteResult deleteAllAudit() {

        long deleted = auditStore.deleteAll();

        log.info(
                "[MONITOR][AUDIT] delete all deleted={}",
                deleted);

        return new AuditDeleteResult(
                deleted,
                auditStore.count(),
                "all");
    }

    /**
     * 把 {@code yyyy-MM-dd} 解析成该日期 0 点（服务器时区）的时刻。
     */
    private Instant parseDate(
            String before) {

        try {
            return LocalDate
                    .parse(before.trim())
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant();
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "before 参数需为 yyyy-MM-dd 格式的日期，例如 2026-10-01");
        }
    }

    /**
     * 审计记录的返回结构。
     *
     * <p>字段与 Redis Stream 记录保持一致：
     * {@code stream}、{@code value}、{@code id}，
     * 其中 {@code value} 里的 payload 是 JSON 对象而不是字符串。</p>
     */
    public static final class AuditRecordView {

        private final String stream;

        private final Map<String, Object> value;

        private final RecordId id;

        public AuditRecordView(
                String stream,
                Map<String, Object> value,
                RecordId id) {

            this.stream = stream;
            this.value = value;
            this.id = id;
        }

        /** Stream 的 key。 */
        public String getStream() {
            return stream;
        }

        /** 记录内容，payload 已解析成 JSON。 */
        public Map<String, Object> getValue() {
            return value;
        }

        /** 记录 ID。 */
        public RecordId getId() {
            return id;
        }
    }

    /**
     * 删除结果。
     */
    public static final class AuditDeleteResult {

        private final long deleted;

        private final long remaining;

        private final String scope;

        public AuditDeleteResult(
                long deleted,
                long remaining,
                String scope) {

            this.deleted = deleted;
            this.remaining = remaining;
            this.scope = scope;
        }

        /** 本次删除的记录条数。 */
        public long getDeleted() {
            return deleted;
        }

        /** 删除后剩余的记录条数。 */
        public long getRemaining() {
            return remaining;
        }

        /** 本次删除的范围。 */
        public String getScope() {
            return scope;
        }
    }
}
