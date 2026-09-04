<div align="center">
  <img src="./ruoyi-ui-vue3/src/assets/logo/logo.png" alt="VLStream-Cloud-Lite" width="160">

  <h1>VLStream-Cloud-Lite</h1>

  <p><strong>基于 RuoYi-Vue、WVP 与 ZLMediaKit 的开源视频接入与流媒体管理平台</strong></p>

  <p>
    <a href="https://github.com/OortCloudGroup/VLStream-Cloud-Lite"><img src="https://img.shields.io/badge/GitHub-OortCloudGroup%2FVLStream--Cloud--Lite-181717?style=flat-square&logo=github" alt="GitHub"></a>
    <a href="https://gitcode.com/qq_74020751/VLStream-Cloud-Lite"><img src="https://img.shields.io/badge/GitCode-qq__74020751%2FVLStream--Cloud--Lite-C71D23?style=flat-square" alt="GitCode"></a>
    <a href="https://gitee.com/lcqssaa/VLStream-Cloud-Lite"><img src="https://img.shields.io/badge/Gitee-lcqssaa%2FVLStream--Cloud--Lite-C71D23?style=flat-square&logo=gitee" alt="Gitee"></a>
    <a href="./LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square" alt="MIT License"></a>
    <img src="https://img.shields.io/badge/Java-8-orange.svg?style=flat-square" alt="Java 8">
    <img src="https://img.shields.io/badge/Spring%20Boot-2.7.18-6DB33F.svg?style=flat-square" alt="Spring Boot 2.7.18">
    <img src="https://img.shields.io/badge/Vue-3.4.31-42B883.svg?style=flat-square" alt="Vue 3.4.31">
  </p>

  <p>
    <a href="#-快速开始">快速开始</a> •
    <a href="#-核心能力">核心能力</a> •
    <a href="#-架构与项目结构">架构</a> •
    <a href="#-技术栈">技术栈</a> •
    <a href="#-部署与升级">部署</a> •
    <a href="#-项目文档">文档</a>
  </p>
</div>

---

