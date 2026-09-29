# 数据库和状态机

## 表归属

`service_bridge_request` 是 Client 和 Server 共同访问的业务桥接表：

| 字段组 | 写入方 |
|---|---|
| request、business_type、request_payload、client_deadline | Client |
| wait_status、responded_at | Client |
| process_status、result/error、processing token/deadline | Server |

## 等待状态

```text
WAITING → RESPONDED
WAITING → TIMED_OUT
```

## 处理状态

```text
PENDING → PROCESSING → SUCCESS
                   └→ FAILED
```

HTTP 超时不修改 `process_status`，Server 完成结果时不以 `wait_status` 限制结果落库；只有发送 RESPONSE 前才检查等待状态和截止时间。

## 超时边界

Client 超时 SQL 只允许未完成任务进入 TIMED_OUT：

```sql
UPDATE service_bridge_request
SET wait_status = 'TIMED_OUT'
WHERE request_id = :requestId
  AND wait_status = 'WAITING'
  AND process_status IN ('PENDING', 'PROCESSING');
```

如果 Server 已经先提交 SUCCESS/FAILED，更新影响零行，Client 重新查询并返回结果。如果 Client 先更新 TIMED_OUT，Server 之后仍可保存迟到结果。

## Schema 管理

ServiceBridge 仅支持 MySQL 5.7 及以上版本。Starter 默认不自动执行 DDL，防止生产环境隐式变更数据库。请通过 Flyway、Liquibase 或现有发布流程执行 `bridge-protocol/src/main/resources/db/service-bridge-schema-mysql.sql`。

BedrockMQ 自身的三张表也必须提前创建。两个组件必须指向同一 DataSource。
