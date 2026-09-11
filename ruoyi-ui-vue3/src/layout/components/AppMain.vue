<template>
  <section class="app-main" :class="{ 'has-protocol-tabs': showProtocolTabs }">
    <protocol-tabs v-if="showProtocolTabs" />
    <div class="app-main__body">
      <router-view v-slot="{ Component, route }">
        <transition name="fade-transform" mode="out-in">
          <keep-alive :include="tagsViewStore.cachedViews">
            <component v-if="!route.meta.link" :is="Component" :key="route.path"/>
          </keep-alive>
        </transition>
      </router-view>
      <iframe-toggle />
    </div>
  </section>
</template>

<script setup>
import iframeToggle from "./IframeToggle/index"
import ProtocolTabs from "./ProtocolTabs.vue"
import useTagsViewStore from '@/store/modules/tagsView'
import usePermissionStore from '@/store/modules/permission'
import { isProtocolTreeLayoutRoute, matchProtocolTabsByRoute } from '@/utils/menuGroups'

const route = useRoute()
const tagsViewStore = useTagsViewStore()
const permissionStore = usePermissionStore()

/** 无左侧树的国际协议页：顶栏 Tab；有树页由页面内右侧嵌入 */
const showProtocolTabs = computed(() => {
  if (permissionStore.currentGroup !== 'video') return false
  if (isProtocolTreeLayoutRoute(route)) return false
  return !!matchProtocolTabsByRoute(route, permissionStore.menuSourceRoutes)
})

onMounted(() => {
  addIframe()
})

watch(() => route, () => {
  addIframe()
})

function addIframe() {
  if (route.meta.link) {
    useTagsViewStore().addIframeView(route)
  }
}
</script>

<style lang="scss" scoped>
.app-main {
  flex: 1;
  min-height: 0;
  width: 100%;
  position: relative;
  overflow: hidden;
  background: #ffffff;
  border-radius: 10px 10px 0 0;
  display: flex;
  flex-direction: column;
}

.app-main__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.app-main.has-protocol-tabs {
  border-radius: 10px 10px 0 0;
}
</style>

<style lang="scss">
.el-popup-parent--hidden {
  .fixed-header {
    padding-right: 6px;
  }
}

::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background-color: #f1f1f1;
}

::-webkit-scrollbar-thumb {
  background-color: #c0c0c0;
  border-radius: 3px;
}
</style>
