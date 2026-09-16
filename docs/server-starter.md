# Server Starter 接入

## Handler SPI

每个 `businessType` 只能有一个 Handler。启动时发现重复类型会直接失败，避免运行时随机路由。

```java
public interface BridgeRequestHandler {
    String businessType();
    BridgeHandlerResult handle(BridgeRequestContext context) throws Exception;
}
```

Handler 必须使用 `requestId` 保证业务幂等。BedrockMQ 是至少一次投递，数据库 CAS 只能保护 ServiceBridge 结果行，不能自动撤销已发生的外部调用、文件写入、支付或通知。

## Handler 返回语义

成功：

```java
return BridgeHandlerResult.success(result);
```

确定的业务失败：

```java
return BridgeHandlerResult.failure("INVALID_ARGUMENT", "参数不合法");
```

临时故障、明确希望 BedrockMQ 重试：

```java
throw new BridgeRetryableException("下游暂时不可用", cause);
```

除 `BridgeRetryableException` 外的异常默认转换为 `HANDLER_ERROR` 最终失败，并发送 RESPONSE。这样可以避免 BedrockMQ 重试耗尽后 ServiceBridge 无法获知最后一次失败。

## 执行令牌

每次消费都会创建随机 `attemptToken` 并写入任务表。完成 SQL 必须同时匹配：

```sql
WHERE request_id = :requestId
  AND process_status = 'PROCESSING'
  AND processing_token = :attemptToken
```

如果 BedrockMQ 超时恢复后新尝试已经开始，旧 Handler 的迟到结果影响零行，不会覆盖新结果。

不要在日志、HTTP 或消息中输出 `attemptToken`。

## 事务边界

业务 Handler 默认在结果事务之外执行，完成阶段才开启短事务：

```text
条件更新结果 + BedrockMQ RESPONSE
```

这样避免长时间持有数据库事务。代价是 Handler 的业务副作用必须自行保证幂等。如果业务必须把自身数据库更新与 ServiceBridge 结果放入同一事务，可以在 Handler 内部使用同一事务管理器，但应评估长事务风险。

## PROCESSING 恢复

可重试异常或进程崩溃可能让任务停留在 PROCESSING。恢复任务扫描 `processing_expires_at`，使用原 token 条件更新 FAILED 并发送 RESPONSE。

`processing-timeout` 是后台单次执行权期限，不是 Client HTTP 超时。它通常应不小于 BedrockMQ 的 `bedrock.mq.processing-timeout-minutes`，避免两个恢复机制的期限相互冲突。

## 自定义结果存储

业务方可实现 `BridgeResultStore` 替换默认 JDBC 实现。所有完成和恢复操作必须检查受影响行数，禁止在 token 不匹配时退化为无条件更新。
