# EHome 旧版协议接入

## 范围与版本

新增入口为「视频汇聚 → 设备管理 → EHome协议」，路由 `/#/ehome/device`。
现有 ISUP 入口继续管理 5.0。本模块使用独立的 `ehome_device` 表、`EHOME` 分类标识和 `ehome:device:*` 权限。当前 SDK 运行时创建第二个 CMS 监听返回错误码 46，因此复用现有底层监听，按设备上报版本分发到独立业务流程。
不迁移或修改现有 `isup_device` 记录，不把名称相近视为协议兼容。

设备主动注册后自动建档，支持查询、区域/分组/标签、编辑名称与备注、在线/离线、通道查询和实时预览。
录像回放、云台和告警不属于本次范围。

| 设备上报版本 | 实现的取流流程 | 验证边界 |
| --- | --- | --- |
| 2.x（含 2.6） | StartGetRealStreamV11 后接收流，不发送 StartPushRealStream | 原生回调模拟和版本分支测试；待真机验证 |
| 3.x | 按官方“4.0 以下”分支处理，版本单独显示 | 未找到具体型号兼容证明，必须真机联调 |
| 4.x | StartGetRealStreamV11 → StartPushRealStream | 原生回调模拟和版本分支测试；待真机验证 |
| 5.x / 未识别 | 此监听拒绝接入 | 5.x 使用现有 ISUP 入口 |

## 开源选型及来源

核对的项目：

