# ServiceBridge Example

该模块在同一个 Spring Boot 应用中同时启用 Client Starter 和 Server Starter，演示两个业务共用一个请求 Topic：

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

`application.yml` 中的 `service.bridge.request-topic` 让 `GENERATE_REPORT` 和 `VALIDATE_INVOICE` 共用请求 Topic，可用 `SERVICE_BRIDGE_REQUEST_TOPIC` 覆盖；响应 Topic 可用 `SERVICE_BRIDGE_RESPONSE_TOPIC` 覆盖。BedrockMQ 的 `message-source` 未显式配置，默认使用 `spring.application.name`。

## 1. 启动 MySQL 和 Redis

在项目根目录执行：

```bash
docker compose -f example/docker-compose.yml up -d
```

`application.yml` 默认连接 `localhost:3306/service_bridge` 和 `localhost:6379`。示例启动时会自动执行 BedrockMQ 和 ServiceBridge 的 MySQL DDL；此配置仅用于本地演示，生产环境应使用 Flyway、Liquibase 或发布流程管理 Schema。

## 2. 构建并运行

```bash
mvn -pl example -am clean package
java -jar example/target/service-bridge-example-0.1.0-SNAPSHOT.jar
```

如果默认端口或账号不适用，可通过 `SERVER_PORT`、`MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`、`REDIS_HOST`、`REDIS_PORT` 和 `REDIS_PASSWORD` 覆盖。多实例运行时，每个实例还应设置唯一的 `BEDROCK_NODE_ID`。

## 3. 发起请求

```bash
curl -X POST http://localhost:8080/reports \
  -H 'Content-Type: application/json' \
  -H 'X-Trace-Id: demo-trace-001' \
  -d '{"reportName":"monthly-sales","requestedBy":"alice"}'
```

成功响应示例：

```json
{
  "requestId": "7a5b9e0d-6f5b-4e50-a6e8-9de3dcaf95d8",
  "code": "SUCCESS",
  "message": "Success",
  "data": {
    "requestId": "7a5b9e0d-6f5b-4e50-a6e8-9de3dcaf95d8",
    "reportName": "monthly-sales",
    "requestedBy": "alice",
    "generatedAt": "2026-09-16T03:00:00Z",
    "status": "GENERATED"
  }
}
```

`reportName` 为空时，Handler 会返回 `INVALID_REPORT_NAME` 业务失败。也可使用请求 ID 查询持久化状态：

```bash
curl http://localhost:8080/reports/{requestId}
```

发票校验使用同一个请求 Topic：

```bash
curl -X POST http://localhost:8080/invoices/validate \
  -H 'Content-Type: application/json' \
  -d '{"invoiceNumber":"INV-001","amount":128.50}'
```

`invoiceNumber` 为空或 `amount` 不大于零时，Handler 会返回 `INVALID_INVOICE`。

示例为了便于本地运行才将 Client 和 Server 合并在同一应用中。真实部署可分别引入 `bridge-client-starter` 和 `bridge-server-starter`，但必须共用同一个 MySQL 数据库。
