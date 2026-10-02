# 部署与运维

## 推荐启动顺序

1. 执行 ServiceBridge DDL；BedrockMQ provider 还需执行其自身 DDL；
2. 创建 REQUEST、RESPONSE Topic，启动 Server 消费者；
3. 启动 Redis；
4. 启动 Client；
5. 开放 HTTP 流量。

Client 先提交请求行再发送 REQUEST。BedrockMQ 没有启用订阅者时发送失败，Client 将请求标记为 FAILED。

## 配置一致性

同一 Client 集群必须使用相同 Redis Channel。不同环境共享 Redis 时，Channel 必须包含环境名，避免测试和生产互相广播。
Client 与 Server 的 `service.bridge.request-topic` 和 `service.bridge.response-topic` 必须分别一致。

使用多个请求 Topic 时，Client 与 Server 的 `service.bridge.request-topics` 映射也必须一致。每个请求 Topic 使用独立的 Server 消费组；响应 Topic 仍为单个配置。
Client 与 Server 的 `service.bridge.mq.provider` 必须一致。BedrockMQ 的 Topic 不允许包含 `:`；Kafka 与 RocketMQ 使用各自的 Topic 命名规则。

同一 Server 集群必须加载相同 `businessType` Handler 集合。

## 监控建议

至少监控：

- 当前实例 WaitContext 数量；
- 请求创建、成功、失败、HTTP 超时数量；
- Redis 发布失败、监听异常数量；
- Client 定时补偿命中数量（启用时）；
- Server Handler 时长和异常数量；
- Server stale token 更新失败数量；
- REQUEST/RESPONSE 发送失败数量及 `MQ_SEND_FAILED` 请求数量；
- 到期 PROCESSING 数量及恢复终结数量；
- 所选 MQ 的消费滞后、重试和死信数量。

## 日志安全

日志中只记录 requestId、businessType、状态和耗时。不要记录：

- 完整请求和结果 payload；
- processing token；
- Redis 密码、数据库密码；
- 个人信息和业务敏感字段。

## 故障行为

| 故障 | 行为 |
|---|---|
| Redis 通知丢失 | 默认在 HTTP 超时回调中读取数据库结果；显式开启 Client 定时补偿后可提前发现结果 |
| Redis 发布失败 | RESPONSE 消费抛异常，由所选 MQ 重试 |
| Client 实例重启 | HTTP 连接断开，后台任务和结果仍保留 |
| Server 实例崩溃 | 数据库到期租约检查终结 PROCESSING 请求 |
| REQUEST 发送失败 | 请求标记为 FAILED，HTTP 返回 503 |
| RESPONSE 发送失败 | 已提交结果保留，Client 在 HTTP 超时回调中补查；显式开启定时补偿后可提前补查 |
| RESPONSE 重复 | WaitContext 原子完成，重复通知被忽略 |
| Handler 重复执行 | 结果表受 token 保护，业务副作用需 requestId 幂等 |
