# ServiceBridge Example

示例由三个独立应用组成：`example-client` 提供 HTTP 接口并发送请求，`example-server` 执行业务，`example-monitor` 可选，用于观察 Redis 通知。Client 和 Server 共用 MySQL，两个业务共用一个请求 Topic：

```text
POST /reports
  → ServiceBridgeClient
  → service-bridge.request
  → GenerateReportHandler
  → service-bridge.response
  → Redis 通知
  → HTTP 响应

POST /invoices/validate
  → ServiceBridgeClient
  → service-bridge.request
  → ValidateInvoiceHandler
  → service-bridge.response
  → Redis 通知
  → HTTP 响应
```

Client 和 Server 的 `application.yml` 均将 `service.bridge.request-topic` 设为 `service-bridge.request`，让 `GENERATE_REPORT` 和 `VALIDATE_INVOICE` 共用请求 Topic。两端可用 `SERVICE_BRIDGE_REQUEST_TOPIC` 和 `SERVICE_BRIDGE_RESPONSE_TOPIC` 覆盖 Topic，覆盖时须保持一致。BedrockMQ 的 `message-source` 未显式配置，默认使用 `spring.application.name`。

## 1. 启动 MySQL 和 Redis

在项目根目录执行：

```bash
docker compose -f example/docker-compose.yml up -d
```

`docker-compose.yml` 将 MySQL 映射到本机 3306，创建 `service_bridge` 数据库（账号和密码均为 `service_bridge`）；Redis 不设密码。下方启动命令通过环境变量连接这些容器。Client 启动时按 `spring.sql.init` 配置执行 BedrockMQ 和 ServiceBridge 的 MySQL DDL，因此本地首次运行先启动 Client，再启动 Server。生产环境应使用 Flyway、Liquibase 或发布流程管理 Schema。

## 2. 构建并运行

在项目根目录构建三个应用：

```bash
mvn -pl example -am clean package
```

在两个终端分别设置连接参数，然后运行 Client 和 Server：

```bash
export MYSQL_URL='jdbc:mysql://localhost:3306/service_bridge?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
export MYSQL_USERNAME=service_bridge MYSQL_PASSWORD=service_bridge REDIS_PASSWORD=''
java -jar example/example-client/target/service-bridge-example-client-0.1.1.jar
```

```bash
export MYSQL_URL='jdbc:mysql://localhost:3306/service_bridge?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
export MYSQL_USERNAME=service_bridge MYSQL_PASSWORD=service_bridge
java -jar example/example-server/target/service-bridge-example-server-0.1.1.jar
```

Client 默认监听 8888，Server 默认监听 9999。可选的 Monitor 默认监听 8899；它只需连接 Redis：

```bash
REDIS_PASSWORD='' java -jar example/example-monitor/target/service-bridge-example-monitor-0.1.1.jar
```

连接其他服务时，可使用 `SERVER_PORT`、`MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`、`REDIS_HOST`、`REDIS_PORT` 和 `REDIS_PASSWORD` 覆盖配置。多实例运行时，每个 BedrockMQ 实例还应设置唯一的 `BEDROCK_NODE_ID`。

## 3. 发起请求

```bash
curl -X POST http://localhost:8888/reports \
  -H 'Content-Type: application/json' \
  -H 'X-Trace-Id: demo-trace-001' \
  -d '{"reportName":"monthly-sales","requestedBy":"alice"}'
```

成功响应示例：

```json
{
  "requestId": "7a5b9e0d-6f5b-4e50-a6e8-9de3dcaf95d8",
  "code": "SUCCESS",
  "message": null,
  "data": {
    "requestId": "7a5b9e0d-6f5b-4e50-a6e8-9de3dcaf95d8",
    "reportName": "monthly-sales",
    "requestedBy": "alice",
    "generatedAt": "2026-09-16T03:00:00Z",
    "status": "GENERATED",
    "key-bus": "value-bus"
  }
}
```

`reportName` 为空时，Handler 会返回 `INVALID_REPORT_NAME` 业务失败。也可使用请求 ID 查询持久化状态：

```bash
curl http://localhost:8888/reports/{requestId}
```

发票校验使用同一个请求 Topic：

```bash
curl -X POST http://localhost:8888/invoices/validate \
  -H 'Content-Type: application/json' \
  -d '{"invoiceNumber":"INV-001","amount":128.50}'
```

`invoiceNumber` 为空或 `amount` 不大于零时，Handler 会返回 `INVALID_INVOICE`。

Client 和 Server 是独立应用，均使用同一个 MySQL 数据库，并配置相同的请求与响应 Topic。
