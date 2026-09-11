<template>
  <div :class="classObj" class="app-wrapper" :style="{ '--current-color': theme }">
    <div v-if="device === 'mobile' && sidebar.opened" class="drawer-bg" @click="handleClickOutside" />

    <div class="layout-header">
      <navbar @setLayout="setLayout" />
    </div>

    <div class="layout-body">
      <sidebar v-if="!sidebar.hide" class="sidebar-container" />
      <div :class="{ sidebarHide: sidebar.hide }" class="main-container">
        <app-main />
        <settings ref="settingRef" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { useWindowSize } from '@vueuse/core'
import Sidebar from './components/Sidebar/index.vue'
import { AppMain, Navbar, Settings } from './components'

import useAppStore from '@/store/modules/app'
import useSettingsStore from '@/store/modules/settings'

const settingsStore = useSettingsStore()
const theme = computed(() => settingsStore.theme);
const sidebar = computed(() => useAppStore().sidebar);
const device = computed(() => useAppStore().device);

const classObj = computed(() => ({
  hideSidebar: !sidebar.value.opened,
  openSidebar: sidebar.value.opened,
  withoutAnimation: sidebar.value.withoutAnimation,
  mobile: device.value === 'mobile'
}))

const { width } = useWindowSize();
const WIDTH = 992;

watch(() => device.value, () => {
  if (device.value === 'mobile' && sidebar.value.opened) {
    useAppStore().closeSideBar({ withoutAnimation: false })
  }
})

watchEffect(() => {
  if (width.value - 1 < WIDTH) {
    useAppStore().toggleDevice('mobile')
    useAppStore().closeSideBar({ withoutAnimation: true })
  } else {
    useAppStore().toggleDevice('desktop')
  }
})

function handleClickOutside() {
  useAppStore().closeSideBar({ withoutAnimation: false })
}

const settingRef = ref(null);
function setLayout() {
  settingRef.value.openSetting();
}
</script>

<style lang="scss" scoped>
@import "@/assets/styles/mixin.scss";
@import "@/assets/styles/variables.module.scss";

.app-wrapper {
  @include clearfix;
  position: relative;
  height: 100%;
  width: 100%;
  background: #f0f2f5;
  display: flex;
  flex-direction: column;

  &.mobile.openSidebar {
    position: fixed;
    top: 0;
  }
}

.layout-header {
  flex-shrink: 0;
  width: 100%;
  z-index: 1002;
  background: transparent;
}

.layout-body {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: flex-start;
  width: 100%;
  position: relative;
  gap: 20px;
  padding: 0 20px 0 0;
  box-sizing: border-box;
}

.drawer-bg {
  background: #000;
  opacity: 0.3;
  width: 100%;
  top: 0;
  height: 100%;
  position: absolute;
  z-index: 999;
}

.main-container {
  flex: 1;
  min-width: 0;
  background: transparent;
  padding: 0;
  margin: 20px 0 0 0 !important;
  height: calc(100% - 20px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
</style>
