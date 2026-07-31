# 本机容器化运行

本目录提供一套本机或服务器都可使用的启动配置：浏览器只访问 Nginx，Nginx 再转发请求到后端；MySQL 不对外开放，只供后端保存任务、成果和操作记录。

## 启动前准备

1. 安装并启动 Docker Desktop。
2. 先在本机完成构建：在 `backend` 目录运行 `mvn package -DskipTests`，在 `frontend` 目录运行 `npm run build`。这会生成 Docker 要打包的后端 JAR 和前端网页文件。
3. 复制 `.env.example` 为 `.env`。
4. 在 `.env` 中把 `MYSQL_PASSWORD` 和 `MYSQL_ROOT_PASSWORD` 换成两条不同的长密码；该文件不会提交到 Git。

## 启动与访问

在项目根目录执行：

```powershell
docker compose --env-file deploy/.env -f deploy/compose.yaml up --build -d
```

浏览器访问 `http://127.0.0.1:8088`。首次启动会下载基础镜像、打包已构建的前后端产物并创建数据库，耗时会比后续启动长。

## 查看与停止

```powershell
docker compose --env-file deploy/.env -f deploy/compose.yaml logs -f
docker compose --env-file deploy/.env -f deploy/compose.yaml down
```

`down` 不会删除 MySQL 数据。只有明确执行 `down --volumes` 才会删掉本机数据库数据。

## 安全边界

- 此配置默认关闭 DJI Cloud API，未填入任何真实凭证。
- 容器环境默认关闭演示身份控制；接入真实登录前不要对外公开访问。
- 真实设备只读验证前，仍需获得测试设备和官方认证信息。
