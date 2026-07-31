# backend

后端代码目录。

## 用途
放 Java Spring Boot 项目的全部代码，包括控制器、服务层、数据访问层等。

## 后续会有的内容
- `src/` — Java 源代码
- `pom.xml` — 项目依赖配置（Maven）
- `src/main/resources/` — 配置文件和数据库脚本

## 相关文档
- [开发规范文档](../docs/开发规范文档.md)
- [数据库部署说明](../docs/数据库部署说明.md)

## 数据库运行方式

- 默认使用本机 H2 文件数据库，无需安装数据库软件。
- 部署到共享环境时，设置 `SPRING_PROFILES_ACTIVE=mysql`，并提供 `UAV_DB_URL`、`UAV_DB_USERNAME`、`UAV_DB_PASSWORD` 三个环境变量。
- 服务启动时会自动执行 `src/main/resources/db/migration/` 下的建表脚本；不要手动建表或把账号密码写入 Git。

## 实时数据格式

- `GET /api/v1/dashboard/status` 与 `/ws/drone-status` 使用统一的实时快照格式，当前版本为 `1.0`。
- 快照会明确返回数据来源、更新时间、任务、设备、告警和返航指令状态；当前来源为本机模拟器。
- 字段说明和后续真实 DJI Cloud API 的转换规则见 [`../docs/统一实时数据格式.md`](../docs/统一实时数据格式.md)。

## DJI Cloud API 接入边界

- 默认 `UAV_DJI_CLOUD_ENABLED=false`，后端不会主动连接真实设备，现有模拟链路继续运行。
- 真实地址、客户端编号和密钥只能由服务器环境变量注入；`.env.example` 只保留变量名和安全默认值。
- `GET /api/v1/dashboard/integration/dji/readiness` 只返回“是否启用、是否配置完整、超时和重试次数”，不会返回地址、客户端编号或密钥。
- 只读 HTTP 客户端统一设置连接超时、读取超时和失败重试；日志只记录固定操作类型、次数和异常类别，不记录 URL、令牌、账号或异常正文。
- 真实环境只允许 HTTPS，本机接口模拟可使用 `localhost`；最大重试次数为 5。正式认证头或签名方式必须在确认 DJI 官方协议后再实现。
