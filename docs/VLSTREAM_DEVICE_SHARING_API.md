# VLStream 设备共享 API

本文记录 WVP 后端的租户间只读共享接口。所有接口仍要求有效登录；租户身份从 WVP 验证后的认证上下文读取，客户端不得提交或覆盖 `tenantId`。标准响应使用现有 `{ code, msg, data }` 包装。

## 访问与数据边界

- 现有 `GET /video/device/list` 及 VLStream 所有者端点维持原权限和租户限制。
- `GET /video/device/shared-list` 提供已登录租户可发现的跨租户 VLStream 设备；私有设备的最小信息仅用于决定是否申请，不代表可查看设备业务数据或视频。
- `OWNER` 的流清单、预览创建和续租沿用原 `vlstream:device:play` 权限及设备归属检查；同租户身份本身不授予播放权限。既有 VLStream 播放没有部门 DataScope，新增入口复用其实际规则。跨租户 `PUBLIC` 与 `GRANTED` 走专门的只读访问校验。此共享不授予 PTZ、配置、固件、位置修改或远程管理。
- 跨租户共享端点使用 32 位 opaque `deviceRef`，不接受设备数据库行 ID。列表结果的 `id` 为 `VLSTREAM:<deviceRef>`；后端不可从请求体接收申请租户 ID。运行时新增引用使用 Java 随机 UUID，但 V1_2_14 的历史回填使用 MySQL `UUID()`，不能声称所有引用均密码学不可预测。引用始终只定位记录，每次实际访问仍重新鉴权；兼容升级方案见本文末节。
- 共享响应不返回设备 ID、序列号、内网地址、源 URL、源凭据或原所有者播放会话。私有设备无权限时只返回名称、协议、可见性、访问/申请状态和 opaque 引用；公开或有效授权时才返回状态及坐标。
- 现有设备由迁移默认设为 `PRIVATE`。迁移不为现有任何角色分配共享权限，也不产生授权记录。

## 目录和设备访问状态

### `GET /video/device/shared-list`

查询参数：`pageNum`（默认 1）、`pageSize`（默认 100，最大 500）、`keyword`（可选名称匹配）。

返回 `data.rows`、`data.total`、`data.stats`、`data.protocols`。每个设备记录包含目录 `id`、`deviceName`、`protocols`、`visibility`、`viewerAccess`、`myAccessRequestStatus`；跨租户私有无权限设备不包含实际状态、坐标、硬件 ID、序列号、IP 或地址。该端点不修改现有 `/video/device/list` 的权限。

`viewerAccess` 取值：`OWNER`、`PUBLIC`、`GRANTED`、`PENDING`、`NONE`。申请状态可能为 `PENDING`、`APPROVED`、`REJECTED`、`REVOKED`、`EXPIRED` 或 `null`。

### `GET /vlstream/device/shared/{deviceRef}/access`

返回安全摘要字段：`deviceRef`、`protocol`、`deviceName`、`visibility`、`viewerAccess`、`myAccessRequestStatus`。只有 `OWNER`、`PUBLIC`、`GRANTED` 返回 `status`、`latitude`、`longitude`。未知引用使用统一的“设备不存在或无权访问”错误，减少设备枚举信息。

### `PUT /vlstream/device/{deviceRowId}/visibility`

仅设备所有者租户可调用，且要求专用权限 `vlstream:device:share:manage`。

请求体：`{"visibility":"PUBLIC"}` 或 `{"visibility":"PRIVATE"}`。状态切换写审计记录并终止该设备现存共享预览租约；新状态是否允许访问仍在后续每次访问中重新检查。`deviceRowId` 仅供所有者侧原有列表/管理页面使用。

## 申请与审批

### `POST /vlstream/device/shared/{deviceRef}/access-requests`

请求体：`{"reason":"可选说明"}`，最多 1000 个字符。请求租户和用户由认证上下文提供。同一设备、同一申请租户最多有一个待处理申请；重复提交返回已有 `requestId` 和 `PENDING`。已公开设备返回 `PUBLIC`；已有有效授权返回 `APPROVED`。

### `GET /vlstream/device/{deviceRowId}/access-requests`

