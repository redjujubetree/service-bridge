# 架构与一致性

Client Starter 维护本机 `requestId → DeferredResult`；Server Starter 执行业务并写结果；`bridge-protocol` 定义双方消息和状态。两个 Starter 依赖 `bridge-mq` 的统一接口；该模块内的 BedrockMQ、Kafka、RocketMQ 自动配置按 `service.bridge.mq.provider` 与客户端类是否存在分别启用。业务应用仅提供所选 MQ 的客户端依赖。Client 和 Server 必须使用相同的 provider 与 Topic。

## REQUEST 提交

Client 先注册 `WaitContext` 并提交 `service_bridge_request` 的 PENDING 行，然后同步发送 REQUEST。发送失败时，另起数据库事务将仍在 PENDING/PROCESSING 的请求标记为 `FAILED / MQ_SEND_FAILED`，返回 HTTP 503。数据库提交与 MQ 发送不构成跨系统原子事务；MQ 返回失败但消息已投递时，业务 Handler 仍可能产生外部副作用，因此必须幂等。

## 处理与结果

Server 消费 REQUEST 后使用 `requestId` 和 `attemptToken` 取得处理权。未到期的 `PROCESSING` 请求不会被重复执行。Handler 完成后，Server 条件更新 SUCCESS/FAILED 并提交数据库事务；Client 仍等待时，在提交后发送 RESPONSE。RESPONSE 发送失败不会回滚已经提交的业务结果，Client 仍可通过数据库补查获得结果。

MQ 消费成功后才确认消息。`BridgeRetryableException` 会先按 token 释放执行权，再由 MQ 重试。Kafka 将重试耗尽的消息送入 `.DLT` Topic；RocketMQ 使用其重试及死信机制；BedrockMQ 使用订阅重试。死信中的 REQUEST 需由运维排查或重放。业务 Handler 对外部系统产生的副作用必须按 `requestId` 幂等，数据库条件更新无法撤销外部副作用。

## 超时恢复

Server 周期性查询 `PROCESSING` 且 `processing_expires_at` 已到期的行；终结失败时再次按 `attemptToken` 和到期时间执行条件更新。扫描结果过期或旧 Handler 迟到时不会覆盖新尝试。`processing-timeout` 是后台处理权期限，与 Client HTTP 等待超时相互独立。

## RESPONSE 与 Redis

Client 集群中的一个实例消费 RESPONSE，向 Redis 发布只含请求标识和终态的通知。所有 Client 实例接收广播，只有持有该请求 `WaitContext` 的实例查数据库并完成 HTTP 响应。Redis Pub/Sub 不持久化通知，Client 还会在 REQUEST 提交后立即补查、周期性补查，并在 HTTP 超时回调中执行数据库检查。

`wait_status` 和 `process_status` 独立。HTTP 已超时但后台执行成功时，允许 `TIMED_OUT / SUCCESS`；结果仍可通过查询接口读取。
