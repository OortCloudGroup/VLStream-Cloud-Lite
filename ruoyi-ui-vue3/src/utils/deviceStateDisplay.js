export function parseSnapshot(value) {
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    return Array.isArray(parsed) ? parsed : null
  } catch {
    return null
  }
}

export function capabilityText(value) {
  const capabilities = parseSnapshot(value)
  if (!capabilities) return translatePhrase("未上报")
  const labels = {
    video: '视频',
    audio: '音频',
    ptz: '云台',
    aiInfer: 'AI 推理',
    face: '人脸识别',
    recording: '录像',
    ota: '固件升级'
  }
  return capabilities.length ? capabilities.map(item => labels[item] || item).join('、') : translatePhrase("无")
}

export function formatDeviceTime(value) {
  if (value == null || value === '') return '-'
  const date = new Date(typeof value === 'string' ? value.replace(' ', 'T') : value)
  if (Number.isNaN(date.getTime())) return '-'
  const pad = number => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function modelStatusText(status) {
  return ({ loaded: translatePhrase("已加载"), running: translatePhrase("运行中"), stopped: translatePhrase("已停止"), failed: translatePhrase("异常") })[status] || status || translatePhrase("未上报")
}

export function deviceBootTimeText(device) {
  try {
    const telemetry = typeof device?.telemetryJson === 'string' ? JSON.parse(device.telemetryJson) : device?.telemetryJson
    const bootTime = telemetry?.bootTime
    if (typeof bootTime === 'number' && Number.isFinite(bootTime) && bootTime > 0 && bootTime <= Date.now()) {
      return formatDeviceTime(bootTime)
    }
  } catch {
    // 设备没有上报有效启动时间时继续采用心跳信息估算。
  }
  const heartbeatIndex = Number(device?.heartbeatIndex)
  const reportedAt = device?.lastReportedAt
  const timestamp = new Date(typeof reportedAt === 'string' ? reportedAt.replace(' ', 'T') : reportedAt).getTime()
  if (!Number.isSafeInteger(heartbeatIndex) || heartbeatIndex < 1 || !Number.isFinite(timestamp) || timestamp <= 0 || timestamp > Date.now()) {
    return translatePhrase("未上报")
  }
  const text = formatDeviceTime(timestamp - (heartbeatIndex - 1) * 60000)
  return text === '-' ? translatePhrase("未上报") : text.slice(0, 16)
}

export function deviceOnlineDurationText(device, now = Date.now()) {
  if (!device?.online) return translatePhrase("离线")
  const start = device.lastOnlineTime
  const timestamp = new Date(typeof start === 'string' ? start.replace(' ', 'T') : start).getTime()
  if (!Number.isFinite(timestamp) || timestamp > now) return translatePhrase("未上报")
  const seconds = Math.floor((now - timestamp) / 1000)
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return translatePhrase("{days}天 {hours}小时 {minutes}分 {seconds}秒", {
    days,
    hours,
    minutes,
    seconds: seconds % 60
  })
}

export function deviceLocationText(device) {
  const { longitude, latitude } = device || {}
  if (typeof longitude !== 'number' || typeof latitude !== 'number' || !Number.isFinite(longitude) || !Number.isFinite(latitude)
    || Math.abs(longitude) > 180 || Math.abs(latitude) > 90) return translatePhrase("未上报")
  return translatePhrase("经度 {longitude}，纬度 {latitude}（WGS84）", { longitude: longitude, latitude: latitude })
}
