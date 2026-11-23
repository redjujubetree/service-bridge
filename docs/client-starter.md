# Client Starter 接入

## 配置示例

业务应用引入 `top.redjujubetree:bridge-client-starter:0.2.1`、MySQL JDBC 驱动 `com.mysql:mysql-connector-j`，以及 BedrockMQ 客户端依赖 `top.redjujubetree:bedrockmq-spring-boot-starter:0.1.0`。

以下示例中的 MySQL 数据库必须与 Server 共用，且已执行 ServiceBridge DDL。Redis Channel 在同一 Client 集群内保持一致。各示例的请求 Topic 和响应 Topic 均须与 Server 配置一致。

### 示例一：BedrockMQ，所有业务共用一个请求 Topic

BedrockMQ 还需要执行其自身的数据库 DDL，并为请求 Topic 启用 Server 订阅者。

```yaml
spring:
  application:
    name: order-service
  datasource:
    url: jdbc:mysql://localhost:3306/service_bridge
    username: service_bridge
    password: change-me
  redis:
    host: localhost
    port: 6379

bedrock:
  mq:
    db-dialect: mysql
    node-id: order-client-1
    poll-interval-ms: 500

service:
  bridge:
    mq:
      provider: bedrock
    request-topic: service-bridge.request
    response-topic: service-bridge.response
    client:
      timeout: 60s
      redis-channel: order-service:service-bridge:completed
      max-waiting-requests: 10000
```

此配置下，`request("ORDER", ...)` 和 `request("PAYMENT", ...)` 都发送到 `service-bridge.request`。BedrockMQ 的 `message-source` 优先取显式配置，其次取 `spring.application.name`；两者都未配置时使用 `bridge-client-starter`。

### 示例二：BedrockMQ，按业务使用不同请求 Topic

在示例一的基础上，将 `request-topic` 替换为以下映射；其他配置保持相同：

```yaml
service:
  bridge:
    request-topics:
      "[ORDER]": service-bridge.order.request
      "[PAYMENT]": service-bridge.payment.request
```

`request("ORDER", ...)` 发送到 `service-bridge.order.request`，`request("PAYMENT", ...)` 发送到 `service-bridge.payment.request`。映射非空时，`request("OTHER", ...)` 会在发送前报错。大写业务类型使用带引号的方括号键，以便 Spring Boot 保留原始大小写。Server 需要配置相同的映射并订阅这两个请求 Topic。

### 显式开启 Client 定时补偿

上述示例默认不开启定时补偿。需要在完成通知丢失时定期补查数据库，可在任一示例的 `service.bridge.client` 下增加：

```yaml
service:
  bridge:
    client:
      reconciliation-enabled: true
      reconciliation-interval: 5s
```

## 公共 API

```java
DeferredResult<ResponseEntity<BridgeHttpResponse>> request(
        String businessType,
        JsonNode payload);

DeferredResult<ResponseEntity<BridgeHttpResponse>> request(
        String businessType,
        JsonNode payload,
        String traceId);

<T> DeferredResult<ResponseEntity<T>> request(
        String businessType,
        JsonNode payload,
        String traceId,
        BridgeResponseHandler<T> responseHandler);

Optional<BridgeStoredResult> query(String requestId);
```

Controller 不应调用 `getResult()`、`Thread.sleep()` 或循环查询。直接返回 `DeferredResult`，Spring MVC 会释放当前 Servlet 工作线程，在结果到达后重新调度响应。

前三个参数的调用沿用默认的 `BridgeHttpResponse`。第四个重载允许按请求提供 `BridgeResponseHandler<T>`，由业务方生成完整的 `ResponseEntity<T>`，包括 HTTP 状态和响应体；`traceId` 不需要时传 `null`。处理器的 `fromStored` 收到数据库中的最终结果，`timeout` 处理 HTTP 等待超时，`unavailable` 处理等待容量不足、请求入库或 MQ 发送失败等客户端故障。三个方法都应返回非空响应。处理器若作为共享 Bean 使用，需保证线程安全。

`traceId` 可为 `null`，非空时最多 127 个字符；超长会在提交请求前抛出 `IllegalArgumentException`。

`query` 用于客户端断线或 HTTP 已超时后的主动查询。它不会修改 `wait_status`。

`service.bridge.request-topic` 与 `service.bridge.response-topic` 可配置，必须与 Server 使用相同值。