所有者审批队列，要求 `vlstream:device:share:approve` 且逐设备校验所有者租户。返回待处理申请的 `requestId`、申请租户/用户、理由和创建时间。

### `GET /vlstream/device/{deviceRowId}/access-grants`

所有者授权历史列表，要求 `vlstream:device:share:approve`。当前可信认证租户必须拥有该设备；查询另外限定授权记录的 `owner_tenant_id` 及当前数据库设备归属。客户端不能用 `tenantId` 或其他租户查询参数改变范围。该接口不新增角色权限或实际授权。

可选查询参数：`pageNum`（默认 1，必须大于 0）、`pageSize`（默认 20，范围 1–100）、`status`（默认 `ALL`，支持 `ACTIVE`、`EXPIRED`、`REVOKED`，忽略大小写及首尾空白）。未知状态或无效页码/页大小返回参数错误。结果按实际批准时间降序、再按 `grantId` 降序，返回 `{code,msg,data:{rows,total,pageNum,pageSize,status,asOf}}`；`total` 是当前筛选范围的数量，越过末页返回空 `rows`。

每行返回：字符串 `grantId`、字符串 `requestId`、`requesterTenantId`、可空字符串 `requesterUserId`、`reason`、`status`、`canRevoke`、`requestCreatedAt`、`approvedAt`、字符串 `approvedByUserId`、`expiresAt`、可空 `expiredAt`、`revokedAt`、`revocationAuditId`、`revokedByUserId`、`expirationAuditId`。所有 ID 均来自实际记录，数字 ID 按字符串序列化；关联申请缺失或不匹配设备/申请租户时，申请者用户及理由不返回关联内容。审计 ID/撤销人只读取同设备、同授权、同所属租户且动作匹配的审计记录；没有证据时返回 `null`，不补造审计。响应不包含审计详情文本、设备业务 ID/凭据、源地址或网络信息。

状态使用同一数据库 `asOf` 时刻计算：`revoked_at` 非空为 `REVOKED`，否则 `expires_at<=asOf` 为 `EXPIRED`，其余为 `ACTIVE`。因此到期任务尚未记录 `expiredAt`/`expirationAuditId` 时也会正确显示到期；只读列表不会运行到期任务或写入审计。`canRevoke` 仅对 `ACTIVE` 为真，前端可用实际 `grantId` 调用既有撤销接口；撤销时仍会重新检查权限、归属和当前状态，竞态可能返回冲突。待审批/已拒绝申请没有授权记录，继续使用申请接口展示，不混入授权历史。

### `GET /vlstream/device/access-requests/mine`

返回当前认证租户自己的申请记录；租户由后端上下文确定。记录使用 `deviceRef`，不暴露设备内部行 ID。

### `POST /vlstream/device/access-requests/{requestId}/approve`

要求 `vlstream:device:share:approve` 并校验设备所有者租户。请求体可省略或传 `{"expiresInDays":7}`；省略时读取 `vlstream.device-access.default-grant-days`（缺省 7 天），显式期限必须为 1 至 `vlstream.device-access.max-grant-days`（缺省最大 30 天）。审批人必须显式调用本接口，不会自动授予。

返回 `requestId`、`grantId`、`expiresInDays`、`status=APPROVED`。并发申请和决策按设备行锁顺序串行化；已处理申请返回冲突。

### 拒绝与撤销

- `POST /vlstream/device/access-requests/{requestId}/reject`：同一审批权限和所有者校验；请求体可选 `{"reason":"说明"}`，最多 1000 字符。
- `POST /vlstream/device/access-grants/{grantId}/revoke`：同一审批权限和所有者校验；撤销后授权立即失效。
- 申请、审批、拒绝、撤销、可见性变更及定时过期均写入共享审计表。后台每分钟物化到期状态并触发对应租约清理。

## 共享预览

### `GET /vlstream/device/shared/{deviceRef}/streams`

要求 `OWNER`、`PUBLIC` 或当前租户持有未过期授权。返回可用流的展示字段（如 `id`、名称、协议和可用状态），不返回原始源地址。

### `POST /vlstream/device/shared/{deviceRef}/preview-sessions`

