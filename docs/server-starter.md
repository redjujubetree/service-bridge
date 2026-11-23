# Server Starter 接入

在 Server 配置 `service.bridge.request-topic` 和 `service.bridge.response-topic`，与 Client 保持一致；省略时分别使用 `service-bridge.request` 和 `service-bridge.response`。

多个请求 Topic 使用 `service.bridge.request-topics` 配置业务类型到 Topic 的映射，且与 Client 保持一致。Server 为每个不同的请求 Topic 建立订阅，并拒绝业务类型与收到的 Topic 不匹配的消息；多个业务可共用一个 Topic。配置映射后，单个 `request-topic` 不再用于请求订阅。

ServiceBridge 通过 BedrockMQ 0.1.0 的 `BedrockConsumerRegistry.registerConsumer` 注册请求处理器。注册会创建订阅并启动该 Topic 和消费组的轮询；已有订阅的启停状态和最大重试次数保持不变。相同组合重复注册会导致启动失败。

## Handler SPI

每个 `businessType` 只能有一个 Handler。启动时发现重复类型会直接失败，避免运行时随机路由。

```java
public interface BridgeRequestHandler {
    String businessType();
    BridgeHandlerResult handle(BridgeRequestContext context) throws Exception;
}
```

Handler 必须使用 `requestId` 保证业务幂等。BedrockMQ 按至少一次投递处理，数据库 CAS 只能保护 ServiceBridge 结果行，不能自动撤销已发生的外部调用、文件写入、支付或通知。

## Handler 返回语义

成功：

```java
return BridgeHandlerResult.success(result);
```

确定的业务失败：

```java
return BridgeHandlerResult.failure("INVALID_ARGUMENT", "参数不合法");
```

临时故障、明确希望 MQ 重试：

```java
throw new BridgeRetryableException("下游暂时不可用", cause);
```

该异常会按本次 `attemptToken` 将 PROCESSING 恢复为 PENDING，再交由 MQ 重试。重试次数耗尽后应监控并处理死信消息；不能假设 MQ 会无限重试。

除 `BridgeRetryableException` 外的异常默认转换为 `HANDLER_ERROR` 最终失败；Client 仍在等待时发送 RESPONSE 通知。

## 执行令牌

每次消费尝试都会创建随机 `attemptToken`；取得处理权后才会将其写入请求表。完成 SQL 必须同时匹配：

```sql
WHERE request_id = :requestId
  AND process_status = 'PROCESSING'
  AND processing_token = :attemptToken
```

重复消息不会取得未过期的执行权。到期后新尝试若已开始，旧 Handler 的迟到结果影响零行，不会覆盖新结果。

`attemptToken` 只用于数据库执行权判断，不要写入日志、HTTP 响应或对外业务消息。

## 事务边界

业务 Handler 默认在结果事务之外执行，完成阶段才开启短事务：

```text
条件更新结果 + 检查 Client 等待状态，提交后发送 RESPONSE 通知
```

这样避免长时间持有数据库事务。Handler 的业务副作用必须自行保证幂等。Handler 内自行开启的事务会在 ServiceBridge 结果事务之前完成，因此当前默认流程不保证业务写入和 ServiceBridge 结果写入的原子性；如业务要求两者同事务，需要定制执行流程与事务边界。

## PROCESSING 恢复

进程崩溃、执行超时或重试状态释放失败可能让任务停留在 PROCESSING。Server 默认每 5 秒最多检查 100 条到期记录，仅在原 token 有效且 `processing_expires_at` 已到期时更新 FAILED；Client 仍在等待且未过截止时间时发送 RESPONSE。可用 `service.bridge.server.recovery-interval-ms` 调整检查间隔。

`processing-timeout` 是后台单次执行权期限，不是 Client HTTP 超时。

## 自定义结果存储

业务方可实现 `BridgeResultStore` 替换默认 JDBC 实现。所有完成和恢复操作必须检查受影响行数，禁止在 token 不匹配或尚未过期时退化为无条件更新。`isClientWaiting` 还需同时检查等待状态与截止时间，并与结果更新使用同一事务。
