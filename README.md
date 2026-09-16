# ServiceBridge

ServiceBridge 是基于 Spring Boot 2.7、BedrockMQ 和 Redis Pub/Sub 的异步请求响应桥接组件。Client 服务接收 HTTP JSON 请求并异步等待，Server 服务处理请求并写入共享数据库，最终通过 BedrockMQ RESPONSE 和 Redis 广播唤醒持有原 HTTP 连接的 Client 实例。

## 技术基线

| 项目 | 版本/约束 |
|---|---|
| Spring Boot | 2.7.18 |
| 构建 JDK | 17 |
| 编译目标 | Java 8 (`--release 8`) |
| BedrockMQ | 0.0.7 |
| 数据库 | MySQL 5.7+ |
| 消息语义 | 至少一次，业务 Handler 必须幂等 |

## 模块

```text
ServiceBridge
├── bridge-protocol         双方共享的消息、状态、Topic 和 DDL
├── bridge-client-starter   HTTP 等待、REQUEST 发送、RESPONSE 消费、Redis 广播
├── bridge-server-starter   REQUEST 消费、业务路由、结果持久化、RESPONSE 发送
└── example                 可运行的 Client + Server 完整链路示例
```

## 核心链路

```text
HTTP JSON
  → Client 注册 WaitContext
  → 同事务写 service_bridge_request + BedrockMQ REQUEST
  → Server 消费 REQUEST
  → Handler 处理
  → 同事务写 SUCCESS/FAILED + BedrockMQ RESPONSE
  → Client 集群中一个实例消费 RESPONSE
  → Redis Pub/Sub 广播
  → 持有 WaitContext 的实例读取数据库
  → DeferredResult 返回 HTTP
```

Redis 只传递完成通知，最终结果始终从数据库读取。Redis 通知丢失时，Client 的定时补偿和 60 秒超时检查仍会读取数据库。

## 1. 初始化数据库

先执行 BedrockMQ 自带的三张表 DDL，再执行 ServiceBridge 的 MySQL DDL：

- `bridge-protocol/src/main/resources/db/service-bridge-schema-mysql.sql`

Client、Server、BedrockMQ 必须使用同一个数据库和同一个 Spring `DataSource`，这样业务状态与 BedrockMQ 消息才能加入同一个本地事务。

## 2. Client 服务接入

```xml
<dependency>
    <groupId>com.lnsoft.erp</groupId>
    <artifactId>bridge-client-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
service:
  bridge:
    client:
      timeout: 60s
      message-source: ${spring.application.name}
      redis-channel: ${spring.profiles.active:default}:${spring.application.name}:service-bridge:completed
      max-waiting-requests: 10000
      require-active-subscriber: true
      reconciliation-enabled: true
      reconciliation-interval: 5s
```

Controller：

```java
@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ServiceBridgeClient serviceBridgeClient;

    public ReportController(ServiceBridgeClient serviceBridgeClient) {
        this.serviceBridgeClient = serviceBridgeClient;
    }

    @PostMapping
    public DeferredResult<ResponseEntity<BridgeHttpResponse>> create(
            @RequestBody JsonNode request) {
        return serviceBridgeClient.request("GENERATE_REPORT", request);
    }
}
```

## 3. Server 服务接入

```xml
<dependency>
    <groupId>com.lnsoft.erp</groupId>
    <artifactId>bridge-server-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
service:
  bridge:
    server:
      message-source: ${spring.application.name}
      processing-timeout: 15m
      recovery-enabled: true
      recovery-interval: 1m
      recovery-batch-size: 100
```

实现业务 Handler：

```java
@Component
public class GenerateReportHandler implements BridgeRequestHandler {

    @Override
    public String businessType() {
        return "GENERATE_REPORT";
    }

    @Override
    public BridgeHandlerResult handle(BridgeRequestContext context) {
        Object report = generateReport(context.getRequestId(), context.getPayload());
        return BridgeHandlerResult.success(report);
    }
}
```

普通业务失败应返回 `BridgeHandlerResult.failure(...)`。只有明确可重试的临时故障才抛出 `BridgeRetryableException`。

## 4. 构建

```bash
mvn clean test
mvn clean install
```

BedrockMQ 0.0.7 需已安装到本地或企业 Maven 仓库。

当前 ServiceBridge 使用 BedrockMQ 源码中的 `BedrockMessageConsumer` API。如果本机 Maven 缓存仍是旧的 `MessageConsumer` 构建，请先从当前 BedrockMQ 源码重新安装：

```bash
cd /Users/kenny/vibe-coding/BedrockMQ
mvn -pl bedrockmq-spring-boot-starter -am clean install
```

## 固定消息协议

```text
REQUEST topic:    service-bridge.request
REQUEST consumer: service-bridge-server

RESPONSE topic:    service-bridge.response
RESPONSE consumer: service-bridge-client
```

同一个 Server 逻辑集群的所有实例必须加载完全相同的 Handler 集合，并使用相同 consumer 名称。不同业务服务不能用不同 consumer 名称订阅同一个 REQUEST Topic，否则 BedrockMQ 的 Pub/Sub 扇出会让每个服务都收到消息。

## 进一步文档

- [运行完整示例](example/README.md)
- [架构与一致性](docs/architecture.md)
- [Client Starter 接入](docs/client-starter.md)
- [Server Starter 接入](docs/server-starter.md)
- [数据库和状态机](docs/database.md)
- [部署与运维](docs/operations.md)
