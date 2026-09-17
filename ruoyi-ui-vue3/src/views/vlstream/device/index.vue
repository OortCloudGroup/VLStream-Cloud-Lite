<template>
  <DeviceClassificationLayout
    protocol-type="VLSTREAM"
    :selected-device-keys="classificationDeviceKeys"
    @filter-change="handleClassificationFilter"
    @assigned="loadDevices"
  >
    <div class="protocol-page">
      <el-card shadow="never">
        <template #header>
          <div class="header">
            <div class="header-title">
              <span class="title">VLStream 设备</span>
              <el-tag :type="mediaAvailable ? 'success' : 'danger'" size="small">
                WVP ZLM {{ mediaAvailable ? '可用' : '不可用' }}
              </el-tag>
            </div>
            <div class="filters">
              <el-input v-model="query.keyword" clearable placeholder="设备名称 / ID / 序列号" @keyup.enter="search" />
              <el-select v-model="query.online" clearable placeholder="全部状态">
                <el-option label="在线" :value="true" />
                <el-option label="离线" :value="false" />
              </el-select>
              <el-button type="primary" @click="search">搜索</el-button>
              <el-button @click="reset">重置</el-button>
            </div>
          </div>
        </template>

        <el-alert v-if="serviceError" :title="serviceError" type="error" :closable="false" show-icon class="service-alert" />

        <el-table v-loading="loading" :data="devices" stripe @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="42" fixed="left" />
          <el-table-column type="index" label="序号" width="56" fixed="left" />
          <el-table-column label="在线状态" width="100" fixed="left" align="center">
            <template #default="{ row }">
              <el-tag :type="row.online ? 'success' : 'info'" effect="light" class="online-status">
                <span class="status-dot" aria-hidden="true" />{{ row.online ? '在线' : '离线' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="deviceName" label="设备名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="deviceId" label="设备 ID" min-width="190" show-overflow-tooltip />
          <el-table-column prop="deviceModel" label="设备型号" min-width="150" show-overflow-tooltip />
          <el-table-column v-if="hasDeviceSerial" prop="deviceSerial" label="序列号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="ipAddr" label="IP" min-width="120" />
          <el-table-column prop="firmwareVersion" label="RootFS 版本" min-width="110" />
          <el-table-column label="设备能力" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ capabilityText(row.capabilitiesJson) }}</template>
          </el-table-column>
          <el-table-column label="最近上线" min-width="180">
            <template #default="{ row }">{{ formatDeviceTime(row.lastOnlineTime) }}</template>
          </el-table-column>
          <el-table-column label="最后心跳" min-width="180">
            <template #default="{ row }">{{ formatDeviceTime(row.lastHeartbeatTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button link type="primary" :disabled="!mediaAvailable" @click="openPreview(row)">播放</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination">
          <el-pagination
            v-model:current-page="query.pageNum"
            v-model:page-size="query.pageSize"
            background
            layout="total, prev, pager, next, sizes"
            :total="total"
            @change="loadDevices"
          />
        </div>
      </el-card>

      <el-dialog
        v-model="previewVisible"
        :title="`${currentDevice?.deviceName || currentDevice?.deviceId || ''} 实时预览`"
        width="900px"
        destroy-on-close
        @closed="releasePreview"
      >
        <div class="stream-bar">
          <span>视频流</span>
          <el-select v-model="selectedStreamId" placeholder="请选择视频流" @change="startPreview">
            <el-option
              v-for="stream in streams"
              :key="stream.id"
              :value="stream.id"
              :label="`${stream.channelId || stream.streamName || stream.id} (${stream.streamType || stream.protocol || '-'})`"
            />
          </el-select>
        </div>
        <div v-loading="previewLoading" class="player">
          <rtc-player v-if="webrtcUrl" :video-url="webrtcUrl" :hasaudio="true" />
          <camera-rtc-player
            v-else-if="cameraRtcConfig"
            :device-id="cameraRtcConfig.cameraId"
            :socket-url="cameraRtcConfig.socketUrl"
          />
          <el-empty v-else description="请选择可用视频流" />
        </div>
      </el-dialog>

      <el-dialog v-model="detailVisible" title="设备详情" width="820px" destroy-on-close>
        <div v-loading="detailLoading">
          <el-descriptions v-if="detailDevice" :column="2" border>
            <el-descriptions-item label="设备名称">{{ detailDevice.deviceName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="设备 ID">{{ detailDevice.deviceId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="设备型号">{{ detailDevice.deviceModel || '-' }}</el-descriptions-item>
            <el-descriptions-item label="在线状态">{{ detailDevice.online ? '在线' : '离线' }}</el-descriptions-item>
            <el-descriptions-item label="IP 地址">{{ detailDevice.ipAddr || '-' }}</el-descriptions-item>
            <el-descriptions-item label="RootFS 版本">{{ detailDevice.firmwareVersion || '-' }}</el-descriptions-item>
            <el-descriptions-item label="开机时间">{{ deviceBootTimeText(detailDevice) }}</el-descriptions-item>
            <el-descriptions-item label="在线时长">{{ deviceOnlineDurationText(detailDevice, detailClock) }}</el-descriptions-item>
            <el-descriptions-item label="最后心跳" :span="2">{{ formatDeviceTime(detailDevice.lastHeartbeatTime) }}</el-descriptions-item>
            <el-descriptions-item label="设备能力" :span="2">{{ capabilityText(detailDevice.capabilitiesJson) }}</el-descriptions-item>
            <el-descriptions-item label="位置坐标" :span="2">{{ deviceLocationText(detailDevice) }}</el-descriptions-item>
          </el-descriptions>

          <div class="model-heading"><h4>设备运行模型</h4></div>
          <el-table
            :data="reportedModels"
            border
            :empty-text="detailDevice?.modelsJson == null ? '设备尚未上报模型信息' : '设备上报的模型列表为空'"
          >
            <el-table-column prop="modelId" label="模型 ID" min-width="140" show-overflow-tooltip />
            <el-table-column prop="modelName" label="模型名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="version" label="版本" min-width="100" />
            <el-table-column prop="format" label="格式" width="90" />
            <el-table-column label="状态" width="100"><template #default="{ row }">{{ modelStatusText(row.status) }}</template></el-table-column>
          </el-table>
          <p class="snapshot-note">模型和能力为设备最近一次上报的信息，离线时保留供查看。</p>

          <h4>视频源</h4>
          <el-table :data="detailStreams" border empty-text="设备没有上报视频源">
            <el-table-column prop="channelId" label="通道" min-width="120" />
            <el-table-column prop="streamType" label="码流类型" width="110" />
            <el-table-column prop="protocol" label="协议" width="90" />
            <el-table-column label="默认流" width="90"><template #default="{ row }">{{ row.defaultStream ? '是' : '否' }}</template></el-table-column>
            <el-table-column label="视频源地址" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.sourceUrl || row.url || '未上报' }}</template>
            </el-table-column>
          </el-table>
        </div>
      </el-dialog>
    </div>
  </DeviceClassificationLayout>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import CameraRtcPlayer from '@/components/CameraRtcPlayer.vue'
import DeviceClassificationLayout from '@/components/DeviceClassificationLayout/index.vue'
import RtcPlayer from '@/components/rtcPlayer/index.vue'
import {
  capabilityText,
  deviceBootTimeText,
  deviceLocationText,
  deviceOnlineDurationText,
  formatDeviceTime,
  modelStatusText,
  parseSnapshot
} from '@/utils/deviceStateDisplay'
import { getDeviceDetail, getMediaStatus, listDevices, listStreams, startPreview as requestPreview } from '@/api/vlstream/device'

const loading = ref(false)
const devices = ref([])
const hasDeviceSerial = computed(() => devices.value.some(device => String(device.deviceSerial ?? '').trim().length > 0))
const total = ref(0)
const mediaAvailable = ref(false)
const serviceError = ref('')
const classificationDeviceKeys = ref([])
const previewVisible = ref(false)
const previewLoading = ref(false)
const currentDevice = ref(null)
const streams = ref([])
const selectedStreamId = ref(null)
const webrtcUrl = ref('')
const cameraRtcConfig = ref(null)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailStreams = ref([])
const detailDevice = ref(null)
const detailClock = ref(Date.now())
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', online: undefined })
let detailClockTimer

const reportedModels = computed(() => parseSnapshot(detailDevice.value?.modelsJson) || [])

watch(detailVisible, visible => {
  clearInterval(detailClockTimer)
  if (visible) detailClockTimer = setInterval(() => { detailClock.value = Date.now() }, 1000)
})

function errorMessage(error, fallback) {
  return error?.response?.data?.msg || error?.message || fallback
}

async function loadDevices() {
  loading.value = true
  serviceError.value = ''
  try {
    const [result, media] = await Promise.all([listDevices(query), getMediaStatus()])
    devices.value = result?.rows || []
    total.value = Number(result?.total || 0)
    mediaAvailable.value = Boolean(media?.data?.available)
  } catch (error) {
    devices.value = []
    total.value = 0
    mediaAvailable.value = false
    serviceError.value = errorMessage(error, 'WVP 服务不可用，无法加载 VLStream 设备')
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  loadDevices()
}

function reset() {
  Object.assign(query, {
    pageNum: 1,
    pageSize: 10,
    keyword: '',
    online: undefined,
    categoryType: undefined,
    categoryId: undefined,
    unclassified: undefined
  })
  loadDevices()
}

function handleSelectionChange(selection) {
  classificationDeviceKeys.value = selection.map(item => String(item.id))
}

function handleClassificationFilter(filter) {
  Object.assign(query, filter, { pageNum: 1 })
  loadDevices()
}

async function openDetail(device) {
  currentDevice.value = device
  detailDevice.value = device
  detailStreams.value = []
  detailVisible.value = true
  detailLoading.value = true
  try {
    const [streamsResult, detailResult] = await Promise.all([listStreams(device.id), getDeviceDetail(device.id)])
    detailStreams.value = streamsResult?.data || []
    detailDevice.value = detailResult?.data?.device || device
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载设备详情失败'))
  } finally {
    detailLoading.value = false
  }
}

async function openPreview(device) {
  currentDevice.value = device
  try {
    streams.value = (await listStreams(device.id))?.data || []
    if (!streams.value.length) {
      ElMessage.warning('设备没有上报可用视频流')
      return
    }
    const preferred = streams.value.find(item => item.defaultStream) || streams.value[0]
    selectedStreamId.value = preferred.id
    previewVisible.value = true
    await startPreview(preferred.id)
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载视频源失败'))
  }
}

async function startPreview(streamId) {
  releasePreview()
  if (!streamId || !currentDevice.value) return
  previewLoading.value = true
  try {
    const stream = (await requestPreview(currentDevice.value.id, streamId))?.data || {}
    if (stream.playMode === 'cameraRTC' && stream.url) {
      cameraRtcConfig.value = parseCameraRtcConfig(stream.url)
      return
    }
    webrtcUrl.value = location.protocol === 'https:' ? (stream.rtcs || stream.webrtcUrl) : (stream.rtc || stream.webrtcUrl)
    if (!webrtcUrl.value) ElMessage.error('WVP 未返回可用播放地址')
  } catch (error) {
    ElMessage.error(errorMessage(error, '创建预览失败'))
  } finally {
    previewLoading.value = false
  }
}

function releasePreview() {
  cameraRtcConfig.value = null
  webrtcUrl.value = ''
}

function parseCameraRtcConfig(streamUrl) {
  const url = new URL(String(streamUrl || '').trim())
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('CameraRTC requires an HTTP(S) URL')
  const parts = url.pathname.split('/').filter(Boolean)
  const marker = parts.findIndex(part => part.toLowerCase() === 'videocall')
  const cameraId = marker >= 0 ? parts[marker + 1] : ''
  if (!cameraId) throw new Error('CameraRTC URL is missing camera ID')
  const socketProtocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return { cameraId: decodeURIComponent(cameraId), socketUrl: `${socketProtocol}//${location.host}/bus/camera-rtc` }
}

onMounted(loadDevices)
onBeforeUnmount(() => {
  clearInterval(detailClockTimer)
  releasePreview()
})
</script>

<style scoped>
.protocol-page { padding: 20px; }
.header, .filters, .stream-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.header-title { display: flex; align-items: center; white-space: nowrap; }
.title { margin-right: 12px; font-size: 18px; font-weight: 600; }
.filters .el-input { width: 240px; }
.filters .el-select { width: 120px; }
.service-alert { margin-bottom: 14px; }
.online-status { font-weight: 600; }
.status-dot { display: inline-block; width: 7px; height: 7px; margin-right: 6px; border-radius: 50%; background: currentColor; }
.pagination { display: flex; justify-content: center; padding-top: 20px; }
.stream-bar { justify-content: flex-start; margin-bottom: 12px; }
.stream-bar .el-select { width: 360px; }
.player { min-height: 480px; background: #000; display: flex; align-items: center; justify-content: center; }
.player :deep(#webRtcPlayerBox), .player :deep(#rtcPlayer) { width: 100%; max-height: 520px; }
.model-heading { display: flex; align-items: center; justify-content: space-between; margin-top: 18px; }
.snapshot-note { color: #909399; font-size: 12px; }
h4 { margin: 18px 0 10px; }
@media (max-width: 1200px) { .header { align-items: flex-start; flex-direction: column; } }
</style>
