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

部署时应让 WVP 与 VLS 使用一致的业务租户模式，并为单租户和多租户分别配置匹配的默认租户 ID。模式解析和默认值验证不代表已更新任何运行环境、业务数据库或设备配置；部署前应根据目标环境复核这些值，并按标准备份、迁移和回滚流程执行升级。

本文描述的是 WVP 侧租户绑定契约，不替代 VLS、设备固件、MQTT ACL 或生产 TLS 的单独配置与联调验收。
