<template>
  <DeviceClassificationLayout :key="classificationKey" protocol-type="EHOME" :selected-device-keys="selectedKeys" @filter-change="filterClassification" @assigned="loadDevices">
    <section class="ehome-page">
      <header class="ehome-heading">
        <div class="heading-title"><div class="protocol-icon"><svg-icon icon-class="ehome" /></div><div><h1>EHome 设备</h1><p>旧版海康设备主动接入 · 设备注册后自动加入列表</p></div></div>
        <el-button type="primary" :icon="Plus" @click="guide = true">接入设备</el-button>
      </header>

      <div class="connection-strip">
        <div><span class="status-dot" :class="{ online: service.registrationReady }" /><strong>{{ service.registrationReady ? '注册服务就绪' : '注册服务未就绪' }}</strong><span class="service-address">{{ service.host || '—' }}<span v-if="service.registrationPort">:{{ service.registrationPort }}</span></span></div>
        <el-button link type="primary" @click="guide = true">接入指南 <el-icon><ArrowRight /></el-icon></el-button>
      </div>
      <el-alert v-if="serviceLoaded && !service.registrationReady" :title="service.message || '接入服务未启动'" type="warning" :closable="false" show-icon class="service-warning" />

      <div class="list-heading"><h2>设备列表 <span>{{ total }}</span></h2><span class="family-note">EHome 2.x / 3.x / 4.x</span></div>
      <el-form class="device-filters" @submit.prevent="search">
        <el-input v-model="query.keyword" placeholder="搜索名称、设备 ID、序列号或 IP" clearable :prefix-icon="Search" @clear="search" />
        <el-select v-model="query.status" popper-class="ehome-select" placeholder="全部状态" aria-label="设备状态" clearable @change="search"><el-option label="在线" value="ON" /><el-option label="离线" value="OFFLINE" /></el-select>
        <el-select v-model="query.devProtocolVersion" popper-class="ehome-select" placeholder="全部版本" aria-label="协议版本" clearable @change="search"><el-option v-for="version in ['2', '3', '4']" :key="version" :label="`EHome ${version}.x`" :value="version" /></el-select>
        <el-button native-type="submit" type="primary" plain>搜索</el-button>
        <el-button :icon="Refresh" :loading="loading" aria-label="刷新设备列表" @click="refresh" />
      </el-form>
      <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon />
      <el-table v-loading="loading" :data="rows" class="ehome-table" row-key="id" @selection-change="selection => selectedKeys = selection.map(row => String(row.id))">
        <el-table-column type="selection" width="42" />
        <el-table-column label="设备" min-width="175"><template #default="{ row }"><div class="device-name">{{ row.name || row.deviceId }}</div><div class="device-id">{{ row.deviceId }}</div></template></el-table-column>
        <el-table-column label="状态" width="88"><template #default="{ row }"><span class="device-status" :class="{ online: row.status === 'ON' }"><i />{{ row.status === 'ON' ? '在线' : '离线' }}</span></template></el-table-column>
        <el-table-column label="协议版本" width="115"><template #default="{ row }"><span class="version-label">{{ row.devProtocolVersion ? `EHome ${row.devProtocolVersion}` : '未识别' }}</span></template></el-table-column>
        <el-table-column label="设备 IP" prop="ipAddress" min-width="130" show-overflow-tooltip />
        <el-table-column label="最近更新" prop="updateTime" min-width="160" />
        <el-table-column label="操作" fixed="right" width="155"><template #default="{ row }"><el-button v-hasPermi="['ehome:device:query']" link type="primary" @click="openPreview(row)">通道 / 预览</el-button><el-button v-hasPermi="['ehome:device:edit']" link type="primary" @click="openEdit(row)">编辑</el-button></template></el-table-column>
        <template #empty><span /></template>
      </el-table>
          <div v-if="!loading && !rows.length" class="empty-state">
            <div class="empty-icon"><el-icon><VideoCamera /></el-icon><span>+</span></div>
            <h3>{{ loadError ? '设备列表加载失败' : filtered ? '没有找到匹配的设备' : '等待第一台 EHome 设备接入' }}</h3>
            <p>{{ loadError ? '请确认服务可用后重试。' : filtered ? '试试其他关键词，或调整版本与状态筛选。' : '在设备端启用平台接入，填写服务器地址和设备 ID。' }}</p>
            <el-button v-if="!filtered && !loadError" type="primary" plain @click="guide = true">查看接入步骤 <el-icon><ArrowRight /></el-icon></el-button>
            <el-button v-else @click="loadError ? refresh() : resetFilters()">{{ loadError ? '重试' : '清空筛选' }}</el-button>
            <div v-if="!filtered && !loadError" class="empty-steps"><span><b>1</b> 启用 EHome</span><i /><span><b>2</b> 配置服务器</span><i /><span><b>3</b> 自动注册</span></div>
          </div>
      <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="loadDevices" />
      <footer class="ehome-footnote"><el-icon><InfoFilled /></el-icon>ISUP 5.0 设备请使用左侧 ISUP 协议入口；协议版本以设备上报为准。</footer>
    </section>
  </DeviceClassificationLayout>

  <el-drawer v-model="guide" title="接入 EHome 设备" size="min(480px, 94vw)" append-to-body class="ehome-guide">
    <div class="guide-intro"><span>EHome</span><h2>让设备主动连接平台</h2><p>适用于旧版海康平台接入。无需填写设备 RTSP 地址。</p></div>
    <ol class="guide-steps">
      <li><h3>开启设备的平台接入</h3><p>登录摄像机或 NVR 的配置页面，进入「网络 → 高级配置 → 平台接入」，启用 EHome。</p></li>
      <li><h3>填写服务器参数</h3><div class="guide-values"><div><span>服务器地址</span><strong>{{ service.host || '—' }}</strong><el-button link type="primary" @click="copy(service.host)">复制</el-button></div><div><span>注册端口</span><strong>{{ service.registrationPort || '—' }}</strong><el-button link type="primary" @click="copy(service.registrationPort)">复制</el-button></div><div><span>取流端口</span><strong>{{ service.streamPort || '—' }}</strong></div></div><p>设备 ID 请保持唯一。跨网络接入时使用设备可达的服务器地址，并映射注册及取流端口。</p></li>
      <li><h3>选择设备支持的协议版本</h3><p>2.x 与 4.x 按各自流程取流；3.x 需要按设备型号联调确认。不要选择 ISUP 5.0。</p></li>
      <li><h3>保存并等待上线</h3><p>设备注册成功后自动出现在此列表。点击「通道 / 预览」，选择通道和主 / 子码流即可查看视频。</p></li>
    </ol>
    <el-alert v-if="service.host === '127.0.0.1' || service.host === 'localhost'" title="当前为本机监听地址" description="外部设备接入前，需要将 EHome 的监听地址和对外地址配置为设备可达的地址。" type="warning" :closable="false" show-icon />
    <template #footer><el-button type="primary" @click="guide = false">我知道了</el-button></template>
  </el-drawer>

  <el-dialog v-model="preview.visible" class="ehome-dialog" :title="`${preview.device?.name || preview.device?.deviceId || '设备'} · 通道预览`" width="min(1080px, 94vw)" append-to-body destroy-on-close @close="closePreview">
    <div class="preview-layout">
      <aside class="channel-panel" v-loading="preview.loading"><div class="channel-title">设备通道 <span>{{ preview.channels.length }}</span></div><el-alert v-if="preview.channelError" :title="preview.channelError" type="warning" :closable="false" /><button v-for="channel in preview.channels" :key="channel.id" class="channel-item" :class="{ active: preview.channel === channel.id }" @click="selectChannel(channel.id)"><el-icon><VideoCamera /></el-icon>{{ channel.name }}</button><el-button v-if="preview.channelError" text type="primary" @click="loadChannels">重新查询</el-button></aside>
      <main class="preview-main"><div class="preview-controls"><el-radio-group v-model="preview.streamType" @change="stopCurrent"><el-radio-button :value="0">主码流</el-radio-button><el-radio-button :value="1">子码流</el-radio-button></el-radio-group><el-button v-hasPermi="['ehome:device:preview']" type="primary" :icon="VideoPlay" :loading="preview.busy" :disabled="!preview.channel || preview.device?.status !== 'ON' || !service.streamReady" @click="play">{{ preview.url ? '重新播放' : '开始预览' }}</el-button></div><div class="preview-video"><EhomePlayer v-if="preview.url" :key="preview.sessionId" :url="preview.url" /><div v-else class="preview-placeholder"><el-icon><VideoCamera /></el-icon><p>{{ preview.message || '选择通道，开始预览' }}</p></div></div><p class="preview-caption">{{ preview.channel ? `通道 ${preview.channel}` : '尚未选择通道' }} · {{ preview.streamType === 0 ? '主码流' : '子码流' }}<span>{{ preview.device?.devProtocolVersion ? `EHome ${preview.device.devProtocolVersion}` : '' }}</span></p></main>
    </div>
  </el-dialog>
  <el-dialog v-model="edit.visible" class="ehome-dialog" title="编辑设备" width="min(460px, 94vw)" append-to-body><el-form label-position="top" @submit.prevent="saveEdit"><el-form-item label="设备 ID"><el-input :model-value="edit.deviceId" disabled /></el-form-item><el-form-item label="设备名称" required><el-input v-model="edit.name" maxlength="64" show-word-limit /></el-form-item><el-form-item label="备注"><el-input v-model="edit.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item></el-form><template #footer><el-button @click="edit.visible = false">取消</el-button><el-button type="primary" :loading="edit.saving" @click="saveEdit">保存</el-button></template></el-dialog>
