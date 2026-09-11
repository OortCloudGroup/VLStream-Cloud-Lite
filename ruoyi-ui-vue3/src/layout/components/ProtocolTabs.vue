<template>
  <div v-if="tabState" class="protocol-tabs-wrap" :class="{ 'is-embedded': embedded }">
    <el-tabs
      :model-value="tabState.active"
      class="protocol-tabs"
      @tab-click="handleTabClick"
    >
      <el-tab-pane
        v-for="tab in tabState.tabs"
        :key="tab.path"
        :label="tab.title"
        :name="tab.path"
      />
    </el-tabs>
  </div>
</template>

<script setup>
import { matchProtocolTabsByRoute } from '@/utils/menuGroups'
import usePermissionStore from '@/store/modules/permission'

defineProps({
  /** 左右分栏右侧内嵌时去掉外层 padding */
  embedded: {
    type: Boolean,
    default: false
  }
})

const route = useRoute()
const router = useRouter()
const permissionStore = usePermissionStore()

const tabState = computed(() => {
  if (permissionStore.currentGroup !== 'video') return null
  return matchProtocolTabsByRoute(route, permissionStore.menuSourceRoutes)
})

function handleTabClick(pane) {
  const path = pane?.paneName || pane?.props?.name
  if (!path || path === route.path) return
  router.push(path).catch(() => {})
}
</script>

<style lang="scss" scoped>
.protocol-tabs-wrap {
  flex-shrink: 0;
  padding: 16px 20px 0;
  background: #fff;

  &.is-embedded {
    padding: 0;
    margin-bottom: 12px;
    background: transparent;
  }
}

.protocol-tabs {
  --el-tabs-header-height: 32px;

  :deep(.el-tabs__header) {
    margin: 0;
    border-bottom: 1px solid #f0f0f0;
  }

  :deep(.el-tabs__nav-wrap::after) {
    display: none;
  }

  :deep(.el-tabs__item) {
    height: 32px;
    padding: 0 16px;
    line-height: 32px;
    text-align: center;
    color: #333;
    font-size: 14px;
    font-weight: 400;
    box-sizing: border-box;
  }

  :deep(.el-tabs__item.is-active) {
    color: var(--el-color-primary);
  }

  :deep(.el-tabs__item:hover) {
    color: var(--el-color-primary);
  }

  :deep(.el-tabs__active-bar) {
    height: 2px;
    background-color: var(--el-color-primary);
  }

  :deep(.el-tabs__content) {
    display: none;
  }
}
</style>
