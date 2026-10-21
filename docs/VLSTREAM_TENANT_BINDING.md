# VLStream 设备默认租户与初始化绑定

VLS 标准版依赖 WVP Lite 的设备目录。WVP 保存权威归属，标准版读取归属后授权远程管理等操作。
本次实现范围是 VLStream MQTT 设备；其他接入协议和分类目录名称本身的租户改造不在本次范围内。

## 归属规则

- 新设备未携带 `tenantId`：按与VLS一致的业务模式 `token.tenantType` 决定默认归属。
  `single` 用 `vlstream.device.default-tenant-id`（默认 `000000`）；`multi` 用
  `vlstream.device.multi-tenant-default-tenant-id`（默认 `0e391fd7-1033-4f09-88c0-187582fee462`）。
  未知模式、空默认租户以及多租户默认误设为 `000000` 明确拒绝，不回退到单租户。
- 已有设备后续漏报租户：保留当前归属；历史空值按配置的默认租户解释。
- 初始化时明确指定租户：在 state 消息公共头放 `tenantId` 和 `tenantBindingProof`，平台校验后自动登记。
- 已存在设备不能通过心跳更换租户；默认租户设备也不能通过后来添加字段改变归属。
- 用户范围来自服务端认证后的 LoginUser；请求参数中的租户不能覆盖此范围。

数据库由 Flyway `V1_2_9__vlstream_device_tenant.sql` 增加可空租户字段及索引，不猜测历史设备归属。
更新后，列表、详情、视频源、预览、固件下发/取消、VLStream 分类设备计数及分类关联读取/设置均核对归属。
内部设备查询返回有效租户供标准版使用；内部接口仍只能通过既有受信后端网络访问。

## 初始化凭据

1. WVP 和受信初始化环境配置独立随机的 `VLSTREAM_DEVICE_TENANT_BINDING_SECRET`（至少 32 字节）。
2. VLS与WVP是一体化系统，VLS必须依赖WVP设备中心。两项目的认证、租户、权限、设备归属等
   共通规则必须同步；各组件仍可有自己的部署配置。
   WVP沿用VLS的业务模式 `token.tenantType` / `SSO_TENANT_TYPE`（未配置时为 `single`），
   不从独立的 `WVP_AUTH_MODE` 本地/SSO登录方式推断单、多租户。
   单租户WVP用 `VLSTREAM_DEVICE_DEFAULT_TENANT_ID`；多租户用
   `VLSTREAM_DEVICE_MULTI_TENANT_DEFAULT_TENANT_ID`。VLS既有
   `vlstream.native-device.default-tenant-id` / `multi-tenant-default-tenant-id` 应分别与其匹配。
3. 在初始化环境执行：

   ```text
   node tools/create-device-tenant-binding.mjs CAM-1 tenant-a <受保护的输出文件路径>
   ```

4. 把输出 JSON 的设备 ID、租户 ID、绑定凭据写入该设备的初始化配置；设备上不保存平台主签名密钥。
5. 每次显式发送 `tenantId` 时，同时发送 `tenantBindingProof`。密钥轮换需重新签发并同步设备配置。

凭据算法：对 UTF-8 `v1\n{deviceId}\n{tenantId}`（末尾无换行）做 HMAC-SHA256，结果按无 padding 的
Base64URL 编码，前缀为 `v1.`。设备 ID 限 1～100 位、租户 ID 限 1～64 位字母/数字/下划线/连字符。
凭据缺失、篡改、换设备/租户使用或尝试跨租户覆盖返回 403；未配置签名密钥返回 503。

该凭据用于证明归属配置，不替代 MQTT 认证；部署仍须执行每设备凭据、自身 Topic ACL 和生产 TLS。
旧固件未携带租户时无需绑定凭据，默认租户接入继续可用。初始化必须先于首次入网，否则设备会先归默认租户。

## 验证和部署边界

2026-10-20：正式WVP已收到状态，但默认设备归属仍为 `000000`，而真实列表按多租户默认
`0e391fd7-1033-4f09-88c0-187582fee462` 查询。此前VLS旧状态链的多租户回退没有同步到实际承接
上报的WVP。经用户授权，仅将重新锁定核验的行7/8/9从 `000000` 改到上述租户，行ID与关联保持；
正式设备页显示3条（核验时两在线、一离线）。原值与事务工具见VLS
`codex/wvp-tenant-20261020/`。这项存量修复已生效，不会自动迁移其他已有设备。

本地新增模式解析供后续发布：必须让WVP的 `token.tenantType` 与VLS一致，多租户部署明确设为
`multi`；两项默认值各自适用于对应模式。仅发布JAR而仍保留默认 `single` 不会启用多租户回退。
本地24项定向测试及13模块全量编译已通过；此次未部署新包、重启服务或改线上全局变量，不能把存量纠正称为线上新增设备默认规则已修复。

Java 8 下租户归属/联邦身份/控制器/心跳及分类定向测试通过。MySQL 8.4 隔离实例验证了迁移重复执行、
历史空值保留、A/B 租户查询互不混入、默认租户包含旧设备，以及租户 ID 大小写精确匹配。
初始化工具与 Java 校验器使用独立计算的已知测试向量验证。

本次尚未更新正在运行的 WVP/VLS、执行业务库迁移或更改真实设备配置。部署顺序为 WVP 迁移和后端、
VLS 后端、隧道网关；正式板端显式租户上报与跨租户浏览器联调仍待完成。
