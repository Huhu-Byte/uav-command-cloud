# 部署说明

本目录包含无人机巡检系统的全部部署配置。

## 目录结构

```
deploy/
├── .env.example          统一环境变量模板（复制为 .env 后修改）
├── compose.yaml          Docker Compose 配置（MySQL + 后端 + Nginx）
├── README.md             本文件
├── nginx/                （前端 Nginx 配置在 frontend/deploy/nginx/ 下，供 Dockerfile 引用）
└── scripts/              一键操作脚本
    ├── build-all.cmd       构建前后端产物（Docker 部署前必须先执行）
    ├── start-docker.cmd    Docker 一键启动
    ├── stop-docker.cmd     Docker 停止（加 clean 参数删除数据）
    ├── logs-docker.cmd     查看 Docker 日志
    ├── start-local.cmd     本机直接启动后端（用 Java JAR）
    └── start-dev.cmd       开发模式启动（后端 Maven + 前端 Vite 热更新）
```

## 方式一：Docker 部署（推荐正式环境）

适合服务器部署或完整环境验证。启动后包含 MySQL、后端和 Nginx 前端三个容器。

### 步骤

1. **安装 Docker Desktop** 并启动

2. **构建前后端产物**
   ```
   deploy\scripts\build-all.cmd
   ```

3. **配置环境变量**
   - 复制 `deploy\.env.example` 为 `deploy\.env`
   - 修改 `MYSQL_PASSWORD` 和 `MYSQL_ROOT_PASSWORD` 为两条不同的长密码
   - 按需开启 DJI MQTT 相关配置

4. **启动容器**
   ```
   deploy\scripts\start-docker.cmd
   ```

5. **访问**
   - 网页：`http://127.0.0.1:8088`
   - 后端 API：`http://127.0.0.1:8088/api/v1/dashboard/status`

6. **查看日志**
   ```
   deploy\scripts\logs-docker.cmd           查看所有日志
   deploy\scripts\logs-docker.cmd backend   只看后端日志
   ```

7. **停止**
   ```
   deploy\scripts\stop-docker.cmd           停止，保留数据
   deploy\scripts\stop-docker.cmd clean     停止并删除所有数据
   ```

## 方式二：本机 JAR 启动（快速调试）

适合后端调试，使用 H2 文件数据库，不需要 Docker。

### 步骤

1. **安装 Java 17** 和 **Maven**

2. **构建后端 JAR**
   ```
   cd backend
   mvn package -DskipTests
   ```

3. **配置环境变量**
   - 复制 `deploy\.env.example` 为 `deploy\.env`
   - 按需开启 MQTT 和 DJI 配置

4. **启动**
   ```
   deploy\scripts\start-local.cmd
   ```

5. **访问**
   - 后端 API：`http://127.0.0.1:8080/api/v1/dashboard/status`
   - 数据库：H2 文件，位于 `%USERPROFILE%\.uav-command\uav-command`

## 方式三：开发模式（前后端联调）

适合前端开发，支持热更新。

### 步骤

1. **安装 Java 17、Maven、Node.js**

2. **安装前端依赖**（首次）
   ```
   cd frontend
   npm install
   ```

3. **启动**
   ```
   deploy\scripts\start-dev.cmd
   ```

4. **访问**
   - 前端：`http://127.0.0.1:5173`（Vite 热更新）
   - 后端：`http://127.0.0.1:8080`

## DJI 机场上云配置

真实设备接入需要配置以下环境变量（在 `deploy\.env` 中修改）：

| 变量 | 说明 | 示例 |
|------|------|------|
| `UAV_DJI_MQTT_ENABLED` | 开启 MQTT 连接 | `true` |
| `UAV_DJI_MQTT_BROKER_URL` | Broker 地址 | `tcp://本机IP:1883` |
| `UAV_DJI_MQTT_GATEWAY_SN` | 机场网关 SN | `8UUXN2B00AO0ST` |
| `UAV_DJI_MQTT_HANDSHAKE_ENABLED` | 开启握手应答 | `true` |
| `UAV_DJI_MQTT_EMBEDDED_BROKER` | 本机内嵌 Mosquitto | `true`（仅本机调试） |
| `UAV_DJI_CLOUD_CLIENT_ID` | DJI App ID | `191594` |
| `UAV_DJI_DEVICE_BINDING_CODE` | 设备绑定码 | 从 DJI 控制台获取 |

**安全边界：**
- `.env` 文件不提交到 Git
- 凭证只通过环境变量注入，不写入代码
- 未完成设备验收前不发送真实控制指令
- Docker 部署默认关闭演示身份控制

## 容器架构

```
浏览器 ──→ Nginx (80) ──→ 后端 (8080) ──→ MySQL (3306)
              │                 │
              │                 ├── /api/  转发
              │                 └── /ws/   WebSocket 转发
              │
              └── 静态网页 (Vue 3 dist)
```

- MySQL 不对外开放，只在 Docker 内部网络通信
- 浏览器只访问 Nginx，由 Nginx 转发 API 和 WebSocket 请求
- 后端容器通过 `SPRING_PROFILES_ACTIVE=mysql` 连接 MySQL