请求体：`{"streamId":123}`。启动前校验设备、流、在线状态和授权；RTSP/RTMP 源必须通过网络安全校验，并由服务端以 DNS pinning 后的源地址建立该查看者独立的 ZLM 代理。响应含 `sessionId`、`playMode`、`expiresInSeconds`、白名单媒体播放地址 `streamInfo`，不包含源 URL、ZLM 代理密钥或媒体服务器内部地址；私网 IP、单标签/内部域名、私有 IPv6 地址和含用户凭据的播放 URL 会被过滤。

CameraRTC 流在网关配置可用时返回一次性信令票据，不将摄像机信令网关地址或设备路径发送给浏览器。响应字段包括 `playMode=cameraRTC`、`deviceRef`、`streamId`、`sessionId`、`webSocketPath`、`ticket`、`expiresInSeconds` 和 `ticketExpiresInSeconds`。票据默认有效 30 秒（可由 `vlstream.device-access.camera-rtc-ticket-ttl-seconds` 配置，服务端限制 10–60 秒）；预览租约默认 120 秒（使用共享预览租约配置，边界 30–300 秒），可通过通用续租/关闭接口管理。`sessionId` 保持 64 位十六进制的不透明摘要形状；数据库的 `ticket_hash` 保存原票据与可信签发登录会话绑定后的 SHA-256 摘要，不保存原票据、原登录令牌或会话凭据。绑定包含可信有效租户、用户、是否联邦及稳定 `LoginUser.token` 的摘要：原生身份使用已验证的 Redis 登录 UUID，联邦身份使用经既有 VLS 验证的令牌。历史原生空租户仍按配置默认租户归一化。不能用客户端 `tenantid`、用户字段、角色名或可变的登录时间/权限集合替代该绑定。

#### CameraRTC WebSocket 持有人认证（2026-10-23）

握手地址仍为返回的同源 `GET /vlstream/device/shared/camera-rtc?ticket=...`，仅允许唯一 `ticket` 查询参数及同源 `Origin`；URL 不携带长效登录令牌。票据不是可由任意持有人使用的 bearer 凭据。升级连接前必须经既有 `TokenService` 得到本次请求的可信登录身份，并与签发票据的用户、租户及登录会话一致。匿名、同租户其他用户、外租户或同用户另一登录会话均拒绝；错误持有人不能消费或烧掉原票据。合法持有人仍须通过一次性、有效期、设备/流归属、在线状态及当前共享授权校验，成功后原子消费票据，以已有 `expires_at` 记录活动信令租约。

原生浏览器 WebSocket 不能任意添加认证请求头，前端在实际调用 `new WebSocket` 时读取当前现有 WVP 认证头，使用标准子协议参数发送：

```js
new WebSocket(sameOriginTicketUrl, [
  'vlstream-shared-rtc.v1',
  'rtc-auth.' + base64url(utf8(JSON.stringify(currentWvpAuthHeaders)))
])
```

载荷仅允许 9 个现有头名（大小写不敏感）：`accesstoken`、`X-VLStream-Token`、`X-WVP-Auth-Source`、`tenantid`、`requesttype`、`appid`、`secretkey`、`Authorization`、`blade-auth`。它们是传给原认证链的未验证输入，不是新的可信身份来源；保留原双令牌及平台验证上下文语义，不根据任意 `Authorization` 推断认证来源。仅精确共享握手路径解析载荷；非浏览器客户端仍可使用现有 HTTP 认证头。

共享专用前端 getter 必须先有当前有效形状的 VLS 会话令牌；当前会话缺失、空白、裸 Bearer 或类型错误时立即拒绝，不从 single 模式历史 URL/storage 中捡回旧 WVP token。通用 REST 原兼容 fallback 保留，仅共享 WebSocket 使用更严格的当前会话要求。

服务端拒绝重复/近似认证子协议、重复字段（含大小写变体）、未知头、非字符串、畸形 UTF-8/Base64URL、控制字符、超长载荷及与实际 HTTP 头冲突的值。JSON UTF-8 最多 6144 字节，Base64URL 载荷最多 8192 字符（不含 `rtc-auth.` 前缀）。过滤器每次重新认证，不接受残留 `SecurityContext`；完成认证后从后续请求视图移除认证子协议，只协商并回显公开固定协议 `vlstream-shared-rtc.v1`。错误响应、应用日志及审计不得包含票据或认证载荷。协议头包含现有敏感凭据，应按认证头管理，不可记录；Base64URL 只是编码。

