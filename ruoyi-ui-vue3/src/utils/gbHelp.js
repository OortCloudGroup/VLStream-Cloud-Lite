export const platformFields = [
  { key: 'name', get label() { return translatePhrase("名称") }, example: '指挥平台 A', get description() { return translatePhrase("便于识别的上级名称；每个上级单独建一条配置。") } },
  { key: 'serverGBId', get label() { return translatePhrase("SIP服务国标编码") }, example: '34020000002000000001', get description() { return translatePhrase("上级平台提供的 20 位 SIP 编码。") } },
  { key: 'serverGBDomain', get label() { return translatePhrase("SIP服务国标域") }, example: '3402000000', get description() { return translatePhrase("上级提供的 SIP 域；通常与其国标编码前 10 位一致，以上级要求为准。") } },
  { key: 'serverIp', get label() { return translatePhrase("SIP服务IP") }, example: '192.0.2.20', get description() { return translatePhrase("上级信令服务器地址，不是本机地址或媒体服务器地址。") } },
  { key: 'serverPort', get label() { return translatePhrase("SIP服务端口") }, example: '5060', get description() { return translatePhrase("上级 SIP 监听端口，以上级提供的实际端口为准。") } },
  { key: 'deviceGBId', get label() { return translatePhrase("设备国标编号") }, example: '34020000002000000002', get description() { return translatePhrase("本平台向该上级注册的国标身份，需由双方确认并授权。") } },
  { key: 'deviceIp', get label() { return translatePhrase("本地IP") }, example: '192.0.2.10', get description() { return translatePhrase("选择本机与上级相通的网卡地址；示例地址不可直接使用。") } },
  { key: 'devicePort', get label() { return translatePhrase("本地端口") }, example: '8116', get description() { return translatePhrase("由本平台 SIP 服务配置提供，此处只读。") } },
  { key: 'username', get label() { return translatePhrase("SIP认证用户名") }, example: '由上级分配', get description() { return translatePhrase("与上级登记的认证身份一致，需与实际鉴权报文核对。") } },
  { key: 'password', get label() { return translatePhrase("SIP认证密码") }, example: '由上级分配', get description() { return translatePhrase("使用上级约定的 SIP 密码，不是管理页面登录密码。") } },
  { key: 'expires', get label() { return translatePhrase("注册周期(秒)") }, example: '3600', get description() { return translatePhrase("按上级策略配置，并核对成功应答的 Expires；不同上级分别设置。") } },
  { key: 'keepTimeout', get label() { return translatePhrase("心跳周期(秒)") }, example: '60', get description() { return translatePhrase("按上级要求设置，单位为秒。") } },
  { key: 'sendStreamIp', get label() { return translatePhrase("SDP发流IP") }, example: '192.0.2.10', get description() { return translatePhrase("上级能够访问的媒体地址，结合 NAT 和实际媒体服务配置核对。") } },
  { key: 'transport', get label() { return translatePhrase("信令传输") }, example: 'UDP', get description() { return translatePhrase("与上级 SIP 接口一致；信令方式和 RTP 媒体传输方式分别确认。") } },
  { key: 'characterSet', get label() { return translatePhrase("字符集") }, example: 'GB2312', get description() { return translatePhrase("按上级要求选择 GB2312 或 UTF-8。") } }
]
export const platformExample = Object.fromEntries(platformFields.map(field => [field.key, `例如：${field.example}`]))
export const serviceFields = [
  { get label() { return translatePhrase("编号") }, example: '34020000002000000002', get description() { return translatePhrase("填写到设备的 SIP 服务器编号；设备自身的国标编号需另外配置。") } },
  { get label() { return translatePhrase("域") }, example: '3402000000', get description() { return translatePhrase("填写到设备的 SIP 服务器域，使用本弹窗实际显示值。") } },
  { label: 'IP', example: '192.0.2.10', get description() { return translatePhrase("填写设备能访问的本平台地址；多网卡只选择正确的一个 IP。") } },
  { get label() { return translatePhrase("端口") }, example: '8116 / UDP', get description() { return translatePhrase("填写实际 SIP 服务端口与传输方式，并检查防火墙和端口映射。") } },
  { get label() { return translatePhrase("密码") }, example: '使用本平台配置的 SIP 密码', get description() { return translatePhrase("填入设备的国标注册认证密码字段，不是 Web 登录密码。") } }
]