> [!IMPORTANT]
> [VLStream-Cloud-Lite 在线访问入口](https://vls-lite.oortcloudsmart.com:2443/#/login?redirect=/index)
 默认账号： admin / 123456
## 📖 项目介绍

VLStream-Cloud-Lite 是一个面向视频设备接入、流媒体分发和设备运维的开源平台。项目代码和模块名中仍保留 `RuoYi`/`ruoyi-*` 技术标识；后端由 Java 8 + Spring Boot 多模块工程组成，管理端使用 Vue 3 + Element Plus，媒体处理依托 WVP 与 ZLMediaKit。

平台将国标设备、网络摄像机、RTSP 推流设备、VLStream 原生 MQTT 设备及可选的厂商 SDK 设备统一纳入管理，提供设备状态、通道、预览、回放、云台、录像计划、告警、级联和系统权限等能力。

> [!WARNING]
> 请仅接入已获得合法授权的设备和视频流，并根据现场网络、隐私、安全和数据保护要求配置访问控制、密钥、对象存储及防火墙。

## 🌐 在线访问


## ✨ 核心能力

| 能力 | 当前实现 |
| --- | --- |
| 国标视频接入 | 基于 WVP 的 GB/T 28181 SIP/RTP 注册、设备/通道查询、点播、回放、云台、录像计划、告警和级联能力 |
| 多协议设备管理 | 集成 ONVIF、RTSP、海康 ISUP、大华 SDK 等设备模块，并在管理端提供对应页面 |
| VLStream 原生设备 | `ruoyi-vlstream` 通过 MQTT 2.2 接收设备状态与视频源快照，保存独立设备数据，返回业务回执并管理在线状态 |
| 统一媒体服务 | 通过 ZLMediaKit 完成媒体节点管理、RTSP/RTMP 拉流代理和浏览器播放地址生成，支持 WebRTC 等播放链路 |
| 设备分类 | 对多协议设备提供区域、分组、标签的统一分类与批量分配 |
| 录像与运维 | 云端录像、录像计划、回放、设备收藏/标记、截图、流代理和服务状态查看 |
| 管理后台 | RuoYi 用户、角色、菜单、数据权限、日志、定时任务、缓存、服务监控、代码生成和 Swagger/Knife4j 接口文档 |
| 可视化大屏 | 独立的 `ruoyi-ui-large` Vue 3 + TypeScript + ECharts 大屏前端 |
| 身份认证 | 支持本地账号密码模式，也保留 WVP 与外部 SSO/VLStream 校验服务的联邦认证入口 |
| 固件与 OTA | VLStream 固件仓库、上传、下载地址、设备升级任务和 MQTT 回执处理 |

### 原生 SDK 与扩展能力边界

代码仓库包含 `ruoyi-isup`、`ruoyi-dahua`、GAT1400、萤石和乐橙等扩展入口，但它们不等同于默认 Docker 发布包中的可直接生产能力：

- 默认发布链路重点验证 GB28181、ONVIF、RTSP、ZLMediaKit、EMQX 与 VLStream 基础接入。
- `DAHUA_ENABLED` 默认值为 `false`；ISUP Linux 原生配置 `isup-linux64.enabled` 默认关闭。
- ISUP/大华功能依赖对应的原生动态库、设备环境、网络端口和第三方 SDK 授权，启用前必须在目标操作系统上单独联调。

## 🏗️ 架构与项目结构

### 运行架构

```text
浏览器 / ruoyi-ui-vue3
          │ HTTP API、WebSocket、WebRTC 播放
          ▼
ruoyi-admin（Spring Boot 入口）
   ├── MySQL：业务、设备、权限和迁移记录
   ├── Redis：缓存、会话和平台运行数据
   ├── ZLMediaKit：媒体接入、转发、拉流代理和播放
   ├── EMQX 5.4：VLStream MQTT Broker（按需启用）
   └── ISUP / 大华原生 SDK（可选，需单独验证）
```

### 后端模块

```text
VLStream-wvp-lite
├── ruoyi-admin          # Spring Boot Web 入口和应用配置
├── ruoyi-common         # 公共模型、工具、响应和基础能力
├── ruoyi-framework      # 安全、数据源、AOP、监控和 Web 基础设施
├── ruoyi-system         # 用户、权限、系统管理、设备分类和通用业务
├── ruoyi-wvp            # WVP/GB28181、SIP、RTP、媒体节点、预览、回放和云台
├── ruoyi-vlstream       # VLStream MQTT 设备、视频源、固件仓库和 OTA
├── ruoyi-isup           # 海康 ISUP/CMS/SS/SMS/Alarm/Stream 集成
├── ruoyi-onvif          # ONVIF 设备发现与管理
├── ruoyi-rtsp           # RTSP 设备、推流和媒体处理
├── ruoyi-dahua          # 大华 SDK、设备控制、抓图和告警扩展
├── ruoyi-quartz         # 定时任务
├── ruoyi-generator      # Velocity 代码生成器
├── ruoyi-ui-vue3        # 主管理前端：Vue 3 + Element Plus + Vite
├── ruoyi-ui-large       # 独立数据大屏：Vue 3 + TypeScript + ECharts
├── sql                  # 基础快照、功能扩展和历史迁移脚本
├── deploy/release       # Release 包中的初始化 SQL、ZLMediaKit 镜像文件
├── compose.yaml         # 自带 MySQL、Redis、MQTT、ZLMediaKit 的完整部署
├── compose.external.yaml # 使用已有基础设施的后端部署
├── Dockerfile           # 主前端构建 + Java 镜像构建
├── DEPLOYMENT.md        # 部署、升级、端口和故障排查
└── RELEASE_NOTES.md     # 当前发布说明
```

`ruoyi-goview/` 目前仅保留一个历史模块 POM，未加入根 `pom.xml` 的 Maven reactor，也不在默认 Docker 构建链路中；不要把它当作当前默认构建模块。

### 关键数据流

```text
GB28181 / ONVIF / RTSP / VLStream 设备
        ├── 信令、设备状态或视频源进入 WVP
        └── WVP 调用 ZLMediaKit 创建/管理媒体流
                         └── 浏览器通过 WebRTC、HTTP-FLV、HLS 等链路播放

VLStream 设备 --MQTT QoS 1--> EMQX --> ruoyi-vlstream
                                  ├── 校验 Topic、协议版本和消息幂等性
                                  ├── 保存设备、遥测、视频源和固件任务
                                  └── 在原 Topic 发布 platform2dev 业务回执
```

## 🧰 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端运行时 | Java 8、Spring Boot 2.7.18、Spring Security 5.7.12、Maven |
| 数据访问 | MyBatis、MyBatis-Plus 3.5.2、PageHelper、Druid |
| 安全与接口 | JWT、Springfox Swagger 3、Knife4j 3.0.3、验证码、数据权限 |
| 流媒体 | WVP-GB28181、Jain SIP、ZLMediaKit、RTP/WebRTC 相关播放链路 |
| 设备协议 | GB/T 28181、ONVIF、RTSP、VLStream MQTT 2.2、海康 ISUP、大华 SDK |
| 主管理前端 | Vue 3.4.31、Vite 5.3.2、Element Plus 2.7.6、Pinia 2.1.7、ECharts 5.5.x |
| 大屏前端 | Vue 3、TypeScript、Vite、TailwindCSS、ECharts |
| 基础设施 | MySQL 8、Redis、EMQX 5.4、Docker Compose v2 |
| 构建镜像 | Node.js 20（前端）、Maven 3.9.9 + Java 8（后端）、Eclipse Temurin 8 JRE |

## 🚀 快速开始

### 环境要求

完整 Docker 部署建议准备：

- Docker Engine 与 Docker Compose v2。
- 能够运行 Linux/amd64 镜像的主机；生产环境需按实际网络开放 HTTP、SIP UDP 和 RTP UDP 端口。
- MySQL、Redis、EMQX 和 ZLMediaKit 的持久化磁盘空间。

源码开发还需要：

- JDK 8。根 `pom.xml` 的 `<java.version>` 当前为 `1.8`。
- Maven，以及 Node.js/npm（仅在单独运行主前端或大屏前端时需要）。
- 源码启动时可访问 MySQL、Redis、ZLMediaKit；启用 VLStream 时还需要可访问 MQTT Broker。

### 1. 使用 Docker 一键启动

在仓库根目录执行：

```powershell
Copy-Item .env.example .env
```

编辑 `.env`，至少替换所有 `change-me-*` 占位值，并根据部署网络修改 `ZLM_PUBLIC_HOST`、数据库/Redis 地址和 MQTT 认证信息。不要把真实 `.env` 提交到 Git。

启动完整依赖栈：

```powershell
docker compose up -d
docker compose ps
```

`compose.yaml` 默认启动以下服务：

| 服务 | Compose 服务名 | 默认对外端口 |
| --- | --- | --- |
| WVP HTTP API 与管理端 | `wvp-backend` | `8080/tcp` |
| MySQL | `mysql` | `3306/tcp` |
| Redis | `redis` | `6379/tcp` |
| EMQX MQTT | `mqtt` | `1883/tcp` |
| ZLMediaKit HTTP/WebSocket | `zlmediakit` | `8081/tcp` |
| ZLMediaKit HTTPS | `zlmediakit` | `8443/tcp` |
| GB28181 SIP | `wvp-backend` | `8116/udp` |
| RTP 媒体端口范围 | `zlmediakit` | `40000-40300/udp` |

启动后访问 `http://localhost:8080`。新建 MySQL 数据卷时会自动执行发布包中的初始化 SQL；已有数据卷不会重复初始化。查看日志：

```powershell
docker compose logs -f wvp-backend
docker compose logs -f zlmediakit
```

### 2. 使用外部基础设施

如果 MySQL、Redis、MQTT Broker 和 ZLMediaKit 已经由其他平台维护，先在 `.env` 中填写外部服务地址，再执行：

```powershell
docker compose -f compose.external.yaml up -d
```

外部模式要求显式配置 `DB_HOST`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PASSWORD`、`VLSTREAM_MQTT_HOST`、`ZLM_HOST`、`ZLM_HOOK_IP` 和 `ZLM_PUBLIC_HOST` 等变量。ZLMediaKit 必须能够访问 `ZLM_HOOK_IP` 指向的 WVP 地址。

### 3. 从源码启动后端

在确认 JDK 8 已生效后，从仓库根目录执行：

```powershell
mvn -ntp -pl ruoyi-admin -am test
mvn -ntp -pl ruoyi-admin -am package -DskipTests
mvn -ntp -pl ruoyi-admin spring-boot:run
```

源码配置位于 [`ruoyi-admin/src/main/resources/application.yml`](./ruoyi-admin/src/main/resources/application.yml) 和 [`ruoyi-admin/src/main/resources/application-druid.yml`](./ruoyi-admin/src/main/resources/application-druid.yml)。源码配置的 HTTP 默认端口是 `9080`；若使用主前端的默认开发代理，应保持后端监听 `9080`，或同步调整 `ruoyi-ui-vue3/vite.config.js`。

打包后的启动方式：

```powershell
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

启动后可按需访问：

- 管理端：`http://localhost:9080`（如果覆盖 `SERVER_PORT`，以实际端口为准）。
- Knife4j：`http://localhost:9080/doc.html`。
- OpenAPI JSON：`http://localhost:9080/v3/api-docs`。

### 4. 单独启动前端

主管理端：

```powershell
Set-Location ruoyi-ui-vue3
npm install
npm run dev
```

当前 Vite 配置默认使用 `http://localhost`（端口 `80`），并把 `/dev-api` 代理到 `http://localhost:9080`。如果本机端口 `80` 已被占用，请调整 `ruoyi-ui-vue3/vite.config.js` 的开发端口；生产构建使用：

```powershell
npm run build:prod
```

数据大屏是独立前端：

```powershell
Set-Location ruoyi-ui-large
npm install
npm run dev
npm run build
```

主 Dockerfile 只构建并打包 `ruoyi-ui-vue3`，`ruoyi-ui-large` 需要单独部署或由现场网关托管。

## 🔌 VLStream 原生设备接入

`ruoyi-vlstream` 的详细协议、数据库、权限和联调说明见[模块 README](./ruoyi-vlstream/README.md)。部署时重点注意：

- `VLSTREAM_MQTT_ENABLED` 默认是 `false`；作为 VLStream 设备中心时必须显式设置为 `true`。
- 设备总线 Topic 为 `vlstream/v2.2/dev/{deviceId}/bus`，当前协议使用 MQTT QoS 1。
- WVP 的 `VLSTREAM_WVP_MQTT_CLIENT_ID` 必须与 VLS 等其他消费者不同，否则 Broker 会互相踢下线。
- WVP 保存设备状态、视频源全量快照、幂等消息、固件仓库和 OTA 任务；设备预览时按需调用 ZLMediaKit 创建拉流代理。
- 固件上传/下载依赖对象存储配置；生产环境不要把 MinIO Access Key、Secret Key 或签名密钥写入 README、YAML 或 Git。

## 🔌 API 示例

后端控制器路径如下，前端开发环境通常会在前面增加 `/dev-api` 并由 Vite 代理去掉该前缀：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/device/query/deviceList` | 查询国标设备/通道 |
| `GET` | `/api/play/start/{deviceId}/{channelId}` | 发起国标点播 |
| `GET` | `/api/proxy/list` | 查询拉流代理 |
| `GET` | `/vlstream/device/list` | 查询 VLStream 设备 |
| `POST` | `/vlstream/device/{deviceRowId}/preview` | 创建 VLStream 设备预览 |
| `GET` | `/device/classification/tree` | 查询统一设备分类树 |

完整接口列表以及请求模型请以启动后的 Knife4j/Swagger 文档为准。

## 🗃️ 数据库与迁移

- Docker 新部署使用 [`deploy/release/sql/init/10-ry-wvp.sql`](./deploy/release/sql/init/10-ry-wvp.sql) 初始化空 MySQL 数据卷。
- 已有数据库的版本升级由 Flyway 管理，迁移文件位于 [`ruoyi-admin/src/main/resources/db/migration/`](./ruoyi-admin/src/main/resources/db/migration/)。已发布迁移文件不得改名、删除或修改。
- `sql/ry-wvp.sql` 是仓库中的基础数据库快照；`sql/2026-08-13-device-classification.sql`、`sql/2026-08-13-vlstream.sql` 和 `sql/2026-08-17-custom-protocol.sql` 是对应功能的源码/手工初始化脚本。
- 生产升级前先备份 MySQL，并阅读 [`DEPLOYMENT.md`](./DEPLOYMENT.md)；不要通过删除 Docker 数据卷来完成升级。

## 🐳 部署与升级

### 发布包

仓库的 GitHub Actions 在推送 `v*` 标签时执行后端测试、构建 `linux/amd64` 镜像，并生成带 SHA-256 校验文件的 ZIP 发布包。发布包包含 Compose 文件、环境模板、初始化 SQL、升级说明和 ZLMediaKit 构建文件。

默认镜像地址为：

```text
ghcr.io/oortcloudgroup/vlstream-cloud-lite:<release-version>
```

生产环境建议固定到明确的 Release 版本，并在升级前完成数据库备份、端口核对和回滚准备。不要把 `docker compose down -v` 当作普通升级命令，它会删除持久化卷中的数据。

### 常用运维命令

```powershell
docker compose ps
docker compose logs -f wvp-backend
docker compose stop
docker compose up -d
```

端口、外部基础设施、数据卷、ZLMediaKit Hook、原生 SDK 和故障排查请以[部署说明](./DEPLOYMENT.md)为准。

## 📚 项目文档

| 文档 | 说明 |
| --- | --- |
| [`DEPLOYMENT.md`](./DEPLOYMENT.md) | Docker、外部基础设施、端口、数据卷、升级和故障排查 |
| [`ruoyi-vlstream/README.md`](./ruoyi-vlstream/README.md) | VLStream MQTT 2.2、设备状态、视频源、固件和 OTA |
| [`ruoyi-ui-vue3/README.md`](./ruoyi-ui-vue3/README.md) | 主管理前端说明 |
| [`ruoyi-ui-large/README.md`](./ruoyi-ui-large/README.md) | 数据大屏前端说明 |
| [`deploy/release/sql/upgrade/README.md`](./deploy/release/sql/upgrade/README.md) | Release 包升级目录说明 |
| [`RELEASE_NOTES.md`](./RELEASE_NOTES.md) | 当前发布内容和默认部署边界 |
| [`sql/`](./sql/) | 数据库快照、功能 SQL 和历史迁移脚本 |
| API 文档 | 启动后访问 `doc.html`；机器接口可访问 `v3/api-docs` |

## 🤝 帮助与支持

- [GitHub 仓库](https://github.com/OortCloudGroup/VLStream-Cloud-Lite)
- [GitHub Issues](https://github.com/OortCloudGroup/VLStream-Cloud-Lite/issues)

提交问题时请同时说明使用的 Release/commit、部署方式（源码或 Docker）、操作系统、相关端口、脱敏后的配置和关键日志。请勿提交真实密码、Token、设备凭据、完整 RTSP URL 或生产环境数据库导出文件。

## 📄 开源许可

本项目自有代码基于 [MIT License](./LICENSE) 发布。仓库中使用的 RuoYi、WVP、ZLMediaKit、Spring 生态、厂商 SDK 及其他第三方组件分别受其各自许可证、授权条款和再分发限制约束，商用部署前请逐项确认。

## 🙏 特别致谢

- [ZLMediaKit](https://github.com/ZLMediaKit/ZLMediaKit) — 高性能流媒体服务框架。
- [WVP-GB28181-Pro](https://github.com/648540858/wvp-GB28181-pro) — GB/T 28181 服务框架。
- [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue) — 快速开发平台。
- [GoView](https://gitee.com/dromara/go-view) — 大屏可视化基础项目。

<div align="center">
  <p>Built with ❤️ by OortCloud</p>
</div>
