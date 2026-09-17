const DEFAULT_RETRY_DELAY_MS = 1000
const DEFAULT_MAX_RETRY_DELAY_MS = 8000
const DEFAULT_CALL_TIMEOUT_MS = 25000
const DEFAULT_DISCONNECT_GRACE_MS = 8000

function parseConfiguration(value) {
  if (!value) return null
  if (typeof value === 'object') return value
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

function createId(runtime) {
  if (runtime.crypto?.randomUUID) return runtime.crypto.randomUUID().toUpperCase()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, char => {
    const value = Math.floor(Math.random() * 16)
    return (char === 'x' ? value : ((value & 0x3) | 0x8)).toString(16)
  }).toUpperCase()
}

export function parseCameraRtcTurnUrls(value) {
  return String(value || '').split(/[;,\s]+/).map(item => item.trim()).filter(Boolean)
}

export class CameraRtcSession {
  constructor(options) {
    this.runtime = options.runtime || globalThis
    this.videoElement = options.videoElement
    this.deviceId = options.deviceId
    this.socketUrl = String(options.socketUrl || '').replace(/\/+$/, '')
    this.extraTurnUrls = options.extraTurnUrls || []
    this.disconnectGraceMs = options.disconnectGraceMs ?? DEFAULT_DISCONNECT_GRACE_MS
    this.maxRetries = options.maxRetries ?? -1
    this.onStatus = options.onStatus || (() => {})
    this.onDiagnostic = options.onDiagnostic || (() => {})
    this.socket = null
    this.peerConnection = null
    this.sessionId = ''
    this.clientId = ''
    this.pendingCandidates = []
    this.remoteDescriptionReady = false
    this.retryCount = 0
    this.retryTimer = null
    this.callTimer = null
    this.disconnectTimer = null
    this.heartbeatTimer = null
    this.stopped = true
    this.generation = 0
  }

  start() {
    if (!this.videoElement || !this.deviceId || !this.socketUrl) {
      this.setStatus('failed', 'CameraRTC 播放参数不完整')
      return
    }
    this.stopped = false
    this.retryCount = 0
    this.openSocket()
  }

  stop() {
    if (this.stopped) return
    this.stopped = true
    this.generation += 1
    this.clearTimers()
    this.sendDisconnect()
    this.cleanupPeerConnection()
    this.cleanupSocket()
    this.setStatus('idle', '')
  }

  retryNow() {
    if (this.stopped) return
    this.retryCount = 0
    this.clearTimers()
    this.cleanupPeerConnection()
    if (this.isSocketOpen()) this.connectToDevice()
    else this.openSocket()
  }

  openSocket() {
    if (this.stopped) return
    this.cleanupSocket()
    this.setStatus('connecting', '正在连接 CameraRTC 信令服务…')
    this.clientId = createId(this.runtime)
    const socket = new this.runtime.WebSocket(`${this.socketUrl}/wswebclient/${this.clientId}`)
    this.socket = socket
    socket.onopen = () => {
      if (this.stopped || socket !== this.socket) return
      this.startHeartbeat()
      this.connectToDevice()
    }
    socket.onmessage = event => this.handleMessage(event)
    socket.onerror = () => this.onDiagnostic({ type: 'signaling-error' })
    socket.onclose = () => {
      if (socket !== this.socket) return
      this.socket = null
      if (!this.stopped) this.scheduleRetry('信令连接中断')
    }
  }

  connectToDevice() {
    if (this.stopped || !this.isSocketOpen()) return
    this.generation += 1
    this.cleanupPeerConnection()
    this.clearCallTimer()
    this.sessionId = `PCWEB-${createId(this.runtime)}`
    this.pendingCandidates = []
    this.remoteDescriptionReady = false
    this.setStatus('connecting', '正在连接设备…')
    this.send('__connectto', { to: this.deviceId })
    this.armCallTimeout()
  }

  async handleMessage(event) {
    let message
    try {
      message = JSON.parse(event.data)
    } catch {
      return
    }
    const data = message?.data || {}
    if (data.sessionId !== this.sessionId) return
    if (message.eventName === '_create') {
      this.handleCreate(data)
    } else if (message.eventName === '_offer') {
      await this.handleOffer(data)
    } else if (message.eventName === '_ice_candidate') {
      await this.handleRemoteCandidate(data)
    } else if (message.eventName === '_session_failed' || message.eventName === '_session_disconnected') {
      this.scheduleRetry(message.eventName === '_session_failed' ? '设备会话失败' : '设备结束了播放会话')
    }
  }

