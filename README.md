# ServiceBridge

ServiceBridge 是基于 Spring Boot 2.7 的异步请求响应桥接组件。Client 接收 HTTP JSON 请求并异步等待；Server 消费 REQUEST、执行业务并写入共享数据库；Client 通过 RESPONSE 和 Redis Pub/Sub 唤醒原 HTTP 连接。MQ 可选 BedrockMQ、Kafka 或 RocketMQ。

## 模块

| 模块 | 职责 |
|---|---|
| `bridge-protocol` | 双方共享的消息、状态、Topic 和建表脚本 |
| `bridge-mq` | MQ 接口、JSON 消息发布器及三个可选适配实现 |
| `bridge-client-starter` | HTTP 等待、REQUEST 发送、RESPONSE 消费、Redis 广播 |
| `bridge-server-starter` | REQUEST 消费、业务路由、结果入库、RESPONSE 发送、超时恢复 |
| `example` | BedrockMQ 配置示例 |

Java 8 编译目标，构建使用 JDK 17。消息语义为至少一次，业务 Handler 必须按 `requestId` 幂等。

## 一致性与消息流程

```text
HTTP → Client 注册 WaitContext → 提交请求行 → 同步发送 REQUEST 到 MQ
     → Server Handler → 提交结果行 → 同步发送 RESPONSE 到 MQ
     → Client 消费 RESPONSE → Redis 广播 → 原实例查数据库并返回 HTTP
```

`service_bridge_request` 是结果真相源。REQUEST 发送失败时 Client 将请求标记为 `FAILED / MQ_SEND_FAILED` 并返回 HTTP 503。RESPONSE 只用于唤醒等待中的 Client；发送失败时业务结果仍可由 Client 的数据库补查获得。数据库提交与 MQ 发送之间没有跨系统原子事务；MQ 返回失败也可能存在实际已投递的情况。Server 用 `attemptToken` 和条件更新保护结果，Handler 对外部副作用仍须自行幂等。

Server 每 5 秒检查已过期的 `processing_expires_at`，按原 `attemptToken` 条件结束超时任务。Redis 通知丢失时，Client 的 HTTP 超时回调仍会读取数据库；需要提前补查时，可显式开启默认关闭的 Client 定时补偿。

## 数据库

在共享 MySQL 执行 `bridge-protocol/src/main/resources/db/service-bridge-schema-mysql.sql`。Client、Server 应使用同一个业务数据库。使用 BedrockMQ 时，还要执行其自带的三张表 DDL；Kafka 与 RocketMQ 不需要 BedrockMQ 的表。

## 选择 MQ

Client 与 Server 应选择同一个 provider、REQUEST Topic 和 RESPONSE Topic。两个 Starter 都会引入 `bridge-mq`，其中三个 MQ 客户端依赖均为 Maven `optional`，不会传递给业务应用。业务方只需显式加入所选 MQ 的客户端依赖。以 BedrockMQ 为例：

```xml
<dependency>
    <groupId>top.redjujubetree</groupId>
    <artifactId>bridge-client-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>top.redjujubetree</groupId>
    <artifactId>bedrockmq-spring-boot-starter</artifactId>
    <version>0.0.7</version>
</dependency>
```

Server 应以 `bridge-server-starter` 替换 Client Starter。选择 Kafka 时显式加入 `org.springframework.kafka:spring-kafka`；选择 RocketMQ 4.x 时显式加入 `org.apache.rocketmq:rocketmq-client:4.9.8`。所选 provider 缺少客户端依赖或 provider 值无效时，应用启动会报错；未选中的实现不会启动。

BedrockMQ 配置示例（数据库和 Redis 连接按部署环境配置）：

```yaml
spring:
  application:
    name: service-bridge-example

bedrock:
  mq:
    db-dialect: mysql
    node-id: service-bridge-node-1
    poll-interval-ms: 500

service:
  bridge:
    request-topic: service-bridge.request
    response-topic: service-bridge.response
    mq:
      provider: bedrock
    server:
      processing-timeout: 15m
      recovery-enabled: true
      recovery-interval-ms: 5000
```

多个业务需要使用不同的请求 Topic 时，在 Client 和 Server 配置相同的映射：

```yaml
service:
  bridge:
    request-topics:
      "[ORDER]": service-bridge.order.request
      "[PAYMENT]": service-bridge.payment.request
    response-topic: service-bridge.response
```

映射非空时，Client 只接受映射中列出的 `businessType`；Server 订阅所有不同的请求 Topic，并校验消息的业务类型与 Topic 是否匹配。多个业务可以共用一个请求 Topic。未配置映射或映射为空时，所有业务使用单个 `request-topic`（默认 `service-bridge.request`）。包含大写字母或特殊字符的业务类型应使用带引号的方括号键，以便 Spring Boot 原样绑定。

选择 Kafka 时，将 `service.bridge.mq.provider` 改为 `kafka`，并使用 Spring Boot 的 `spring.kafka.*` 配置，例如：

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      acks: all
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      enable-auto-commit: false
```

Kafka 需预建 REQUEST、RESPONSE 及各自的 `.DLT` Topic，DLT 分区数应不少于源 Topic。选择 RocketMQ 4.x 时，将 provider 改为 `rocketmq` 并配置 `service.bridge.mq.rocketmq.namesrv-addr`；重试耗尽的消息由 RocketMQ 放入死信队列。

三个适配器都按 consumer group 竞争消费：同一请求 Topic 的 Server 实例使用相同消费组，不同请求 Topic 使用不同消费组；Client 实例使用 `service-bridge-client` 消费组。每个 Server 逻辑集群应加载相同的 Handler 集合；不同业务集群应使用不同 REQUEST Topic。

## 构建

```bash
mvn clean test
```

更多说明见 [架构与一致性](docs/architecture.md)、[Client Starter](docs/client-starter.md)、[Server Starter](docs/server-starter.md)、[数据库](docs/database.md) 和 [运维](docs/operations.md)。
