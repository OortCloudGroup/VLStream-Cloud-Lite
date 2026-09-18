<template>
  <div class="ehome-player">
    <div ref="container" class="player-surface" />
    <div v-if="message" class="player-message"><el-icon><VideoCamera /></el-icon><span>{{ message }}</span></div>
  </div>
</template>
<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { VideoCamera } from '@element-plus/icons-vue'
const props = defineProps({ url: { type: String, required: true } })
const container = ref()
const message = ref('正在连接视频…')
let player
let disposed = false
onMounted(() => {
  if (!window.Jessibuca) { message.value = '播放器加载失败，请刷新页面'; return }
  player = new window.Jessibuca({
    container: container.value, decoder: '/jessibuca/decoder.js', isFlv: true,
    isNotMute: false, useMSE: false, useWCS: false, videoBuffer: 0.3, loadingTimeout: 15,
    loadingText: '正在连接视频…', showBandwidth: true,
    operateBtns: { fullscreen: true, screenshot: true, audio: true }
  })
  player.on('play', () => { if (!disposed) message.value = '' })
  for (const event of ['error', 'timeout', 'loadingTimeout']) {
    player.on(event, () => { if (!disposed) message.value = '视频连接失败，请检查设备码流或重新播放' })
  }
  Promise.resolve(player.play(props.url)).catch(() => { if (!disposed) message.value = '视频连接失败，请重新播放' })
})
onBeforeUnmount(() => { disposed = true; player?.destroy(); player = null })
</script>
<style scoped>
.ehome-player { position: relative; width: 100%; height: 100%; min-height: 350px; background: #101c2d; }
.player-surface { position: absolute; inset: 0; }
.player-message { position: absolute; inset: 0; pointer-events: none; display: flex; flex-direction: column; justify-content: center; align-items: center; gap: 16px; color: #aabbd0; }
.player-message .el-icon { font-size: 36px; }
</style>