  handleCreate(data) {
    if (!['online', 'sleep'].includes(data.state)) {
      this.scheduleRetry('设备当前离线')
      return
    }
    const configurations = [data.iceServers, data.domainnameiceServers, data.domainnameIceServers]
      .map(parseConfiguration)
      .filter(configuration => Array.isArray(configuration?.iceServers) && configuration.iceServers.length)
    const iceConfiguration = configurations[0] || {
      iceServers: this.extraTurnUrls.map(url => ({ urls: url }))
    }
    this.setStatus('negotiating', '正在协商媒体通道…')
    this.send('__call', {
      to: this.deviceId,
      datachannel: 'true',
      audio: 'recvonly',
      video: 'recvonly',
      user: 'admin',
      pwd: '123456',
      mode: 'live',
      source: 'MainStream',
      iceservers: JSON.stringify(iceConfiguration)
    })
    this.iceConfiguration = iceConfiguration
    this.armCallTimeout()
  }

  async handleOffer(data) {
    const generation = this.generation
    try {
      const connection = this.createPeerConnection(generation)
      await connection.setRemoteDescription({ type: 'offer', sdp: data.sdp })
      if (!this.isCurrentConnection(connection, generation)) return
      this.remoteDescriptionReady = true
      await this.flushRemoteCandidates(connection, generation)
      const answer = await connection.createAnswer()
      await connection.setLocalDescription(answer)
      if (!this.isCurrentConnection(connection, generation)) return
      this.send('__answer', { to: this.deviceId, type: answer.type, sdp: answer.sdp })
      this.setStatus('negotiating', '正在建立视频通道…')
    } catch (error) {
      this.onDiagnostic({ type: 'offer-error', message: error?.message || String(error) })
      this.scheduleRetry('媒体协商失败')
    }
  }

  createPeerConnection(generation) {
    this.cleanupPeerConnection()
    const connection = new this.runtime.RTCPeerConnection(this.iceConfiguration || { iceServers: [] })
    this.peerConnection = connection
    connection.ontrack = event => {
      if (!this.isCurrentConnection(connection, generation)) return
      const stream = event.streams?.[0] || new this.runtime.MediaStream([event.track])
      this.videoElement.srcObject = stream
      const playResult = this.videoElement.play()
      if (playResult?.catch) playResult.catch(() => { this.videoElement.controls = true })
      this.clearCallTimer()
      this.retryCount = 0
      this.setStatus('playing', '播放中')
    }
    connection.onicecandidate = event => {
      if (!event.candidate || !this.isCurrentConnection(connection, generation)) return
      this.send('__ice_candidate', {
        to: this.deviceId,
        candidate: JSON.stringify({
          candidate: event.candidate.candidate,
          sdpMid: event.candidate.sdpMid,
          sdpMLineIndex: event.candidate.sdpMLineIndex
        })
      })
    }
    connection.oniceconnectionstatechange = () => {
      if (!this.isCurrentConnection(connection, generation)) return
      if (['connected', 'completed'].includes(connection.iceConnectionState)) {
        this.clearDisconnectTimer()
        this.clearCallTimer()
        return
      }
      if (connection.iceConnectionState === 'disconnected') {
        this.armDisconnectTimer(connection)
      } else if (connection.iceConnectionState === 'failed') {
        this.scheduleRetry('媒体连接失败')
      }
    }
    return connection
  }

  async handleRemoteCandidate(data) {
    let candidate
    try {
      candidate = typeof data.candidate === 'string' ? JSON.parse(data.candidate) : data.candidate
    } catch {
      return
    }
    if (!candidate) return
    if (!this.peerConnection || !this.remoteDescriptionReady) {
      this.pendingCandidates.push(candidate)
      return
    }
    try {
      await this.peerConnection.addIceCandidate(candidate)
    } catch (error) {
      this.onDiagnostic({ type: 'remote-candidate-error', message: error?.message || String(error) })
    }
  }

  async flushRemoteCandidates(connection, generation) {
    const candidates = this.pendingCandidates.splice(0)
    for (const candidate of candidates) {
      if (!this.isCurrentConnection(connection, generation)) return
      await connection.addIceCandidate(candidate)
    }
  }