本次无需新增凭据、真实安全配置、数据库字段或迁移。旧版本尚未消费的票据不符合新会话摘要，升级后必须重新创建预览；前后端须配套更新，原浏览器无认证子协议调用将被拒绝。现有反向代理须转发 `Sec-WebSocket-Protocol`，过长的双令牌/平台头可能超过实际服务器或代理的请求头上限，仍需部署环境确认；本地没有放宽任何头大小、CORS 或防火墙配置。入口 `VITE_SHARED_CAMERA_RTC_READY` 保持原值，代码通过隔离验证不等同于已开启实际共享播放。

此绑定明确覆盖首次 WebSocket 握手。通用续租/关闭仍校验可信用户、租户、设备及当前共享授权，不扩展为所有活动连接的登录会话撤销机制。前端只清理本地存储不代表服务器旧令牌已经撤销；实际已被原认证服务撤销的令牌不能建立新连接，既有连接继续受预览租约和共享撤权检查约束。

信令 WebSocket 由 WVP 使用服务端配置 `video.custom-availability.camera-rtc-signal-url` 连接上游；中继强制绑定已授权流里的 CameraRTC 目标，过滤事件及字段，不转发客户端 `user`/`pwd`，每条消息和空闲期间均复核当前可见性/授权。SDP 和 ICE 候选剔除私网/本地地址；ICE 配置只保留公开 STUN URL，丢弃 TURN URL 和所有凭据。租约关闭或权限变化时，WVP 发出 `__disconnected` 并关闭上游、浏览器连接。非 CameraRTC/RTSP/RTMP 协议仍拒绝共享播放。

共享 `__call` 强制 `audio/video=recvonly`、`datachannel=false`；由于仓库缺少板端 DataChannel 只读能力证明，含 application/SCTP/data-channel 描述的 offer/answer 整体拒绝并终止信令租约，不降级转发。只包含音视频的既有信令投影保持可用；必须通过 DataChannel 初始化的板端可能无法播放，尚未完成真实板端兼容验收。ICE 只解析字面地址并按地址字节分类，过滤展开 IPv6 回环、IPv4-mapped 私网及 ULA；候选结构/扩展未知或格式非法时拒绝，不经 DNS 猜测。SDP `c=`/`o=` 同时处理 IP4/IP6。上游正常 CLOSE 的 `onClosing` 与异常/浏览器关闭共用幂等终止路径，立即使活动租约失效。

**验证范围：**按用户后续决定，外部信令服务不属于本轮修改范围，不作为本轮 WVP 持有人认证修复的阻塞项。本轮仅证明自有 WVP 的认证、一次性票据、信令租约及浏览器接入边界；不修改外部服务，也不把隔离媒体夹具当作真实板端出画或外部网关运行验收。此前关于外部入口、媒体销毁及 TURN 的诊断保留在历史报告中。

### 续租和关闭

- `PUT /vlstream/device/shared/{deviceRef}/preview-sessions/{leaseId}`：按设备、当前租户、当前用户及 ACTIVE 状态定位租约；每次重新核验可见性、授权期限、流和源指纹。授权撤销、改私有或过期时续租失败，并立即结束/删除媒体代理。
- `DELETE /vlstream/device/shared/{deviceRef}/preview-sessions/{leaseId}`：只关闭当前租户和当前用户自己的租约。关停失败的 ZLM 代理由后台清理重试；租约终态不能续租复活。
- 这两个端点也接受 CameraRTC 返回的 64 位十六进制 `sessionId`：续租会重新检查当前授权及信令流，关闭会使活动票据租约立即到期，中继最多每两秒发现权限/租约变化并关闭上游 WebSocket。未消费票据无法续租；客户端可为活动连接续租，默认 120 秒实现期限仍可配置。
- 租约默认 120 秒（配置 `vlstream.device-access.preview-lease-seconds`），服务端边界为 30–300 秒；授权剩余期限更短时按剩余期限收敛。
- ZLM 创建前在独立事务提交 `CREATING` 清理记录及服务端固定 vhost/app/stream 身份；创建成功且重新通过授权/流校验后才成为 `ACTIVE`。撤销与创建并发会转入 `CANCEL_REQUESTED`，不能激活。确定已创建后的失败转 `FAILED`；响应未知时保留创建期限（120 秒，恢复实现期限，非审批期限），避免资源尚在创建时将“当前不存在”误记为清理完成。进程中断/未知响应的记录到期后转 `FAILED`，后台持续重试删除，成功后才记 `proxy_deleted`。未更改表结构或迁移。

