<template>
  <div :class="{ 'has-logo': showLogo }" class="sidebar-container">
    <logo v-if="showLogo" :collapse="isCollapse" />
    <el-scrollbar wrap-class="scrollbar-wrapper">
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        background-color="transparent"
        text-color="rgba(0, 0, 0, 0.65)"
        :unique-opened="true"
        :active-text-color="theme"
        :collapse-transition="false"
        mode="vertical"
        class="sidebar-el-menu"
        :class="sideTheme"
      >
        <sidebar-item
          v-for="(route, index) in sidebarRouters"
          :key="route.path + index"
          :item="route"
          :base-path="route.path"
        />
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup>
import Logo from './Logo'
import SidebarItem from './SidebarItem'
import useAppStore from '@/store/modules/app'
import useSettingsStore from '@/store/modules/settings'
import usePermissionStore from '@/store/modules/permission'

const route = useRoute();
const appStore = useAppStore()
const settingsStore = useSettingsStore()
const permissionStore = usePermissionStore()

const sidebarRouters = computed(() => permissionStore.sidebarRouters);
const showLogo = computed(() => settingsStore.sidebarLogo);
const sideTheme = computed(() => settingsStore.sideTheme);
const theme = computed(() => settingsStore.theme);
const isCollapse = computed(() => !appStore.sidebar.opened);

const activeMenu = computed(() => {
  const { meta, path } = route;
  if (meta.activeMenu) {
    return meta.activeMenu;
  }
  return path;
});
</script>

<style lang="scss" scoped>
.sidebar-container {
  background-color: transparent !important;
  border-radius: 0;
  box-shadow: none;
  padding: 0;

  .scrollbar-wrapper,
  :deep(.el-scrollbar),
  :deep(.el-scrollbar__view) {
    background-color: transparent !important;
  }

  .sidebar-el-menu.el-menu {
    border: none !important;
    border-right: 0 !important;
    height: 100%;
    width: 100% !important;
    background-color: transparent !important;
    --el-menu-bg-color: transparent;
    --el-menu-hover-bg-color: var(--el-color-primary-light-9, rgba(64, 158, 255, 0.1));
    --el-menu-text-color: rgba(0, 0, 0, 0.65);
    --el-menu-active-color: var(--el-color-primary);
  }
}
</style>
