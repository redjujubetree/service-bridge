# 部署与运维

## 推荐启动顺序

1. 执行 BedrockMQ 和 ServiceBridge DDL；
2. 启动 Server，等待 `service-bridge.request/service-bridge-server` 订阅注册；
3. 启动 Redis；
4. 启动 Client；
5. 开放 HTTP 流量。

BedrockMQ 在发送消息时按当时已启用的订阅创建消费记录。如果 REQUEST Topic 没有订阅，之后启动 Server 不会自动为旧消息补建消费记录。Client 默认在发送前检查订阅，没有订阅时返回 503。

## 配置一致性

同一 Client 集群必须使用相同 Redis Channel。不同环境共享 Redis 时，Channel 必须包含环境名，避免测试和生产互相广播。

同一 Server 集群必须加载相同 `businessType` Handler 集合。滚动发布期间新增 Handler 时，应先完成所有 Server 实例部署，再允许 Client 发送新业务类型。

## 监控建议

至少监控：

- 当前实例 WaitContext 数量；
- 请求创建、成功、失败、HTTP 超时数量；
- Redis 发布失败、监听异常数量；
- Client 定时补偿命中数量；
- Server Handler 时长和异常数量；
- Server stale token 更新失败数量；
- PROCESSING 恢复数量；
- BedrockMQ PENDING/PROCESSING/FAILED 数量。

## 日志安全

日志中只记录 requestId、businessType、状态和耗时。不要记录：

- 完整请求和结果 payload；
- processing token；
- Redis 密码、数据库密码；
- 个人信息和业务敏感字段。

## 故障行为

| 故障 | 行为 |
|---|---|
| Redis 通知丢失 | 定时补偿或超时检查从数据库读取结果 |
| Redis 发布失败 | RESPONSE 消费抛异常，BedrockMQ 重试 |
| Client 实例重启 | HTTP 连接断开，后台任务和结果仍保留 |
| Server 实例崩溃 | BedrockMQ 和 Server processing deadline 恢复 |
| RESPONSE 重复 | WaitContext 原子完成，重复通知被忽略 |
| Handler 重复执行 | 结果表受 token 保护，业务副作用需 requestId 幂等 |

## 升级协议

修改消息字段时优先做向后兼容新增。破坏性变更必须提升 `protocolVersion`，并安排 Client/Server 双读或双写窗口。当前验证器拒绝非 `1.0` 消息，未经兼容设计不要单边升级版本。