  armCallTimeout() {
    this.clearCallTimer()
    this.callTimer = this.runtime.setTimeout(() => this.scheduleRetry('设备呼叫超时'), DEFAULT_CALL_TIMEOUT_MS)
  }

  armDisconnectTimer(connection) {
    if (this.disconnectTimer) return
    this.setStatus('recovering', `网络波动，等待 ${Math.ceil(this.disconnectGraceMs / 1000)} 秒自动恢复…`)
    this.disconnectTimer = this.runtime.setTimeout(() => {
      this.disconnectTimer = null
      if (connection === this.peerConnection && connection.iceConnectionState === 'disconnected') this.scheduleRetry('媒体连接中断')
    }, this.disconnectGraceMs)
  }

  scheduleRetry(reason) {
    if (this.stopped || this.retryTimer) return
    this.clearCallTimer()
    this.clearDisconnectTimer()
    this.cleanupPeerConnection()
    if (this.maxRetries >= 0 && this.retryCount >= this.maxRetries) {
      this.setStatus('failed', `${reason}，自动重连已达上限`)
      return
    }
    const attempt = ++this.retryCount
    const delay = Math.min(DEFAULT_RETRY_DELAY_MS * 2 ** Math.max(0, attempt - 1), DEFAULT_MAX_RETRY_DELAY_MS)
    this.setStatus('retrying', `${reason}，${Math.ceil(delay / 1000)} 秒后重连`)
    this.retryTimer = this.runtime.setTimeout(() => {
      this.retryTimer = null
      if (this.isSocketOpen()) this.connectToDevice()
      else this.openSocket()
    }, delay)
  }

  startHeartbeat() {
    this.clearHeartbeat()
    this.heartbeatTimer = this.runtime.setInterval(() => {
      if (this.sessionId && this.isSocketOpen()) this.send('__ping', { to: this.deviceId })
    }, 20000)
  }

  send(eventName, data = {}) {
    if (!this.isSocketOpen()) return false
    this.socket.send(JSON.stringify({
      eventName,
      data: {
        sessionId: this.sessionId,
        sessionType: 'IE',
        messageId: createId(this.runtime),
        from: this.clientId,
        ...data
      }
    }))
    return true
  }

  sendDisconnect() {
    if (this.sessionId && this.isSocketOpen()) this.send('__disconnected', { to: this.deviceId })
  }

  isSocketOpen() {
    return this.socket?.readyState === (this.runtime.WebSocket?.OPEN ?? 1)
  }

  isCurrentConnection(connection, generation) {
    return !this.stopped && connection === this.peerConnection && generation === this.generation
  }

  cleanupPeerConnection() {
    const connection = this.peerConnection
    this.peerConnection = null
    this.remoteDescriptionReady = false
    this.pendingCandidates = []
    if (connection) {
      connection.ontrack = null
      connection.onicecandidate = null
      connection.oniceconnectionstatechange = null
      if (connection.signalingState !== 'closed') connection.close()
    }
    const stream = this.videoElement?.srcObject
    if (stream?.getTracks) stream.getTracks().forEach(track => track.stop())
    if (this.videoElement) this.videoElement.srcObject = null
  }

  cleanupSocket() {
    this.clearHeartbeat()
    const socket = this.socket
    this.socket = null
    if (!socket) return
    socket.onopen = null
    socket.onmessage = null
    socket.onerror = null
    socket.onclose = null
    if (socket.readyState === 0 || socket.readyState === 1) socket.close(1000, 'player closed')
  }

  clearTimers() {
    if (this.retryTimer) this.runtime.clearTimeout(this.retryTimer)
    this.retryTimer = null
    this.clearCallTimer()
    this.clearDisconnectTimer()
    this.clearHeartbeat()
  }

  clearCallTimer() {
    if (this.callTimer) this.runtime.clearTimeout(this.callTimer)
    this.callTimer = null
  }

  clearDisconnectTimer() {
    if (this.disconnectTimer) this.runtime.clearTimeout(this.disconnectTimer)
    this.disconnectTimer = null
  }

  clearHeartbeat() {
    if (this.heartbeatTimer) this.runtime.clearInterval(this.heartbeatTimer)
    this.heartbeatTimer = null
  }

  setStatus(state, message) {
    this.onStatus({ state, message, retryCount: this.retryCount })
  }
}
