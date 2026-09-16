# 架构与一致性

## 组件职责

Client Starter 是 HTTP 请求侧组件，负责建立 `requestId → DeferredResult` 本机映射。Server Starter 是后台处理侧组件，只关心业务执行和结果写入，不依赖 Redis，也不感知 HTTP 连接是否仍然存在。

`bridge-protocol` 是双方唯一的线上协议来源。协议版本、Topic、consumer 名、状态枚举和消息字段不能在两个 Starter 内各自复制。

## 提交阶段

Client 必须先注册 `WaitContext`，再执行数据库事务：

```text
1. 生成 requestId
2. 注册 WaitContext
3. 开启事务
4. 插入 service_bridge_request(PENDING/WAITING)
5. MessageProducer.send(REQUEST)
6. 提交事务
```

BedrockMQ 与业务表共用一个 DataSource，因此步骤 4 和 5 同时提交或同时回滚。BedrockMQ 消费者在提交前看不到 REQUEST，避免响应早于 `WaitContext` 注册。

## 完成阶段

Server Handler 返回后，结果和 RESPONSE 也在同一事务提交：

```text
1. 使用 requestId + attemptToken 条件更新 SUCCESS/FAILED
2. 更新成功后 MessageProducer.send(RESPONSE)
3. 提交事务
```

结果更新失败通常表示当前执行已失去所有权，不能发送 RESPONSE，也不能无条件覆盖新执行结果。

## RESPONSE 与 Redis

BedrockMQ RESPONSE 由 Client 集群中的一个实例消费。该实例将只包含 `requestId` 和终态的消息发布到 Redis。所有 Client 实例收到广播，但只有本机注册表中存在对应 `requestId` 的实例访问数据库并完成 HTTP 响应。

这个过程是至少一次的：

- RESPONSE 可能重复消费；
- Redis 消息可能重复；
- Redis 发布成功后进程可能在 BedrockMQ 标记完成前崩溃。

重复消息是安全的，因为 `WaitContext.tryClaim()` 只允许一个线程取得完成权，数据库状态更新也使用条件 SQL。

## Redis 丢消息

Redis Pub/Sub 不持久化通知，因此 Client 有三层保护：

1. REQUEST 提交后立即补查一次数据库；
2. 默认每 5 秒批量检查本机等待请求；
3. HTTP 超时回调执行数据库原子判断。

数据库是结果真相源，Redis 只降低响应延迟。

## HTTP 超时与后台处理分离

`wait_status` 和 `process_status` 是两个独立状态机。Client 超时只把 `wait_status` 更新为 `TIMED_OUT`，Server 仍然可以继续将 `process_status` 更新为 `SUCCESS` 或 `FAILED`。

因此允许：

```text
wait_status    = TIMED_OUT
process_status = SUCCESS
```

这表示用户没有在 60 秒内拿到结果，但后台结果已被保留，可通过后续查询接口读取。

## 多实例约束

Client 所有实例使用同一个 RESPONSE consumer 名。BedrockMQ 只让一个实例消费某条 RESPONSE，再通过 Redis 广播定位 HTTP 连接。

Server 所有实例使用同一个 REQUEST consumer 名。它们必须是同一逻辑服务，加载相同 Handler 集合。若需要多个互不相同的业务处理服务，应改为独立 Topic 或增加统一 Dispatcher，不能简单注册不同 consumer 名。