</template>

<script setup name="EhomeDevice">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search, Refresh, ArrowRight, VideoCamera, VideoPlay, InfoFilled } from '@element-plus/icons-vue'
import DeviceClassificationLayout from '@/components/DeviceClassificationLayout/index.vue'
import EhomePlayer from './EhomePlayer.vue'
import { listDevices, getStatus, getChannels, updateDevice, startPreview, stopPreview, keepPreview } from '@/api/ehome/device'

const rows = ref([]), total = ref(0), loading = ref(false), loadError = ref(''), selectedKeys = ref([])
const classificationKey = ref(0)
const guide = ref(false), serviceLoaded = ref(false)
const service = reactive({ registrationReady: false, streamReady: false, host: '', message: '' })
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '', devProtocolVersion: '' })
const filtered = computed(() => !!(query.keyword || query.status || query.devProtocolVersion || query.categoryId))
const preview = reactive({ visible: false, device: null, channels: [], channel: null, streamType: 0, loading: false, busy: false, message: '', channelError: '', sessionId: '', url: '' })
const edit = reactive({ visible: false, saving: false, id: null, deviceId: '', name: '', remark: '' })
let listSequence = 0, channelSequence = 0, playSequence = 0, refreshTimer, heartbeatTimer, destroyed = false
async function loadDevices(silent = false) {
  const sequence = ++listSequence
  if (silent !== true) loading.value = true
  try { const result = await listDevices({ ...query }); if (sequence !== listSequence) return; rows.value = result.rows || []; total.value = result.total || 0; loadError.value = '' }
  catch (error) { if (sequence === listSequence) { rows.value = []; total.value = 0; loadError.value = '设备列表加载失败，请检查服务后重试' } }
  finally { if (sequence === listSequence) loading.value = false }
}
async function loadStatus() {
  try { const result = await getStatus(); Object.assign(service, result.data); serviceLoaded.value = true }
  catch { Object.assign(service, { registrationReady: false, streamReady: false, message: '无法获取接入服务状态' }); serviceLoaded.value = true }
}
function refresh(silent = false) { return Promise.all([loadDevices(silent === true), loadStatus()]) }
function search() { query.pageNum = 1; selectedKeys.value = []; loadDevices() }
function resetFilters() { Object.assign(query, { keyword: '', status: '', devProtocolVersion: '', categoryType: undefined, categoryId: undefined, unclassified: undefined }); classificationKey.value++; search() }
function filterClassification(filter) { Object.assign(query, filter); search() }
async function copy(value) { if (!value) return; try { await navigator.clipboard.writeText(String(value)); ElMessage.success('已复制') } catch { ElMessage.info('请手动复制参数') } }
function openEdit(row) { Object.assign(edit, { ...row, visible: true, saving: false, name: row.name || '', remark: row.remark || '' }) }
async function saveEdit() {
  if (!edit.name.trim()) return ElMessage.warning('请输入设备名称')
  edit.saving = true
  try { await updateDevice(edit.id, { name: edit.name.trim(), remark: edit.remark }); edit.visible = false; ElMessage.success('已保存'); loadDevices() }
  finally { edit.saving = false }
}
async function openPreview(row) {
  await stopCurrent()
  Object.assign(preview, { visible: true, device: row, channels: [], channel: null, streamType: 0, message: '', channelError: '' })
  await loadChannels()
}
async function loadChannels() {
  const sequence = ++channelSequence
  preview.loading = true; preview.channelError = ''
  try {
    if (preview.device.status !== 'ON') { preview.channelError = '设备离线，上线后可查询通道'; preview.message = '设备当前离线'; return }
    const result = await getChannels(preview.device.id)
    if (sequence !== channelSequence || !preview.visible) return
    preview.channels = result.data || []; preview.channel = preview.channels[0]?.id || null
    if (!preview.channels.length) preview.channelError = '设备未返回可用通道'
    if (!service.streamReady) preview.message = '取流服务未就绪，请检查服务状态'
  } catch (error) { if (sequence === channelSequence) preview.channelError = error.message || '通道查询失败' }
  finally { if (sequence === channelSequence) preview.loading = false }
}
async function stopCurrent() {
  ++playSequence; clearInterval(heartbeatTimer)
  const id = preview.sessionId
  preview.sessionId = ''; preview.url = ''; preview.busy = false
  if (id) { try { await stopPreview(id) } catch { /* The server lease also expires if the network is unavailable. */ } }
}
async function selectChannel(id) { await stopCurrent(); preview.channel = id; preview.message = '' }
async function play() {
  await stopCurrent()
  const sequence = ++playSequence
  preview.busy = true; preview.message = '正在请求设备视频流…'
  try {
    const result = await startPreview(preview.device.id, { channel: preview.channel, streamType: preview.streamType })
    if (sequence !== playSequence || !preview.visible || destroyed) { await stopPreview(result.data.sessionId); return }
    preview.sessionId = result.data.sessionId; preview.url = result.data.url
    heartbeatTimer = setInterval(async () => {
      const id = preview.sessionId
      if (!id) return
      try { await keepPreview(id) } catch { if (preview.sessionId === id) { await stopCurrent(); preview.message = '视频会话已断开，请重新播放' } }
    }, 20000)
  } catch (error) { if (sequence === playSequence) preview.message = error.message || '取流失败，请重试' }
  finally { if (sequence === playSequence) preview.busy = false }
}
function closePreview() { ++channelSequence; stopCurrent() }
onMounted(() => { refresh(); refreshTimer = setInterval(() => { if (!document.hidden && !preview.visible && !loading.value) refresh(true) }, 15000) })
onBeforeUnmount(() => { destroyed = true; ++listSequence; ++channelSequence; clearInterval(refreshTimer); stopCurrent() })
</script>

