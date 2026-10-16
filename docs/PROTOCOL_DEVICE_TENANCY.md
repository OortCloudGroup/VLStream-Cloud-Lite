# RTSP/ONVIF 手动接入与租户归属

用户在 RTSP 或 ONVIF 管理手动创建保存的协议来源。WVP 保存设备、源地址和租户归属；
VLS 提供已验证的业务身份；媒体仍使用既有 WVP/ZLM 预览租约。

## 身份与业务流程

请求范围来自 Spring Security LoginUser.tenantId。联邦请求经 WVP 向 VLS 验证，原生 SSO
保留已验证租户。本地旧会话没有租户且不是联邦会话时，兼容 vlstream.device.default-tenant-id。
缺失联邦租户拒绝，不采信客户端 tenantid、JSON 或 params。管理员/顶级租户没有跨租户例外；
原有接口权限和部门 DataScope 保留。

- 手动新增由服务端覆盖提交的 tenantId，写入可信业务租户；后续修改不改归属。
- 列表、地图、分页总数、详情、历史入口、更新和删除使用当前租户及原部门范围。
  更新/删除 SQL 再带可信租户谓词，批量删除先验证全部行。
- 预览从同一授权目录读取保存的行，再创建媒体；续租/停止重验设备范围，再按原协议、设备 ID、
  用户和 sessionId 检查租约。媒体失败如实返回，不虚构成功。
- RTSP/ONVIF 分类关联/计数、聚合视频目录、按保存行读取的 ONVIF 云台入口使用相同范围。
  分类名称目录仍共享，本次不改其他协议归属。
- ONVIF 同 IP 重复检查限于有效租户，不向其他租户暴露已有设备。

## 默认租户和历史记录

本次不改变 VLStream MQTT 主动上报的初始化流程：未知归属首次上报归配置默认租户，已有设备
漏报保留归属，显式绑定须有可信凭据，后续心跳不能改归属。见 VLSTREAM_TENANT_BINDING.md。
没有增加设备自动认领或转移接口。

VLS 单租户固定业务租户默认 000000，多租户未知上报设备使用
vlstream.native-device.multi-tenant-default-tenant-id。WVP 不从浏览器模式、请求头或操作员
推断全局默认归属。双方已有默认设备租户配置须核对；不能把单租户默认值当作多租户播放准入，
也不能借改默认值把全部历史设备重新解释为当前租户。

Flyway V1_2_13__protocol_device_tenant_ownership.sql 为 rtsp_device/onvif_device 增加可空
tenant_id 和索引。迁移只把 NULL 或 OCTET_LENGTH=0 的记录赋给部署方明确核准的目标租户；所有非空归属（包括空白、000000 和其他租户）均保留。
仅作用于 rtsp_device/onvif_device，重复执行不转移已有归属，不按部署账号猜测。
目标来自 PROTOCOL_DEVICE_LEGACY_TENANT_ID（protocol-device.legacy-tenant-id），通过 Java 配置传给 Flyway 占位符 protocol_legacy_tenant_id；未设置时兼容原 vlstream.device.default-tenant-id，最终单租户默认值为 000000。
多租户部署必须在发布前核对并显式配置已批准的历史归属目标；不得假定各服务的默认租户相同。升级后新手动记录直接归可信创建租户。本次未部署或执行生产迁移。

## 失败和验证边界

缺身份、缺联邦租户、未知 ID、其他租户或部门不可见行在媒体请求前拒绝。迁移失败不得删除
授权检查绕过。新增列/索引保留原列，旧应用可读取；回退旧应用会恢复其旧权限行为。

本地真实 MyBatis SQL、DataScope AOP、合成 H2 数据及模拟媒体覆盖同租户新增后的 CRUD、
预览/续租/停止、跨租户拒绝、客户端伪造、原生 SSO/本地兼容、部门隔离及批量原子授权。
独立 MySQL 迁移夹具覆盖重复执行、历史空值、已有归属、部分执行、手工已加列和大小写匹配。
这些不代表生产实播成功；真实 SSO/媒体及用户批准的历史归属仍须发布验收。
