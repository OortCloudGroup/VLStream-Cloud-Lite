<template>
  <div class="app-container">
    <el-row :gutter="20">
      <el-col :xs="24" :sm="24" :md="12" :lg="8">
        <el-card class="dashboard-card">
          <template #header>
            <div class="card-header">
              <span>设备总览</span>
            </div>
          </template>
          <LeftTop/>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="12" :lg="8">
        <el-card class="dashboard-card">
          <template #header>
            <div class="card-header">
              <span>国标统计</span>
            </div>
          </template>
          <TopCenter/>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="12" :lg="8">
        <el-card class="dashboard-card">
          <template #header>
            <div class="card-header">
              <span>CPU</span>
            </div>
          </template>
          <RightTop ref="RightTopRef"/>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :xs="24" :sm="24" :md="12" :lg="8">
        <el-card class="dashboard-card">
          <template #header>
            <div class="card-header">
              <span>网络</span>
            </div>
          </template>
          <LeftBottom ref="LeftBottomRef"/>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="12" :lg="8">
        <el-card class="dashboard-card">
          <template #header>
            <div class="card-header">
              <span>内存</span>
            </div>
          </template>
          <BottomCenter ref="BottomCenterRef"/>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="12" :lg="8">
        <el-card class="dashboard-card">
          <template #header>
            <div class="card-header">
              <span>磁盘</span>
            </div>
          </template>
          <RightBottom ref="RightBottomRef"/>
        </el-card>
      </el-col>
    </el-row>
    <ReliabilityGuide />
  </div>
</template>

<script setup name="Index">
import LeftTop from "./components/home/left-top.vue"
import TopCenter from "./components/home/top-center.vue"
import RightTop from "./components/home/right-top.vue"
import LeftBottom from "./components/home/left-bottom.vue"
import BottomCenter from "./components/home/bottom-center.vue"
import RightBottom from "./components/home/right-bottom.vue"
import ReliabilityGuide from "./components/home/ReliabilityGuide.vue"
import {serverInfo} from "../api/index.js";

const timer = ref(null)
let active = false
let loading = false
const RightTopRef = ref(null)
const LeftBottomRef = ref(null)
const BottomCenterRef = ref(null)
const RightBottomRef = ref(null)

function startPolling() {
  if (active) return
  active = true
  nextTick(serverInfoFun)
}

function stopPolling() {
  active = false
  clearTimeout(timer.value)
}

onMounted(startPolling)
onActivated(startPolling)
onDeactivated(stopPolling)

function serverInfoFun(){
  if (!active || loading) return
  loading = true
  let delay = 2000
  serverInfo()
      .then((res) => {
        if (!active) return
        RightTopRef.value?.setData(res.data.cpu)
        LeftBottomRef.value?.setData(res.data.net)
        BottomCenterRef.value?.setData(res.data.mem)
        RightBottomRef.value?.setData(res.data.disk)
      })
      .catch(() => { delay = 15000 })
      .finally(() => {
        loading = false
        if (active) timer.value = setTimeout(serverInfoFun, delay)
      });
}
onBeforeUnmount(stopPolling)
</script>

<style scoped lang="scss">
:deep(.dashboard-card) {
  box-shadow: none;
  border-radius: 12px;
  margin-bottom: 12px;
}
</style>