多个业务分别发送到不同请求 Topic 时，配置 `service.bridge.request-topics` 映射，例如 `"[ORDER]": service-bridge.order.request`、`"[PAYMENT]": service-bridge.payment.request`。Client 按 `businessType` 选择 Topic：未配置映射或映射为空时，所有业务使用 `service.bridge.request-topic`（默认 `service-bridge.request`）；映射非空时，未列出的业务类型会在发送前报错。Client 与 Server 的映射应一致；响应 Topic 仍使用单个 `service.bridge.response-topic`。大写业务类型使用带引号的方括号键，保证 Spring Boot 原样绑定。

Client 与 Server 都需引入 BedrockMQ 客户端依赖，并将 `service.bridge.mq.provider` 配置为 `bedrock`。

Client 定时从数据库补查等待中的请求结果默认关闭。需要在完成通知丢失时提前补回 HTTP 响应，可显式配置 `service.bridge.client.reconciliation-enabled: true`；检查间隔由 `service.bridge.client.reconciliation-interval` 控制，默认 5 秒。关闭时，HTTP 超时回调仍会查询数据库中的最终结果。

## 默认响应结构

```json
{
  "requestId": "c8cc...",
  "code": "SUCCESS",
  "message": null,
  "data": {}
}
```

默认 HTTP 状态：

| 场景 | HTTP | code |
|---|---:|---|
| 成功 | 200 | `SUCCESS` |
| Server 业务失败 | 422 | Server error code 或 `FAILED` |
| 60 秒未完成 | 408 | `TIMEOUT` |
| 等待容量达到上限 | 503 | `SERVICE_UNAVAILABLE` |
| 请求提交或 REQUEST 发送失败 | 503 | `SERVICE_UNAVAILABLE` |

业务系统可为单次请求指定自己的返回类型，例如直接返回 Server 写入的 JSON 结果：

```java
BridgeResponseHandler<JsonNode> responses = new BridgeResponseHandler<JsonNode>() {
    public ResponseEntity<JsonNode> fromStored(BridgeStoredResult stored) {
        if (stored.getProcessStatus() == BridgeProcessStatus.SUCCESS) {
            try {
                return ResponseEntity.ok(objectMapper.readTree(stored.getResultPayload()));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Invalid stored result", e);
            }
        }
        ObjectNode error = objectMapper.createObjectNode()
                .put("code", stored.getErrorCode())
                .put("message", stored.getErrorMessage());
        return ResponseEntity.status(422).body(error);
    }

    public ResponseEntity<JsonNode> timeout(String requestId) {
        return ResponseEntity.status(408).body(
                objectMapper.createObjectNode().put("requestId", requestId).put("code", "TIMEOUT"));
    }

    public ResponseEntity<JsonNode> unavailable(String requestId, String message) {
        return ResponseEntity.status(503).body(
                objectMapper.createObjectNode().put("requestId", requestId).put("message", message));
    }
};

return serviceBridgeClient.request("GENERATE_REPORT", payload, null, responses);
```

需要统一调整所有默认响应时，也可声明自己的 `DefaultBridgeResponseHandler` Bean 覆盖自动配置；这种方式的返回体仍是 `BridgeHttpResponse`，业务自定义返回类型应使用上述重载。
如果业务自行实现了 `ServiceBridgeClient`，还需实现这个新重载；接口的默认实现会抛出 `UnsupportedOperationException`。

## 自定义请求存储

默认实现为 `JdbcBridgeRequestStore`。业务系统可以声明自己的 Bean：

```java
@Bean
public BridgeRequestStore bridgeRequestStore() {
    return new CustomBridgeRequestStore();
}
```

必须保证：

- `create` 参与调用方事务；
- `markTimedOutIfWaitingAndUnfinished` 是单条原子条件更新；
- `markRespondedIfWaiting` 只允许终态结果变为 RESPONDED；
- `findTerminal` 应尽量批量查询，避免 N+1。

## 容量

`max-waiting-requests` 是每个实例的精确上限，由 `Semaphore` 控制。达到上限后不会写数据库或发送 MQ。

`DeferredResult` 不长期占用 Servlet 工作线程，但仍占用 TCP 连接、文件描述符和少量内存。

## 调用链超时

业务超时默认 60 秒。下列超时应略大于它，建议 65～70 秒：

- `spring.mvc.async.request-timeout`
- Nginx `proxy_read_timeout`
- 云负载均衡 idle timeout
- 前端 HTTP 客户端 timeout
