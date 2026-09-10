<template>
  <DeviceClassificationLayout protocol-type="VLSTREAM" :selected-device-keys="classificationDeviceKeys" @filter-change="handleClassificationFilter" @assigned="getList">
  <div class="app-container">
    <div class="toolbar-with-search">
      <div class="toolbar-left">
        <el-tag :type="mediaAvailable ? 'success' : 'danger'">
          {{ mediaAvailable ? 'ZLM 可用' : 'ZLM 不可用' }}
        </el-tag>
      </div>
      <div class="searchHeight_out flexRowAC">
        <search-height-box
          keyword="keyword"
          placeholder="请输入设备名称 / ID / 序列号"
          :data="searchData"
          @handle="searchResetFn"
        />
        <export-excel-pdf />
      </div>
    </div>

    <table-self
      class="new_table"
      header-cell-class-name="header_tenant_cell"
      stripe
      v-loading="loading"
      :data="deviceList"
      current-row-key="deviceId"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" :width="clacPXToVW(55)" align="center" />
      <el-table-column type="index" label="序号" :width="clacPXToVW(70)" align="center" />
      <el-table-column prop="deviceName" label="设备名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="deviceId" label="设备 ID" min-width="220" show-overflow-tooltip />
      <el-table-column prop="deviceSerial" label="序列号" min-width="150" show-overflow-tooltip />
      <el-table-column label="状态" :width="clacPXToVW(90)" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.online ? 'success' : 'info'">{{ scope.row.online ? '在线' : '离线' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ipAddr" label="IP" min-width="130" show-overflow-tooltip />
      <el-table-column prop="firmwareVersion" label="固件版本" min-width="120" show-overflow-tooltip />
      <el-table-column prop="lastHeartbeatTime" label="最后心跳" min-width="170" show-overflow-tooltip />
      <el-table-column label="操作" align="right" fixed="right" :width="clacPXToVW(100)">
        <template #default="scope">
          <div class="operateAppBox flexRowAC" style="justify-content: flex-end;">
            <div
              class="new_table_svg_group"
              :class="{ 'is-disabled': !mediaAvailable }"
              @click.stop="mediaAvailable && preview(scope.row)"
              v-hasPermi="['vlstream:device:play']"
            >
              <span>预览</span>
            </div>
          </div>
        </template>
      </el-table-column>
    </table-self>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum"
                v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog v-model="previewOpen" :title="previewTitle" width="820px" append-to-body destroy-on-close>
      <el-select v-if="streams.length > 1" v-model="selectedStreamId" style="width: 100%; margin-bottom: 12px"
                 @change="playSelected">
        <el-option v-for="stream in streams" :key="stream.id" :value="stream.id"
                   :label="`${stream.channelId} / ${stream.streamType}${stream.defaultStream ? '（默认）' : ''}`" />
      </el-select>
      <div v-loading="previewLoading" class="player-box">
        <rtc-player v-if="rtcUrl" :video-url="rtcUrl" :hasaudio="true" />
        <el-empty v-else description="正在拉取视频流" />
      </div>
    </el-dialog>
  </div>
  </DeviceClassificationLayout>
</template>

<script setup>
import { getCurrentInstance, onMounted, reactive, ref } from 'vue'
import RtcPlayer from '@/components/rtcPlayer/index.vue'
import { getMediaStatus, listDevices, listStreams, startPreview } from '@/api/vlstream/device'
import DeviceClassificationLayout from '@/components/DeviceClassificationLayout/index.vue'
import { clacPXToVW } from '@/utils/index'

const { proxy } = getCurrentInstance()
const loading = ref(false)
const deviceList = ref([])
const classificationDeviceKeys = ref([])
const total = ref(0)
const mediaAvailable = ref(false)
const previewOpen = ref(false)
const previewLoading = ref(false)
const previewTitle = ref('视频预览')
const currentDevice = ref(null)
const streams = ref([])
const selectedStreamId = ref('')
const rtcUrl = ref('')
const queryParams = reactive({ pageNum: 1, pageSize: 10, keyword: '', online: undefined })
const searchData = ref([
  {
    label: '状态',
    value: 'online',
    type: 'select',
    option: [
      { label: '在线', value: true },
      { label: '离线', value: false }
    ],
    default: undefined
  }
])

function getList() {
  loading.value = true
  Promise.all([listDevices(queryParams), getMediaStatus()]).then(([devices, media]) => {
    deviceList.value = devices.rows || []
    total.value = devices.total || 0
    mediaAvailable.value = Boolean(media.data && media.data.available)
  }).finally(() => { loading.value = false })
}

function handleQuery() { queryParams.pageNum = 1; getList() }
function resetQuery() {
  queryParams.keyword = ''
  queryParams.online = undefined
  handleQuery()
}
function handleSelectionChange(selection) { classificationDeviceKeys.value = selection.map(item => String(item.id)) }
function handleClassificationFilter(filter) { Object.assign(queryParams, filter, { pageNum: 1 }); getList() }

function searchResetFn(val) {
  queryParams.pageNum = 1
  queryParams.keyword = val.keyword || ''
  queryParams.online = val.online === undefined || val.online === '' || val.online === null ? undefined : val.online
  getList()
}

async function preview(row) {
  currentDevice.value = row
  previewTitle.value = `${row.deviceName || row.deviceId} - 视频预览`
  const result = await listStreams(row.id)
  streams.value = result.data || []
  if (!streams.value.length) {
    proxy.$modal.msgWarning('设备没有上报可用视频流')
    return
  }
  const preferred = streams.value.find(item => item.defaultStream) || streams.value[0]
  selectedStreamId.value = preferred.id
  previewOpen.value = true
  await playSelected()
}

async function playSelected() {
  if (!currentDevice.value || !selectedStreamId.value) return
  previewLoading.value = true
  rtcUrl.value = ''
  try {
    const result = await startPreview(currentDevice.value.id, selectedStreamId.value)
    const stream = result.data || {}
    rtcUrl.value = location.protocol === 'https:' ? stream.rtcs : stream.rtc
    if (!rtcUrl.value) proxy.$modal.msgError('ZLM 未返回 WebRTC 播放地址')
  } finally {
    previewLoading.value = false
  }
}

onMounted(getList)
</script>

<style scoped>
.player-box { min-height: 420px; background: #111; display: flex; align-items: center; justify-content: center; }
.player-box :deep(#rtcPlayer) { width: 100%; }
</style>
