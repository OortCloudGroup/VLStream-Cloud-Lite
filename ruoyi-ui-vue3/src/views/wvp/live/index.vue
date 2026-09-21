<template>
  <div class="app-container live-page">
    <div class="workbench-layout">
      <aside v-yResize class="workbench-aside">
        <div class="aside-title">{{ $tp("设备列表") }}</div>
        <div class="head-container">
          <el-input
              v-model="deviceName"
              :placeholder="$tp('搜索设备名称')"
              clearable
              prefix-icon="Search"
              style="margin-bottom: 12px"
          />
        </div>
        <div class="top">
          <div>{{ $tp("通道列表") }}</div>
          <div>
            <el-switch
                v-model="activeValue"
                :active-text="$tp('行政区划')"
                :inactive-text="$tp('业务分组')"
                @change="onSwitch"
            />
          </div>
        </div>
        <div class="tree">
          <el-tree
              ref="deviceTreeRef"
              :data="treeData"
              :props="defaultProps"
              lazy
              :load="loadNode"
              :filter-node-method="filterNode"
              @node-click="handleNodeClick"
          />
        </div>
      </aside>

      <main class="workbench-main">
        <ProtocolTabs embedded />
        <div class="workbench-toolbar">
          <svg-icon :class="['flex-icon', { active: model === 1 }]" icon-class="screen1" @click="spiltIndex(1)" />
          <svg-icon :class="['flex-icon', { active: model === 4 }]" icon-class="screen4" @click="spiltIndex(4)" />
          <svg-icon :class="['flex-icon', { active: model === 6 }]" icon-class="screen6" @click="spiltIndex(6)" />
          <svg-icon :class="['flex-icon', { active: model === 9 }]" icon-class="screen9" @click="spiltIndex(9)" />
          <el-dropdown trigger="click" @command="handleScreenMore">
            <span class="flex-icon more-trigger">
              <svg-icon icon-class="screen-more" />
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="8">
                  <svg-icon icon-class="screen8" class="dropdown-screen-icon" /> {{ $tp("八画面") }}
                </el-dropdown-item>
                <el-dropdown-item command="16">
                  <svg-icon icon-class="screen16" class="dropdown-screen-icon" /> {{ $tp("十六画面") }}
                </el-dropdown-item>
                <el-dropdown-item command="17">
                  <svg-icon icon-class="screen17" class="dropdown-screen-icon" /> {{ $tp("十七画面") }}
                </el-dropdown-item>
                <el-dropdown-item command="21">
                  <svg-icon icon-class="screen21" class="dropdown-screen-icon" /> {{ $tp("二十一画面") }}
                </el-dropdown-item>
                <el-dropdown-item command="23">
                  <svg-icon icon-class="screen23" class="dropdown-screen-icon" /> {{ $tp("二十三画面") }}
                </el-dropdown-item>
                <el-dropdown-item command="24">
                  <svg-icon icon-class="screen24" class="dropdown-screen-icon" /> {{ $tp("二十四画面") }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button size="small" @click="handleCustomScreen">{{ $tp("自定义") }}</el-button>
          <svg-icon class="flex-icon" icon-class="screen-full" @click="toggleLiveFullscreen" />
        </div>

        <div class="workbench-players" ref="livePlayersRef">
          <div class="players-grid" :style="gridContainerStyle">
            <div
                v-for="(item, index) in currentCells"
                :key="`${splitShow}-${index}`"
                :style="getCellStyle(index)"
                :class="['player-cell', { active: activePlayerIndex === index }]"
                @click="setActivePlayer(index)"
            >
              <CusPlayer :ref="'video' + index" />
            </div>
          </div>
        </div>
      </main>
    </div>

    <el-dialog v-model="customDialogVisible" :title="$tp('自定义视图')" width="26%" append-to-body destroy-on-close>
      <div class="custom-view-form">
        <div class="custom-view-field">
          <div class="custom-view-label">{{ $tp("行(输入值1-9)") }}</div>
          <el-input
              v-model="customRows"
              maxlength="1"
              inputmode="numeric"
              @input="onCustomNumInput('rows', $event)"
          />
        </div>
        <span class="custom-view-x">x</span>
        <div class="custom-view-field">
          <div class="custom-view-label">{{ $tp("列(输入值1-9)") }}</div>
          <el-input
              v-model="customCols"
              maxlength="1"
              inputmode="numeric"
              @input="onCustomNumInput('cols', $event)"
          />
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="confirmCustomScreen">{{ $tp("确定") }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="WVPLive">
import {queryForTree} from "@/api/wvp/region";
import {queryListByCivilCode, queryListByParentId, sendDevicePush} from "@/api/wvp/channel.js";
import {queryForTree as groupQueryForTree} from "@/api/wvp/group.js";
import CusPlayer from "@/components/flv/CusPlayer.vue";
import ProtocolTabs from "@/layout/components/ProtocolTabs.vue";
import { start as playPush} from "@/api/wvp/push.js";
import { start as playProxy } from "@/api/wvp/proxy.js";
import {ElMessage} from "element-plus";
import { getSplitLayout, getCustomEqualLayout } from "@/views/work/splitScreenLayouts.js";

const {proxy} = getCurrentInstance();

const queryParams = ref({
  pageNum: 1,
  pageSize: 200,
})

const video = ref(null);
const deviceName = ref('');
const deviceTreeRef = ref(null);
const livePlayersRef = ref(null);

const treeData = ref([]);

const defaultProps = {
  children: 'children',
  label: 'name',
  isLeaf: 'leaf'
};

const splitShow = ref(4)
const borderWidth = ref(2)
const activePlayerIndex = ref(null);
const model = ref(4);
const activeValue = ref(true);
const customDialogVisible = ref(false)
const customRows = ref('1')
const customCols = ref('1')
const customLayout = ref(null)

const currentLayout = computed(() => {
  if (splitShow.value === 'custom' && customLayout.value) {
    return customLayout.value
  }
  return getSplitLayout(splitShow.value)
})
const currentCells = computed(() => currentLayout.value.cells)
const gridContainerStyle = computed(() => ({
  display: 'grid',
  gridTemplateColumns: currentLayout.value.columns,
  gridTemplateRows: currentLayout.value.rows,
  gap: '2px',
  width: '100%',
  height: '100%',
  minHeight: '640px'
}))

watch(deviceName, (val) => {
  deviceTreeRef.value?.filter(val)
})

function filterNode(value, data) {
  if (!value) return true
  return String(data.name || '').includes(value)
}

async function onSwitch(e) {
  if (activeValue.value) {
    await getTreeData();
  } else {
    await getGroupQueryForTree();
  }
}

const loadNode = async (node, resolve) => {
  if (node.level === 0) {
    return resolve(treeData.value);
  } else if (node.level === 1) {
    return resolve(treeData.value[node.level - 1].children);
  } else if (node.level === 2) {
    if (activeValue.value) {
      queryParams.value.civilCode = node.data.deviceId;
      const response = await queryListByCivilCode(queryParams.value);
      const children = response.rows.map(item => ({
        ...item,
        leaf: true,
        name: item.gbName
      }));
      resolve(children);
    } else {
      queryParams.value.groupDeviceId = node.data.deviceId;
      const response = await queryListByParentId(queryParams.value);
      const children = response.rows.map(item => ({
        ...item,
        leaf: true,
        name: item.gbName
      }));
      resolve(children);
    }
  }
};

const handleNodeClick = async (data) => {
  if(activePlayerIndex.value == null){
    proxy.$modal.msgError(translatePhrase("请先选择一个播放窗口"));
    return
  }

  if(data.dataType === 1){
    if (data.gbDeviceId && data.gbParentId) {
      const params = {
        deviceId: data.gbParentId,
        channelId: data.gbDeviceId
      }
      const res = await sendDevicePush(params);

      const videoRef = proxy.$refs[`video${activePlayerIndex.value}`];
      if (videoRef && videoRef[0]) {
        if (location.protocol === "https:") {
          videoRef[0].createPlayer(res.data.https_flv, 0);
        } else {
          videoRef[0].createPlayer(res.data.flv, 0);
        }
      } else {
        proxy.$modal.msgError(translatePhrase("请选择播放器"));
      }
    } else {
      proxy.$modal.msgError(translatePhrase("通道或设备不存在"))
    }
  }

  if(data.dataType === 2) {
    const ans = await playPush({id: data.dataDeviceId});
    const videoRef = proxy.$refs[`video${activePlayerIndex.value}`];
    if (videoRef && videoRef[0]) {
      if (location.protocol === "https:") {
        videoRef[0].createPlayer(ans.data.https_flv, 0);
      } else {
        videoRef[0].createPlayer(ans.data.flv, 0);
      }
    } else {
      proxy.$modal.msgError(translatePhrase("请选择播放器"));
    }
  }

  if(data.dataType === 3) {
    const ans = await playProxy({id: data.dataDeviceId});
    const videoRef = proxy.$refs[`video${activePlayerIndex.value}`];
    if (videoRef && videoRef[0]) {
      if (location.protocol === "https:"){
        videoRef[0].createPlayer(ans.https_flv, 0);
      } else {
        videoRef[0].createPlayer(ans.flv, 0);
      }
    } else {
      proxy.$modal.msgError(translatePhrase("请选择播放器"));
    }
  }

};

function getCellStyle(index) {
  const cell = currentLayout.value.cells[index]
  if (!cell) return {}
  return {
    gridColumn: cell.column,
    gridRow: cell.row,
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#000000',
    boxSizing: 'border-box',
    border: `${borderWidth.value}px solid transparent`,
    minHeight: 0,
    overflow: 'hidden'
  }
}

function setActivePlayer(index) {
  activePlayerIndex.value = index;
}

async function getTreeData() {
  const res = await queryForTree();
  let data = [
    {
      get name() { return translatePhrase("根资源组") },
      children: []
    }
  ]
  data[0].children = proxy.handleTree(res.data, "id")
  treeData.value = data;
}

async function getGroupQueryForTree() {
  const res = await groupQueryForTree();
  let data = [
    {
      get name() { return translatePhrase("根资源组") },
      children: []
    }
  ]
  data[0].children = proxy.handleTree(res.data, "id")
  treeData.value = data;
}

function spiltIndex(index){
  const key = Number(index)
  customLayout.value = null
  splitShow.value = key;
  model.value = key;
  activePlayerIndex.value = null;
}

function handleScreenMore(command) {
  spiltIndex(command)
}

function handleCustomScreen() {
  customRows.value = '1'
  customCols.value = '1'
  customDialogVisible.value = true
}

function onCustomNumInput(which, val) {
  const raw = String(val ?? '').replace(/\D/g, '').slice(0, 1)
  if (which === 'rows') {
    customRows.value = raw
  } else {
    customCols.value = raw
  }
}

function confirmCustomScreen() {
  const rows = Number(customRows.value)
  const cols = Number(customCols.value)
  if (!Number.isInteger(rows) || !Number.isInteger(cols) || rows < 1 || rows > 9 || cols < 1 || cols > 9) {
    ElMessage.warning(translatePhrase("行和列请输入 1-9 的整数"))
    return
  }
  customLayout.value = getCustomEqualLayout(rows, cols)
  splitShow.value = 'custom'
  model.value = 'custom'
  activePlayerIndex.value = null
  customDialogVisible.value = false
}

function toggleLiveFullscreen() {
  const el = livePlayersRef.value
  if (!el) return
  if (!document.fullscreenElement) {
    el.requestFullscreen?.()
  } else {
    document.exitFullscreen?.()
  }
}

onMounted(async () => {
  await getTreeData();
});

</script>

<style scoped>
.app-container.live-page {
  background: #fff;
  border-radius: 10px;
  min-height: calc(100vh - 84px);
  box-sizing: border-box;
  padding: 16px 20px;
}

.workbench-layout {
  display: flex;
  align-items: stretch;
  gap: 20px;
  min-height: calc(100vh - 120px);
  background: #fff;
}

.workbench-aside {
  width: 300px;
  flex-shrink: 0;
  padding: 0 20px 0 0;
  margin: 0;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  border-radius: 0;
  line-height: normal;
  font-size: inherit;
  color: inherit;
}

.aside-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  padding-left: 8px;
  border-left: 3px solid var(--el-color-primary);
  line-height: 1;
  margin-bottom: 12px;
}

.top {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.tree {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.workbench-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.workbench-toolbar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-shrink: 0;
}

.workbench-players {
  flex: 1;
  min-height: 640px;
  background: #f5f5f5;
  border-radius: 4px;
  overflow: hidden;
}

.players-grid {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 640px;
  background: #000;
}

.player-cell {
  position: relative;
  transition: border-color 0.3s ease;
  overflow: hidden;
}

.player-cell:hover {
  cursor: pointer;
}

.player-cell.active {
  border-color: var(--el-color-primary) !important;
  z-index: 2;
}

.flex-icon {
  cursor: pointer;
  width: 24px;
  height: 24px;
  font-size: 24px;
  transition: color 0.3s ease, transform 0.3s ease;
  color: #666;
}

.flex-icon.active {
  color: var(--el-color-primary);
  transform: scale(1.1);
}

.workbench-toolbar .flex-icon,
.workbench-toolbar .svg-icon,
.workbench-toolbar .more-trigger .svg-icon {
  width: 24px;
  height: 24px;
  font-size: 24px;
}

.more-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
}

.dropdown-screen-icon {
  margin-right: 8px;
  font-size: 16px;
  vertical-align: middle;
}

.custom-view-form {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 16px;
  padding: 8px 0 16px;
}

.custom-view-field {
  width: 120px;
}

.custom-view-label {
  font-size: 13px;
  color: #666;
  margin-bottom: 8px;
  white-space: nowrap;
}

.custom-view-x {
  padding-bottom: 8px;
  font-size: 14px;
  color: #333;
}
</style>
