<template>
  <el-dropdown trigger="click" @command="setLocale">
    <button class="language-switch" type="button" :aria-label="$t('language.label')" :title="$t('language.label')">
      <span class="language-switch__icon" aria-hidden="true">🌐</span>
      <span class="language-switch__code">{{ activeLocale.shortCode.toUpperCase() }}</span>
    </button>
    <template #dropdown>
      <el-dropdown-menu class="language-menu">
        <el-dropdown-item
          v-for="locale in supportedLocales"
          :key="locale.code"
          :command="locale.code"
          :class="{ 'is-active': locale.code === currentLocale }"
        >
          <span :dir="locale.dir" :lang="locale.code">{{ locale.name }}</span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
import { computed } from 'vue'
import { currentLocale, setLocale, supportedLocales } from '@/i18n'

const activeLocale = computed(() => supportedLocales.find(item => item.code === currentLocale.value) || supportedLocales[0])
</script>

<style lang="scss" scoped>
.language-switch {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 54px;
  height: 36px;
  padding: 0 10px;
  border: 0;
  border-radius: 6px;
  color: inherit;
  background: transparent;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    color: var(--el-color-primary);
    background: var(--navbar-hover, rgba(0, 0, 0, 0.05));
    outline: none;
  }
}

.language-switch__code {
  font-size: 13px;
  font-weight: 600;
}

.language-switch__icon {
  line-height: 1;
}
</style>
