<template>
  <div class="navbar">
    <div class="navbar-left" :class="{ 'is-collapse': !appStore.sidebar.opened }">
      <router-link to="/" class="brand" :class="{ 'is-collapse': !appStore.sidebar.opened }">
        <img class="brand-logo" :src="brandLogo" alt="VLStream" />
      </router-link>
    </div>
    <hamburger
      id="hamburger-container"
      :is-active="appStore.sidebar.opened"
      class="hamburger-container"
      @toggleClick="toggleSideBar"
    />

    <product-nav class="navbar-center" />

    <div class="right-menu">
      <language-switch class="right-menu-item" />
      <div class="avatar-container">
        <el-dropdown @command="handleCommand" class="right-menu-item hover-effect" trigger="click">
          <div class="avatar-wrapper">
            <img :src="userStore.avatar" class="user-avatar" />
            <span v-if="userStore.name" class="user-name">{{ userStore.name }}</span>
            <el-icon class="caret-icon"><caret-bottom /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <router-link to="/user/profile">
                <el-dropdown-item>{{ $t('account.profile') }}</el-dropdown-item>
              </router-link>
              <el-dropdown-item command="setLayout" v-if="settingsStore.showSettings">
                <span>{{ $t('account.layout') }}</span>
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <span>{{ $t('account.logout') }}</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ElMessageBox } from 'element-plus'
import Hamburger from '@/components/Hamburger'
import ProductNav from '@/components/ProductNav/index.vue'
import LanguageSwitch from '@/components/LanguageSwitch/index.vue'
import brandLogo from '@/assets/logo/vls-brand.png'
import useAppStore from '@/store/modules/app'
import useUserStore from '@/store/modules/user'
import useSettingsStore from '@/store/modules/settings'
import { useI18n } from 'vue-i18n'

const appStore = useAppStore()
const userStore = useUserStore()
const settingsStore = useSettingsStore()
const { t } = useI18n()

function toggleSideBar() {
  appStore.toggleSideBar()
}

function handleCommand(command) {
  switch (command) {
    case "setLayout":
      setLayout();
      break;
    case "logout":
      logout();
      break;
    default:
      break;
  }
}

function logout() {
  ElMessageBox.confirm(t('account.logoutConfirm'), t('common.prompt'), {
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel'),
    type: 'warning'
  }).then(() => {
    userStore.logOut().then(() => {
      location.href = '/index';
    })
  }).catch(() => { });
}

const emits = defineEmits(['setLayout'])
function setLayout() {
  emits('setLayout');
}
</script>

<style lang='scss' scoped>
.navbar {
  height: 64px;
  min-height: 64px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  background: var(--navbar-bg);
  color: var(--navbar-text);
  box-sizing: border-box;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.navbar-left {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  min-width: 260px;
  transition: min-width 0.28s;

  &.is-collapse {
    min-width: 40px;
  }
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 200px;
  text-decoration: none;
  color: var(--el-color-primary);

  &.is-collapse {
    min-width: auto;
    width: 40px;
  }

  .brand-logo {
    display: block;
    width: auto;
    height: 52px;
    max-width: none;
    flex-shrink: 0;
  }

  &.is-collapse {
    overflow: hidden;

    .brand-logo {
      height: 52px;
    }
  }
}

.hamburger-container {
  height: 64px;
  display: flex;
  align-items: center;
  flex-shrink: 0;
  cursor: pointer;
  color: var(--el-color-primary);
  transition: background 0.2s;
  padding: 0 8px;
  margin-right: 8px;

  &:hover {
    background: var(--navbar-hover);
  }

  :deep(.hamburger) {
    width: 24px;
    height: 24px;
  }
}

.navbar-center {
  flex: 1;
  min-width: 0;
}

.right-menu {
  display: flex;
  align-items: center;
  height: 100%;
  flex-shrink: 0;
  margin-left: auto;
  min-width: 160px;
  justify-content: flex-end;

  .right-menu-item {
    display: inline-flex;
    align-items: center;
    padding: 4px 8px;
    height: auto;
    font-size: 16px;
    color: var(--el-color-primary);

    &.hover-effect {
      cursor: pointer;
      border-radius: 4px;
      transition: background 0.2s;

      &:hover {
        background: var(--navbar-hover);
      }
    }
  }

  .avatar-wrapper {
    display: flex;
    align-items: center;
    gap: 8px;

    .user-avatar {
      cursor: pointer;
      width: 30px;
      height: 30px;
      border-radius: 50%;
    }

    .user-name {
      font-size: 16px;
      color: var(--el-color-primary);
      max-width: 96px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .caret-icon {
      font-size: 12px;
      color: var(--el-color-primary);
    }
  }
}

:global(html[dir="rtl"]) .right-menu {
  margin-left: 0;
  margin-right: auto;
}
</style>
