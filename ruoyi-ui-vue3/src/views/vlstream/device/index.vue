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
              <span class="title">{{ $tp("VLStream 设备") }}</span>
              <el-tag :type="mediaAvailable ? 'success' : 'danger'" size="small">
                WVP ZLM {{ mediaAvailable ? $tp('可用') : $tp('不可用') }}
              </el-tag>
            </div>
            <div class="filters">
              <el-input v-model="query.keyword" clearable :placeholder="$tp('设备名称 / ID / 序列号')" @keyup.enter="search" />
              <el-select v-model="query.online" clearable :placeholder="$tp('全部状态')">
                <el-option :label="$tp('在线')" :value="true" />
                <el-option :label="$tp('离线')" :value="false" />
              </el-select>
              <el-button type="primary" @click="search">{{ $tp("搜索") }}</el-button>
              <el-button @click="reset">{{ $tp("重置") }}</el-button>
            </div>
          </div>
        </template>

        <el-alert v-if="serviceError" :title="$tp(serviceError)" type="error" :closable="false" show-icon class="service-alert" />

        <el-table v-loading="loading" :data="devices" stripe scrollbar-always-on @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="42" fixed="left" />
          <el-table-column type="index" :label="$tp('序号')" width="56" fixed="left" />
          <el-table-column :label="$tp('在线状态')" width="100" fixed="left" align="center">
            <template #default="{ row }">
              <el-tag :type="row.online ? 'success' : 'info'" effect="light" class="online-status">
                <span class="status-dot" aria-hidden="true" />{{ row.online ? $tp('在线') : $tp('离线') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="deviceName" :label="$tp('设备名称')" min-width="150" show-overflow-tooltip />
          <el-table-column prop="deviceId" :label="$tp('设备 ID')" min-width="190" show-overflow-tooltip />
          <el-table-column prop="deviceModel" :label="$tp('设备型号')" min-width="150" show-overflow-tooltip />
          <el-table-column v-if="hasDeviceSerial" prop="deviceSerial" :label="$tp('序列号')" min-width="140" show-overflow-tooltip />
          <el-table-column prop="ipAddr" label="IP" min-width="120" />
          <el-table-column prop="firmwareVersion" :label="$tp('RootFS 版本')" min-width="110" />
          <el-table-column :label="$tp('设备能力')" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ capabilityText(row.capabilitiesJson) }}</template>
          </el-table-column>
          <el-table-column :label="$tp('最近上线')" min-width="180">
            <template #default="{ row }">{{ formatDeviceTime(row.lastOnlineTime) }}</template>
          </el-table-column>
          <el-table-column :label="$tp('最后心跳')" min-width="180">
            <template #default="{ row }">{{ formatDeviceTime(row.lastHeartbeatTime) }}</template>
          </el-table-column>
          <el-table-column :label="$tp('操作')" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">{{ $tp("详情") }}</el-button>
              <el-button link type="primary" :disabled="row.online !== true" @click="openPreview(row)">{{ $tp("播放") }}</el-button>
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
        :title="previewDialogTitle"
        class="locale-dialog locale-dialog--wide"
        destroy-on-close
        @closed="releasePreview"
      >
        <div class="stream-bar">
          <span>{{ $tp("视频流") }}</span>
          <el-select v-model="selectedStreamId" :placeholder="$tp('请选择视频流')" @change="startPreview">
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
          <el-empty v-else :description="$tp('请选择可用视频流')" />
        </div>
      </el-dialog>

      <el-dialog v-model="detailVisible" :title="$tp('设备详情')" class="locale-dialog locale-dialog--wide" destroy-on-close>
        <div v-loading="detailLoading">
          <el-descriptions v-if="detailDevice" :column="descriptionColumns" border>
            <el-descriptions-item :label="$tp('设备名称')">{{ detailDevice.deviceName || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$tp('设备 ID')">{{ detailDevice.deviceId || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$tp('设备型号')">{{ detailDevice.deviceModel || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$tp('在线状态')">{{ detailDevice.online ? $tp('在线') : $tp('离线') }}</el-descriptions-item>
            <el-descriptions-item :label="$tp('IP 地址')">{{ detailDevice.ipAddr || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$tp('RootFS 版本')">
              <div class="firmware-version-actions">
                <span>{{ detailDevice.firmwareVersion || '-' }}</span>
                <el-button v-if="firmwareDetail?.availableUpgrades?.length || firmwareDetail?.latestTask"
                  link type="primary" @click="firmwareVisible = true">{{ $tp('固件升级') }}</el-button>
              </div>
            </el-descriptions-item>
            <el-descriptions-item :label="$tp('开机时间')">{{ deviceBootTimeText(detailDevice) }}</el-descriptions-item>
            <el-descriptions-item :label="$tp('在线时长')">{{ deviceOnlineDurationText(detailDevice, detailClock) }}</el-descriptions-item>
            <el-descriptions-item :label="$tp('最后心跳')" :span="2">{{ formatDeviceTime(detailDevice.lastHeartbeatTime) }}</el-descriptions-item>
            <el-descriptions-item :label="$tp('设备能力')" :span="2">{{ capabilityText(detailDevice.capabilitiesJson) }}</el-descriptions-item>
            <el-descriptions-item :label="$tp('位置坐标')" :span="2">{{ deviceLocationText(detailDevice) }}</el-descriptions-item>
          </el-descriptions>

          <div class="model-heading"><h4>{{ $tp("设备运行模型") }}</h4></div>
          <el-table scrollbar-always-on
            :data="reportedModels"
            border
            :empty-text="detailDevice?.modelsJson == null ? $tp('设备尚未上报模型信息') : $tp('设备上报的模型列表为空')"
          >
            <el-table-column prop="modelId" :label="$tp('模型 ID')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="modelName" :label="$tp('模型名称')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="version" :label="$tp('版本')" min-width="100" />
            <el-table-column prop="format" :label="$tp('格式')" width="90" />
            <el-table-column :label="$tp('状态')" width="100"><template #default="{ row }">{{ modelStatusText(row.status) }}</template></el-table-column>
          </el-table>
          <p class="snapshot-note">{{ $tp("模型和能力为设备最近一次上报的信息，离线时保留供查看。") }}</p>

          <h4>{{ $tp("视频源") }}</h4>
          <el-table :data="detailStreams" border scrollbar-always-on :empty-text="$tp('设备没有上报视频源')">
            <el-table-column prop="channelId" :label="$tp('通道')" min-width="120" />
            <el-table-column prop="streamType" :label="$tp('码流类型')" width="110" />
            <el-table-column prop="protocol" :label="$tp('协议')" width="90" />
            <el-table-column :label="$tp('默认流')" width="90"><template #default="{ row }">{{ row.defaultStream ? $tp('是') : $tp('否') }}</template></el-table-column>
            <el-table-column :label="$tp('视频源地址')" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.sourceUrl || row.url || $tp('未上报') }}</template>
            </el-table-column>
          </el-table>
        </div>
      </el-dialog>

      <el-dialog v-model="firmwareVisible" :title="$tp('固件升级')" class="locale-dialog locale-dialog--wide" destroy-on-close>
        <el-alert v-if="firmwareDetail?.upgradeBlockedReason" :title="$tp(firmwareDetail.upgradeBlockedReason)"
          type="info" :closable="false" show-icon />
        <h4>{{ $tp('可用固件升级') }}</h4>
        <el-table :data="firmwareDetail?.availableUpgrades || []" border scrollbar-always-on :empty-text="$tp('没有更高版本的兼容固件')">
          <el-table-column :label="$tp('目标')" width="90"><template #default>RootFS</template></el-table-column>
          <el-table-column prop="currentVersion" :label="$tp('当前版本')" width="120" />
          <el-table-column prop="latestVersion" :label="$tp('最新版本')" width="120" />
          <el-table-column prop="fileName" :label="$tp('固件包')" min-width="220" show-overflow-tooltip />
          <el-table-column :label="$tp('操作')" width="130" align="center">
            <template #default="{ row }">
              <el-button v-hasPermi="['vlstream:firmware:deploy']" type="primary" size="small"
                :loading="firmwareBusy" :disabled="!firmwareDetail?.canUpgrade || firmwareBusy"
                @click="deployFirmware(row)">{{ $tp('固件升级') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <template v-if="firmwareDetail?.latestTask">
          <h4>{{ $tp('最近 OTA 任务') }}</h4>
          <el-descriptions :column="descriptionColumns" border>
            <el-descriptions-item :label="$tp('目标')">RootFS</el-descriptions-item>
            <el-descriptions-item :label="$tp('状态')">
              {{ taskStatusText(firmwareDetail.latestTask.deployStatus) }}
              <el-button v-if="isActiveTask(firmwareDetail.latestTask.deployStatus)" v-hasPermi="['vlstream:firmware:deploy']"
                link type="danger" :loading="firmwareBusy" :disabled="firmwareBusy" @click="cancelLatestTask">{{ $tp('终止任务') }}</el-button>
            </el-descriptions-item>
            <el-descriptions-item :label="$tp('版本')"><span dir="ltr">{{ firmwareDetail.latestTask.currentVersion }} → {{ firmwareDetail.latestTask.targetVersion }}</span></el-descriptions-item>
            <el-descriptions-item :label="$tp('任务 ID')">{{ firmwareDetail.latestTask.requestId }}</el-descriptions-item>
            <el-descriptions-item v-if="firmwareDetail.latestTask.failureReason" :label="$tp('说明')" :span="2">{{ firmwareDetail.latestTask.failureReason }}</el-descriptions-item>
          </el-descriptions>
          <el-button class="firmware-refresh" :loading="detailLoading" @click="reloadFirmwareDetail">{{ $tp('刷新') }}</el-button>
        </template>
      </el-dialog>
    </div>
  </DeviceClassificationLayout>
</template>

<script setup>
import { monitorDevicePreview } from '@/utils/devicePreviewGuard'
import { translatePhrase } from '@/i18n'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useWindowSize } from '@vueuse/core'
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
import { cancelDeviceFirmwareTask, deployDeviceFirmware, getDeviceDetail, getMediaStatus, listDevices, listStreams, startPreview as requestPreview } from '@/api/vlstream/device'

const loading = ref(false)
const { width: viewportWidth } = useWindowSize()
const descriptionColumns = computed(() => viewportWidth.value < 768 ? 1 : 2)
const devices = ref([])
const hasDeviceSerial = computed(() => devices.value.some(device => String(device.deviceSerial ?? '').trim().length > 0))
const total = ref(0)
const mediaAvailable = ref(false)
const serviceError = ref('')
const classificationDeviceKeys = ref([])
const previewVisible = ref(false)
const previewLoading = ref(false)
const currentDevice = ref(null)
const previewDialogTitle = computed(() => {
  const name = currentDevice.value?.deviceName || currentDevice.value?.deviceId
  const title = translatePhrase('实时预览')
  return name ? `${name} - ${title}` : title
})
const streams = ref([])
const selectedStreamId = ref(null)
const webrtcUrl = ref('')
const cameraRtcConfig = ref(null)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailStreams = ref([])
const detailDevice = ref(null)
const firmwareDetail = ref(null)
const firmwareVisible = ref(false)
const firmwareBusy = ref(false)
let detailGeneration = 0
const detailClock = ref(Date.now())
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', online: undefined })
let detailClockTimer

const reportedModels = computed(() => parseSnapshot(detailDevice.value?.modelsJson) || [])

watch(detailVisible, visible => {
  clearInterval(detailClockTimer)
  if (visible) {
    detailClock.value = Date.now()
    detailClockTimer = setInterval(() => { detailClock.value = Date.now() }, 1000)
  } else {
    detailGeneration++
    firmwareVisible.value = false
  }
})

function errorMessage(error, fallback) {
  return error?.response?.data?.msg || error?.message || fallback
}

async function loadDevices() {
  loading.value = true
  serviceError.value = ''
  try {
    const result = await listDevices(query)
    devices.value = result?.rows || []
    total.value = Number(result?.total || 0)
  } catch (error) {
    devices.value = []
    total.value = 0
    serviceError.value = errorMessage(error, 'WVP 服务不可用，无法加载 VLStream 设备')
  } finally {
    loading.value = false
  }
}

async function loadMediaStatus() {
  try { mediaAvailable.value = Boolean((await getMediaStatus())?.data?.available) }
  catch { mediaAvailable.value = false }
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
  const generation = ++detailGeneration
  currentDevice.value = device
  detailDevice.value = device
  firmwareDetail.value = null
  firmwareVisible.value = false
  detailStreams.value = []
  detailVisible.value = true
  detailLoading.value = true
  try {
    const [streamsResult, detailResult] = await Promise.all([listStreams(device.id), getDeviceDetail(device.id)])
    if (generation !== detailGeneration) return
    detailStreams.value = streamsResult?.data || []
    detailDevice.value = detailResult?.data?.device || device
    firmwareDetail.value = detailResult?.data || null
  } catch (error) {
    if (generation !== detailGeneration) return
    ElMessage.error(errorMessage(error, translatePhrase("加载设备详情失败")))
  } finally {
    if (generation === detailGeneration) detailLoading.value = false
  }
}

async function reloadFirmwareDetail() {
  const generation = detailGeneration
  const id = detailDevice.value?.id
  if (!id) return
  detailLoading.value = true
  try {
    const result = await getDeviceDetail(id)
    if (generation !== detailGeneration) return
    firmwareDetail.value = result?.data || null
    detailDevice.value = result?.data?.device || detailDevice.value
  } catch (error) {
    if (generation === detailGeneration) ElMessage.error(errorMessage(error, translatePhrase('加载设备详情失败')))
  } finally { if (generation === detailGeneration) detailLoading.value = false }
}

async function deployFirmware(candidate) {
  if (firmwareBusy.value || !firmwareDetail.value?.canUpgrade) return
  const generation = detailGeneration
  const id = detailDevice.value?.id
  try {
    await ElMessageBox.confirm(translatePhrase('确认将设备升级到 RootFS {version}？升级过程中设备将重启。', { version: candidate.latestVersion }), translatePhrase('固件升级确认'), {
      type: 'warning', confirmButtonText: translatePhrase('确定'), cancelButtonText: translatePhrase('取消')
    })
  } catch { return }
  if (generation !== detailGeneration || firmwareBusy.value || !firmwareDetail.value?.canUpgrade) return
  firmwareBusy.value = true
  try {
    await deployDeviceFirmware(id, candidate.firmwareId)
    ElMessage.success(translatePhrase('OTA 固件升级指令已下发'))
    if (generation === detailGeneration) await reloadFirmwareDetail()
  } catch (error) {
    ElMessage.error(errorMessage(error, translatePhrase('固件升级指令下发失败')))
  } finally { firmwareBusy.value = false }
}

async function cancelLatestTask() {
  const task = firmwareDetail.value?.latestTask
  if (!task || firmwareBusy.value || !isActiveTask(task.deployStatus)) return
  const generation = detailGeneration
  const id = detailDevice.value?.id
  try {
    await ElMessageBox.confirm(translatePhrase('终止只会解除 WVP 平台任务锁，不会向设备发送取消指令。请确认设备已经停止升级后再继续。'), translatePhrase('终止 OTA 任务'), {
      type: 'warning', confirmButtonText: translatePhrase('确定'), cancelButtonText: translatePhrase('取消')
    })
  } catch { return }
  if (generation !== detailGeneration || firmwareBusy.value) return
  firmwareBusy.value = true
  try {
    await cancelDeviceFirmwareTask(id, task.requestId)
    ElMessage.success(translatePhrase('平台 OTA 任务已终止'))
    if (generation === detailGeneration) await reloadFirmwareDetail()
  } catch (error) { ElMessage.error(errorMessage(error, translatePhrase('终止 OTA 任务失败'))) }
  finally { firmwareBusy.value = false }
}

function isActiveTask(status) {
  return ['CREATED', 'PUBLISHED', 'ACCEPTED', 'DOWNLOADING', 'VERIFYING', 'INSTALLING', 'REBOOTING'].includes(status)
}

function taskStatusText(status) {
  return translatePhrase(({
    CREATED: '待发布', PUBLISHED: '已下发', ACCEPTED: '设备已接收', DOWNLOADING: '下载中',
    VERIFYING: '校验中', INSTALLING: '安装中', REBOOTING: '重启中', SUCCESS: '升级成功',
    FAILED: '升级失败', CANCELLED: '已终止', TIMED_OUT: '已超时'
  })[status] || status || '-')
}

async function openPreview(device) {
  if (device.online !== true) return ElMessage.warning(translatePhrase('设备离线，不允许播放'))
  currentDevice.value = device
  try {
    streams.value = (await listStreams(device.id))?.data || []
    if (!streams.value.length) {
      ElMessage.warning(translatePhrase("设备没有上报可用视频流"))
      return
    }
    const preferred = streams.value.find(item => item.defaultStream) || streams.value[0]
    selectedStreamId.value = preferred.id
    previewVisible.value = true
    await startPreview(preferred.id)
  } catch (error) {
    ElMessage.error(errorMessage(error, translatePhrase("加载视频源失败")))
  }
}

let previewGeneration = 0
let stopPreviewMonitor = () => {}
watch(previewVisible, visible => { if (!visible) releasePreview() })

async function startPreview(streamId) {
  releasePreview()
  if (!streamId || !currentDevice.value) return
  const generation = previewGeneration
  const id = currentDevice.value.id
  previewLoading.value = true
  try {
    const device = (await getDeviceDetail(id))?.data?.device
    if (generation !== previewGeneration || !previewVisible.value) return
    if (device?.online !== true) {
      currentDevice.value.online = false
      previewVisible.value = false
      return ElMessage.warning(translatePhrase('设备离线，不允许播放'))
    }
    stopPreviewMonitor = monitorDevicePreview(async () => (await getDeviceDetail(id))?.data?.device, () => {
      releasePreview()
      previewVisible.value = false
      ElMessage.warning(translatePhrase('设备离线或状态无法确认，已停止播放'))
    })
    const stream = (await requestPreview(currentDevice.value.id, streamId))?.data || {}
    if (generation !== previewGeneration || !previewVisible.value) return
    if (stream.playMode === 'cameraRTC' && stream.url) {
      cameraRtcConfig.value = parseCameraRtcConfig(stream.url)
      return
    }
    webrtcUrl.value = location.protocol === 'https:' ? (stream.rtcs || stream.webrtcUrl) : (stream.rtc || stream.webrtcUrl)
    if (!webrtcUrl.value) ElMessage.error(translatePhrase("WVP 未返回可用播放地址"))
  } catch (error) {
    ElMessage.error(errorMessage(error, translatePhrase("创建预览失败")))
  } finally {
    previewLoading.value = false
  }
}

function releasePreview() {
  previewGeneration += 1
  stopPreviewMonitor()
  previewLoading.value = false
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

onMounted(() => { loadDevices(); loadMediaStatus() })
onBeforeUnmount(() => {
  detailGeneration++
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
.firmware-version-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.firmware-refresh { margin-top: 14px; }
h4 { margin: 18px 0 10px; }
@media (max-width: 1200px) { .header { align-items: flex-start; flex-direction: column; } }
</style>