<style scoped lang="scss">
:global(.ehome-guide), :global(.ehome-dialog), :global(.ehome-select) {
  background: #fff !important;
  --el-bg-color: #fff; --el-bg-color-overlay: #fff; --el-fill-color-blank: #fff;
  --el-fill-color-light: #f4f7fb; --el-text-color-primary: #33465e; --el-text-color-regular: #61738a;
  --el-text-color-placeholder: #9ba8b9; --el-border-color: #dfe7f1; --el-border-color-light: #e8eef6;
  --el-color-warning-light-9: #fff8eb; --el-dialog-bg-color: #fff; --el-drawer-bg-color: #fff;
}
.ehome-page { padding: 24px; color: #253247; min-width: 0; background: #fff; min-height: 650px;
  --el-bg-color: #fff; --el-bg-color-overlay: #fff; --el-fill-color-blank: #fff; --el-fill-color-light: #f6f8fc;
  --el-text-color-primary: #33465e; --el-text-color-regular: #61738a; --el-text-color-placeholder: #9ba8b9;
  --el-border-color: #dfe7f1; --el-border-color-light: #e8eef6; --el-color-warning-light-9: #fff8eb;
}
.ehome-heading, .heading-title, .connection-strip, .connection-strip > div, .list-heading, .device-filters, .preview-controls { display: flex; align-items: center; }
.ehome-heading { justify-content: space-between; gap: 16px; margin-bottom: 24px; }
.heading-title { gap: 13px; } h1 { font-size: 23px; margin: 0 0 7px; letter-spacing: .2px; } .heading-title p { color: #8793a4; font-size: 12px; margin: 0; line-height: 1.6; }
.protocol-icon { display: grid; place-items: center; width: 50px; height: 50px; flex-shrink: 0; color: #378bfa; background: #edf5ff; border: 1px solid #deebff; border-radius: 14px; font-size: 26px; }
.connection-strip { justify-content: space-between; gap: 10px; flex-wrap: wrap; background: #f6f9fd; border: 1px solid #e8eef6; border-radius: 9px; padding: 12px 16px; font-size: 12px; }
.connection-strip > div { gap: 8px; flex-wrap: wrap; } .connection-strip strong { font-weight: 500; } .service-address { color: #8390a2; margin-left: 8px; font-family: ui-monospace, monospace; }
.status-dot { width: 7px; height: 7px; border-radius: 50%; background: #e6a23c; } .status-dot.online { background: #24b989; box-shadow: 0 0 0 3px #e2f5ee; }
.service-warning { margin-top: 10px; } .list-heading { justify-content: space-between; margin: 26px 0 16px; gap: 10px; } h2 { font-size: 16px; margin: 0; } h2 span { margin-left: 7px; padding: 2px 7px; color: #7190b1; background: #f0f5fa; border-radius: 5px; font-size: 12px; } .family-note { font-size: 11px; color: #98a4b3; letter-spacing: .3px; }
.device-filters { gap: 9px; margin-bottom: 18px; flex-wrap: wrap; } .device-filters > .el-input { flex: 1 1 210px; min-width: 190px; } .device-filters > .el-select { width: 115px; } .device-filters > .el-button { margin: 0; }
.ehome-table { --el-table-header-bg-color: #f7f9fc; --el-table-header-text-color: #7b899b; --el-table-border-color: #eef2f7; --el-table-row-hover-bg-color: #f6faff; --el-table-bg-color: #fff; --el-table-tr-bg-color: #fff; --el-table-text-color: #61738a; font-size: 12px; }
.ehome-table :deep(.el-table__empty-block) { display: none; }
.ehome-table :deep(th.el-table__cell) { background: #f7f9fc !important; color: #7b899b; }
.ehome-table :deep(td.el-table__cell) { background: #fff; }
.empty-state { text-align: center; }
.ehome-table :deep(th.el-table__cell) { height: 43px; font-weight: 500; } .ehome-table :deep(td.el-table__cell) { padding: 15px 0; } .device-name { font-weight: 500; color: #33465e; margin-bottom: 4px; } .device-id { font-size: 11px; color: #97a3b3; } .version-label { border-radius: 4px; background: #f1f5fc; color: #6584aa; padding: 4px 6px; font-size: 11px; }
.device-status { display: inline-flex; align-items: center; gap: 5px; color: #909aa7; } .device-status i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; } .device-status.online { color: #21a878; }
.empty-state { padding: 55px 15px 40px; line-height: normal; } .empty-icon { width: 72px; height: 72px; margin: 0 auto 21px; position: relative; display: grid; place-items: center; background: #f0f6ff; color: #92b8eb; border-radius: 22px; font-size: 35px; } .empty-icon span { position: absolute; right: -4px; bottom: -2px; display: grid; place-items: center; height: 24px; width: 24px; border: 3px solid white; border-radius: 50%; background: #509bfb; color: white; font-size: 18px; } .empty-state h3 { color: #536781; font-size: 16px; font-weight: 500; margin: 0 0 12px; } .empty-state p { font-size: 12px; color: #97a4b5; margin-bottom: 22px; }
.empty-steps { display: flex; justify-content: center; align-items: center; margin-top: 39px; gap: 13px; font-size: 11px; color: #a1adbb; } .empty-steps b { display: inline-grid; place-items: center; width: 19px; height: 19px; background: #f2f5f9; border-radius: 50%; margin-right: 3px; font-weight: 400; } .empty-steps i { height: 1px; width: 24px; background: #e6edf5; }
.ehome-footnote { margin-top: 24px; display: flex; align-items: center; gap: 6px; font-size: 11px; color: #9aa6b5; line-height: 1.7; }
.guide-intro { border-radius: 12px; background: #f2f7ff; padding: 24px; margin-bottom: 27px; } .guide-intro > span { color: #418ff5; font-size: 12px; font-weight: 600; } .guide-intro h2 { margin: 12px 0; font-size: 22px; color: #2d4667; } .guide-intro p, .guide-steps p { font-size: 13px; color: #8593a5; line-height: 1.9; }
.guide-steps { padding-left: 26px; margin: 0 0 26px; } .guide-steps li { padding-left: 8px; margin-bottom: 25px; color: #4a8ddd; } .guide-steps h3 { color: #33465e; font-size: 15px; font-weight: 500; } .guide-values { border: 1px solid #e7edf6; border-radius: 8px; padding: 5px 13px; } .guide-values > div { display: flex; align-items: center; gap: 10px; padding: 9px 0; font-size: 12px; } .guide-values span { color: #8a98a9; width: 73px; } .guide-values strong { color: #4d627e; font-weight: 500; overflow-wrap: anywhere; flex: 1; }
.preview-layout { display: flex; min-height: 430px; gap: 18px; } .channel-panel { width: 190px; flex-shrink: 0; padding: 14px; background: #f6f8fc; border-radius: 9px; overflow-y: auto; max-height: 500px; } .channel-title { font-size: 13px; color: #6f8198; margin-bottom: 18px; } .channel-title span { float: right; } .channel-item { width: 100%; display: flex; align-items: center; gap: 9px; background: transparent; border: 0; border-radius: 6px; padding: 12px; color: #6f8198; cursor: pointer; text-align: left; } .channel-item.active { color: #398ef4; background: #e7f0ff; } .preview-main { flex: 1; min-width: 0; } .preview-controls { justify-content: space-between; gap: 12px; margin-bottom: 14px; } .preview-video { aspect-ratio: 16/9; min-height: 340px; border-radius: 9px; background: #101c2d; overflow: hidden; } .preview-placeholder { display: flex; height: 100%; min-height: 340px; flex-direction: column; align-items: center; justify-content: center; color: #9dacc0; font-size: 13px; padding: 24px; text-align: center; } .preview-placeholder .el-icon { font-size: 39px; color: #4c6380; } .preview-caption { font-size: 12px; color: #8896a8; } .preview-caption span { float: right; }
@media (max-width: 1100px) { .ehome-page { padding: 18px; } .ehome-heading { align-items: flex-start; } .protocol-icon { display: none; } .heading-title p { max-width: 260px; } }
@media (max-width: 700px) { .preview-layout { flex-direction: column; } .channel-panel { width: 100%; max-height: 150px; } .empty-steps { gap: 7px; } .family-note { display: none; } }
</style>
