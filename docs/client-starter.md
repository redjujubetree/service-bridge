# Client Starter 接入

## 公共 API

```java
DeferredResult<ResponseEntity<BridgeHttpResponse>> request(
        String businessType,
        JsonNode payload);

DeferredResult<ResponseEntity<BridgeHttpResponse>> request(
        String businessType,
        JsonNode payload,
        String traceId);

Optional<BridgeStoredResult> query(String requestId);
```

Controller 不应调用 `getResult()`、`Thread.sleep()` 或循环查询。直接返回 `DeferredResult`，Spring MVC 会释放当前 Servlet 工作线程，在结果到达后重新调度响应。

`query` 用于客户端断线或 HTTP 已超时后的主动查询。它不会修改 `wait_status`。

## 响应结构

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
| 没有 REQUEST 订阅 | 503 | `SERVICE_UNAVAILABLE` |
| 等待容量达到上限 | 503 | `SERVICE_UNAVAILABLE` |
| 请求或 MQ 事务提交失败 | 503 | `SERVICE_UNAVAILABLE` |

如业务系统要求统一返回 HTTP 200，可自定义 `BridgeResponseFactory`；默认自动配置将在业务方声明同类型 Bean 后让出，但修改时应同步调整自动配置条件。

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

`DeferredResult` 不长期占用 Servlet 工作线程，但仍占用 TCP 连接、文件描述符和少量内存。生产容量测试应同时覆盖应用、Ingress、负载均衡和客户端超时。

## 调用链超时

业务超时默认 60 秒。下列超时应略大于它，建议 65～70 秒：

- `spring.mvc.async.request-timeout`
- Nginx `proxy_read_timeout`
- Ingress/Gateway timeout
- 云负载均衡 idle timeout
- 前端 HTTP 客户端 timeout