## 权限配置与错误

迁移注册两个隐藏权限标识 `vlstream:device:share:manage`、`vlstream:device:share:approve`，但不将其分配给任何现有角色。上线时须由具有权限配置职责的管理员按组织授权；功能代码不会自动放权。所有者租户检查是权限注解之外的强制第二道校验。

联邦身份的协议兼容通行不适用于 `vlstream:device:share` 及子权限；真实 `PermissionService` 必须从可信 `LoginUser.permissions` 判断专用权限。WVP 原生身份已有管理员/显式通配权限仍遵循原框架匹配规则；VLS 联邦权限只按下面的精确契约映射，没有权限集合的联邦账号不能管理或审批共享。目录 SQL 对 NULL、空串、纯空格的历史归属统一归配置默认租户，不因此写入或变更设备所属租户。

### VLS → WVP 可信共享权限契约

WVP 沿现有 `token.vlstreamVerifyTokenAddress` 向 VLS 验证令牌，默认入口为 `GET /blade-system/user/info`；没有新增客户端权限字段或校验端点。VLS 原响应已包含服务端 `data.permissions` 字符串数组：控制器从可信服务端会话取得用户，再查询该用户当前启用的菜单/角色关系。普通用户沿原权限查询；管理员保留原通用 `*:*:*`，但要导出共享权力，仍额外查询其实际菜单分配并仅附加已明确分配的共享两项。查询不赋权，不从管理员标志或角色名生成共享权限。

WVP 只从成功验证响应顶层 `data.permissions` 导入精确的 `vlstream:device:share:manage`、`vlstream:device:share:approve`，并要求响应有可信租户。私有验证身份类型仅在该服务端响应路径构造；再复制到当前 LoginUser/联邦角色集合。`*:*:*`、`vlstream:*`、共享通配、角色名、isAdmin、客户端头/请求体/任意 JWT claim，以及 `data.user.permissions`/params/claims 均不能产生共享权限。旧响应省略 permissions、字段类型错误或数组混入非字符串时，共享权力默认为空，原联邦协议访问行为兼容。

每个联邦请求都会重新验证并读取权限，不用旧登录缓存绕过撤权；相同 token 的下一次请求即可观察到权限移除。获得精确权限仍必须通过所属租户和设备归属校验，不能借客户端 tenantId 访问外租户的审批/历史/管理。本轮未迁移、登记或分配任何真实菜单/角色，未新建服务凭据；上游没有实际精确分配时仍拒绝管理/审批。VLS 外部平台会话实时重验属于既有独立入口，不能把本次本地权限映射测试称为外部平台运行验收。

常见拒绝情况使用现有 WVP `AjaxResult`/`ServiceException` 错误格式；不存在或不可读设备、未授权租户、已处理申请、期限越界、流不可用及未配置 CameraRTC 上游均不会产生可用查看会话。具体 HTTP/业务错误码遵循现有 Spring Security 和全局异常处理映射。

## 历史引用的兼容后续方案（尚未执行）

V1_2_14 保持原样，不重写既有 Flyway 文件。需要统一不可预测引用时，另行评审追加纠正迁移及应用升级：新增引用别名/版本记录，由应用为每台设备生成安全随机新主引用，目录只发新引用；旧引用在明确兼容期映射到同一设备，并继续执行完全相同的权限校验，随后按确认的截止时间停用旧别名。回填、切换和旧别名失效须有数量校验及审计，不能只替换当前唯一引用导致已打开的申请页面失效，也不能以引用难猜代替鉴权。本轮未新增该迁移、未轮换引用或写入实际数据库。