- [ruoyi-qs-nvr](https://github.com/2929004360/ruoyi-qs-nvr)，MIT，参考提交 `292e54f359eecbda4faa3c7c137d37985dbacc1c`。与当前 RuoYi / Java 技术栈接近，提供注册、通道查询、媒体转发等实现。移植适配了 `NET_EHOME_CONFIG` / `NET_EHOME_DEVICE_INFO` 查询与注册响应的调用方式，保留其许可证于 `docs/licenses/ruoyi-qs-nvr-MIT.txt`。
- [ip-camera-ehome-server](https://github.com/corenel/ip-camera-ehome-server)，Apache-2.0，C++ / Python EHome 4.0 示例；README 仍列有多设备验证等待办。
- FreeEhome 被 ZLMediaKit README 列为合作项目，但本次其 GitHub 页面返回 451，无法完成代码与许可证核验，因此未采用。

实际方案在已有 `ruoyi-isup` 模块中新增独立 EHome 适配层；复用已初始化的海康 CMS / Stream 原生库及其底层监听，以及项目已有 JavaCV、ZLMediaKit、Jessibuca，不运行参考项目的微服务或安装第二套媒体服务。海康原生 SDK 是厂商库，并非开源协议实现。复用库不代表按 ISUP 5.0 的流程处理旧协议。

海康官方接口说明（2026-09-16 核对）：

- [StartPushRealStream](https://open.hikvision.com/hardware/v2/接口定义/GUID-67A457B4-13BE-41E4-9A57-E5253108AD62.html)：4.0 以下不需要调用，4.0 及以上必须调用。
- [DeviceInfo](https://open.hikvision.com/hardware/v2/接口定义/GUID-EF678AC7-BB36-40F1-AD44-1AA5896FEC6F.html)：`dwChannelAmount` 为模拟与数字通道总数，`dwStartChannel` 为起始通道。
- [预览流程](https://open.hikvision.com/hardware/v2/接口定义/GUID-000B0DFC-BBB0-405C-96F5-30D9D9BD4F6B.html)：CMS 发信令、Stream SDK 接收 PS 流、媒体服务转封装供浏览器播放。

## 配置

以下为默认值，可通过外部配置或 Spring 环境变量覆盖。底层监听地址和端口沿用现有 `isup.IP`、`isup.cmsServer.Port`、`isup.smsServer.Port`，页面从后端读取真实值。

```yaml
ehome:
  enabled: true
  linux-enabled: false # Linux 基础接入显式设为 true；Windows 沿用应用初始化
  public-host: "" # 默认按通往设备的路由自动选择本机 IPv4；公网/NAT 时填写设备可达地址
  default-dept-id: 100
  media-public-host: 127.0.0.1 # 浏览器能访问的 ZLMediaKit 地址
  media-rtmp-port: 1935
  media-http-port: 8090 # 浏览器可访问的 ZLM HTTP 端口，默认继承 media.http-port
  media-http-scheme: http
  # media-push-sign: 外部 ZLM 的有效推流签名；默认沿用当前项目基于 media.secret 的摘要
```

`isup.IP` 默认 `0.0.0.0`，只控制服务监听，不作为发给设备的连接地址。设备填写可达的服务器局域网地址和 `isup.cmsServer.Port`。
`ehome.public-host` 默认为空：取流时根据设备上报的 IP，查询操作系统路由选择实际本机 IPv4，UDP connect 不发送数据。多网卡设备按各自路由选择。公网/NAT 或容器桥接时显式配置 `ehome.public-host`，它优先于自动选择。通配、回环及无法确定的地址会报错，不会发给设备。
注册端口按部署需要开放 TCP/UDP，取流使用 TCP。NAT 环境须映射同号端口。
媒体 API 地址、HTTP 端口、密钥继续使用现有 `media.*` 配置。

Windows x86_64 与 Linux x86_64 现在使用相同的 CMS/Stream 初始化、协议分发和预览回调。Linux 通过 `ehome.linux-enabled=true` 启用，优先于历史 `isup-linux64.enabled` 分支，避免重复 CMS 监听；历史报警/存储分支的默认值保持不变。

仓库中的 Windows 与 Linux CMS/Stream 原生库均已实测报告 SDK **2.5.1.35**。SDK 版本不是设备协议版本，不是每个协议版本各放一套库。库已在仓库中，本次没有下载或替换厂商二进制；补齐的是加载方式、Linux 运行路径和镜像携带。

加载器固定使用当前包配套的 OpenSSL 库；Linux 通过 `RTLD_NOW | RTLD_DEEPBIND` 加载，减少和 FFmpeg、其他 SDK 的符号冲突。Windows 与两个 Linux 环境都测试了先加载 FFmpeg 再初始化海康库，未复现崩溃。此结果不覆盖所有外部库组合。

SDK 目录默认是项目根下 `ruoyi-isup/win-lib` 或 `ruoyi-isup/linux-lib`；可设置 `EHOME_SDK_PATH` 或 JVM 参数 `-Dehome.sdk.path=...` 指向完整配套 SDK 目录。不要只替换单个 OpenSSL 文件。当前自带二进制仅支持 x86_64，ARM64 / 32 位会在加载前给出明确错误。

### Docker 启用

主 Dockerfile 已将完整 `ruoyi-isup/linux-lib` 复制到 `/app/ruoyi-isup/linux-lib`，并补充系统依赖。更新代码后需要重新构建镜像，旧镜像不含本次适配。

```powershell
# 在 .env 中配置真实地址，不要照抄示例地址用于部署
# EHOME_PUBLIC_HOST=设备可达的服务器IPv4地址
# EHOME_MEDIA_PUBLIC_HOST=浏览器可达的ZLM地址
# EHOME_MEDIA_HTTP_PORT=浏览器可达的ZLM端口（overlay 默认8081）
docker compose -f compose.yaml -f compose.ehome.yaml config --quiet
docker compose -f compose.yaml -f compose.ehome.yaml up -d
```

`compose.ehome.yaml` 只增加后端的 EHome 参数及注册 TCP/UDP、取流 TCP 端口映射；ZLM 使用现有部署。发布工作流已将这个可选配置加入未来发行附件。

### 双平台验证记录（2026-09-17）

- Windows x86_64 / Corretto 8：CMS、Stream 初始化，SDK版本读取，独立测试端口 TCP 连接及资源释放通过。
- Debian 12 x86_64 / Java 8（隔离 Docker）：同上通过。
- Ubuntu 22.04 x86_64 / Temurin 8（与 Dockerfile 相同基础镜像）：同上通过。
- 上述测试均先加载 FFmpeg，再加载海康 SDK；容器无外部网络、不连接业务数据库或真实设备。
- 文件清单与 SHA-256：`docs/ehome-sdk-manifest.json`；测试日志：`codex/ehome-sdk/`。
- 官方新包下载页要求登录与授权，本次未取得新包，未以第三方同名库替换现有二进制。
- 无设备条件下，不能完成 2.x / 3.x / 4.x 的真实注册和视频兼容验收。3.x 仍是待型号确认的协议分支。

## 数据与权限

- Flyway `V1_2_8__ehome_legacy_access.sql` 创建设备表和菜单；只给已有管理员角色分配菜单，普通角色需按需授权。
- 设备按唯一 `device_id` 更新；重连更换登录句柄仍保留名称、备注及分类。
- 列表和每个设备操作均通过部门数据权限过滤；返回值不含密码、会话密钥或原生登录句柄。
- 新接入设备归入配置的 `default-dept-id`，应设置为本系统实际存在的部门。
- 不将设备能力中的“通道存在”直接当作每个子通道在线证明。

## 预览与资源回收

先查询设备实际通道范围，再按通道、主/子码流发起取流。相同通道的多个观看者共享上游流，每个观看者拥有单独的会话租约。
页面关闭主动停止，浏览器每 20 秒续期；60 秒未续期则释放上游信令、原生预览和转封装线程。
设备离线时释放对应视频会话。PS 缓冲区限制为 8 MiB，写入回调不等待网络，超限终止该流。
取流等待 15 秒超时返回明确错误；不同请求、切换通道、关闭弹窗均有过期响应清理。

浏览器播放使用 HTTP-FLV。建议先用 H.264 验证；H.265、音频格式和浏览器支持需按设备与现有 ZLM/播放器版本实测。

## 验证

根 POM 为 Java 8，使用 Corretto 1.8.0_442。

```powershell
rtk mvn -pl ruoyi-isup -am '-Dtest=Ehome*Test' '-Dsurefire.failIfNoSpecifiedTests=false' test
rtk mvn -pl ruoyi-admin -am '-DskipTests' compile
# 在 ruoyi-ui-vue3 下
rtk npm run build:prod -- --outDir ../codex/ehome/dist
```

构建与测试报告保存在项目根目录 `codex/ehome`。页面和弹窗已通过真实浏览器检查，离线弹窗使用临时浏览器响应验证，并已恢复真实列表，未向数据库插入测试设备。
通过编译、模拟回调、接口和页面检查不等于真实摄像机已完成注册与视频播放；仍需提供实际型号及协议版本进行最终验收。

2026-09-16 页面验证：13 项 EHome 回归测试、2 项协议权限测试通过；全应用 Java 8 编译和前端生产构建通过。状态接口显示注册、取流均就绪，列表及版本筛选请求返回 HTTP 200。Flyway 已应用 1.2.8。

2026-09-17 双平台补齐验证：17 项 EHome/SDK/启动分支测试和 2 项协议权限测试通过；全应用 Java 8 构建通过，Compose overlay 检查通过。三种操作系统环境的原生初始化、版本读取、监听与释放实测通过。

2026-09-17 真机验证更新：设备 J88137763 的 Windows EHome 4.0 注册、通道查询、主码流 H.264 1920×1080、子码流 H.264 704×480 实际浏览器画面均通过。修复了 SDK 组件路径须在 Init 后设置的问题，并将 EHome 播放器调整为软件解码以处理本次子码流黑屏。自动选址等22项测试通过。详情见 `codex/ehome-device-test/playback-verified.md`；2.x/3.x及Linux真机仍未验证。
