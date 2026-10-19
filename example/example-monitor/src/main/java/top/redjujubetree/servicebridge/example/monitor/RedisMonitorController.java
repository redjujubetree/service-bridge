package top.redjujubetree.servicebridge.example.monitor;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/monitor")
public class RedisMonitorController {

    private final StringRedisTemplate redisTemplate;

    public RedisMonitorController(
            StringRedisTemplate redisTemplate) {

        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/audit")
    public List<MapRecord<String, Object, Object>> audit() {

        return redisTemplate
                .opsForStream()
                .range(
                        RedisAuditStore.STREAM_KEY,
                        Range.unbounded());
    }
}