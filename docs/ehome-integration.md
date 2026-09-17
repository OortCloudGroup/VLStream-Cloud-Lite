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
  public-host: 127.0.0.1
  default-dept-id: 100
  media-public-host: 127.0.0.1 # 浏览器能访问的 ZLMediaKit 地址
  media-rtmp-port: 1935
  media-http-scheme: http
  # media-push-sign: 外部 ZLM 的有效推流签名；默认沿用当前项目基于 media.secret 的摘要
```

设备填写 `ehome.public-host` 和 `isup.cmsServer.Port`，服务端将 `ehome.public-host:isup.smsServer.Port` 告知设备用于推流。
本机默认只监听回环地址；外部设备需要将 `isup.IP` 配置为实际网卡地址或 `0.0.0.0`，并将 `ehome.public-host` 配置为设备可达地址。
注册端口按部署需要开放 TCP/UDP，取流使用 TCP。NAT 环境须映射同号端口。
媒体 API 地址、HTTP 端口、密钥继续使用现有 `media.*` 配置。

当前原生初始化沿用现有应用生命周期。Windows 的 CMS/Stream 回调按版本和预览会话分发；Linux 现有 SDK 默认兼容性保护仍保留，未初始化时返回明确的未就绪状态，不擅自启用可能使 JVM 崩溃的库。Linux 真机部署前需替换并验证兼容的 SDK 运行时及对应回调适配。

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

本次验证：13 项 EHome 回归测试、2 项协议权限测试通过；全应用 Java 8 编译和前端生产构建通过。当前本地服务状态接口显示注册、取流均就绪，列表及版本筛选请求返回 HTTP 200。Flyway 已应用 1.2.8。EHome 列表为空，尚无真机注册或播放验证。
