<template>
  <div class="product-nav">
    <div
      v-for="tab in PRODUCT_TABS"
      :key="tab.key"
      class="product-nav__item"
      :class="{ 'is-active': activeGroup === tab.key }"
      @click="handleSelect(tab.key)"
    >
      {{ translateRouteTitle(tab.title) }}
    </div>
  </div>
</template>

<script setup>
import { PRODUCT_TABS, buildSidebarByGroup, findFirstMenuPath, resolveGroupByRoute } from '@/utils/menuGroups'
import usePermissionStore from '@/store/modules/permission'
import useAppStore from '@/store/modules/app'
import { translateRouteTitle } from '@/i18n'

const route = useRoute()
const router = useRouter()
const permissionStore = usePermissionStore()
const appStore = useAppStore()

const activeGroup = computed(() => permissionStore.currentGroup || 'workbench')

function applyGroup(groupKey, { navigate } = { navigate: false }) {
  const sameGroup = groupKey === permissionStore.currentGroup
  // 同组内路由跳转不重建侧栏，避免左侧菜单闪一下
  if (!sameGroup || !permissionStore.sidebarRouters.length) {
    const routes = buildSidebarByGroup(permissionStore.menuSourceRoutes, groupKey)
    permissionStore.setCurrentGroup(groupKey)
    permissionStore.setSidebarRouters(routes)
    appStore.toggleSideBarHide(routes.length === 0)
  }

  if (navigate) {
    const routes = permissionStore.sidebarRouters
    const first = findFirstMenuPath(routes)
    if (first && normalizeCompare(first) !== normalizeCompare(route.path)) {
      router.push(first).catch(() => {})
    }
  }
}

function normalizeCompare(p) {
  if (!p) return ''
  const s = ('/' + p).replace(/\/+/g, '/')
  return s.length > 1 && s.endsWith('/') ? s.slice(0, -1) : s
}

function handleSelect(groupKey) {
  if (groupKey === activeGroup.value) {
    applyGroup(groupKey, { navigate: false })
    return
  }
  applyGroup(groupKey, { navigate: true })
}

function syncFromRoute() {
  if (!permissionStore.menuSourceRoutes.length) return
  const group = resolveGroupByRoute(route)
  applyGroup(group, { navigate: false })
}

watch(() => [route.path, route.meta?.activeMenu], () => {
  syncFromRoute()
})

watch(() => permissionStore.menuSourceRoutes.length, (len) => {
  if (len > 0) syncFromRoute()
}, { immediate: true })

defineExpose({ applyGroup, syncFromRoute })
</script>

<style lang="scss" scoped>
.product-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 64px;
  flex: 1;
  min-width: 0;
  gap: 0;
}

.product-nav__item {
  position: relative;
  height: 64px;
  line-height: 64px;
  padding: 0 clamp(8px, 1.4vw, 20px);
  font-size: clamp(14px, 1.25vw, 18px);
  color: var(--top-nav-text);
  cursor: pointer;
  user-select: none;
  white-space: nowrap;
  transition: color 0.2s;
  background: transparent;

  &:hover {
    color: var(--el-color-primary);
    background-color: transparent;
  }

  &.is-active {
    color: var(--el-color-primary);
    font-weight: 700;
    background: transparent;

    &::after {
      content: "";
      position: absolute;
      bottom: 0;
      left: 50%;
      width: 24px;
      height: 3px;
      background: var(--el-color-primary);
      transform: translateX(-50%);
    }
  }
}

@media (max-width: 680px) {
  .product-nav__item {
    padding-inline: 6px;
    font-size: 13px;
  }
}
</style>
