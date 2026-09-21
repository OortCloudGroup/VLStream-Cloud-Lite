<template>
  <div v-if="visibleList.length" class="oort_button_group">
    <template v-for="(item, index) in visibleList" :key="index">
      <div
        class="oort_button_group_item"
        :class="{ 'is-disabled': item.disabled }"
        @click="onItemClick(item)"
      >
        <svg-icon
          v-if="item.svg"
          class="btn-group-icon"
          :icon-class="item.svg"
        />
        <span>{{ $tp(item.name) }}</span>
        <el-dropdown v-if="item.children && item.children.length > 0" @click.stop>
          <div class="new_table_svg_group new_item_more">
            <svg-icon class="btn-group-icon" icon-class="more" />
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="(itd, ind) in item.children"
                :key="ind"
                @click="onItemClick(itd)"
              >
                <div class="new_table_svg_group">
                  <svg-icon
                    v-if="itd.svg"
                    class="btn-group-icon"
                    :icon-class="itd.svg"
                  />
                  <span>{{ $tp(itd.name) }}</span>
                </div>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <div
        v-if="index !== visibleList.length - 1 && visibleList.length > 1"
        class="oort_button_group_line"
      />
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { checkPermi } from '@/utils/permission'

const props = defineProps({
  buttonList: {
    type: Array,
    default: () => []
  }
})

const visibleList = computed(() => {
  return (props.buttonList || []).filter(item => {
    if (item.show === false) return false
    if (item.permi && item.permi.length) {
      return checkPermi(item.permi)
    }
    return true
  })
})

function onItemClick(item) {
  if (!item || item.disabled) return
  if (typeof item.clickFn === 'function') {
    item.clickFn()
  }
}
</script>

<style lang="scss" scoped>
.new_item_more {
  margin-left: 0;
  margin-top: 2px;
}

.new_table_svg_group {
  display: flex;
  align-items: center;
  gap: 4px;
}

.oort_button_group {
  border-radius: 20px;
  background: var(--el-menu-hover-bg-color, #f2f3f5);
  height: 36px;
  display: flex;
  align-items: center;

  &_item {
    padding: 0 12px;
    height: 100%;
    cursor: pointer;
    display: flex;
    align-items: center;
    flex-direction: row;
    color: var(--el-color-primary);

    .btn-group-icon {
      width: 16px;
      height: 16px;
      color: var(--el-color-primary);
      fill: currentColor;
    }

    span {
      color: var(--el-color-primary);
      font-size: 14px;
      margin: 0 4px;
      font-weight: 500;
    }

    &.is-disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }
  }

  &_item:first-child:hover:not(.is-disabled) {
    background: var(--el-color-primary-light-9, rgba(64, 158, 255, 0.1));
    border-top-left-radius: 20px;
    border-bottom-left-radius: 20px;
  }

  &_item:hover:not(.is-disabled) {
    background: var(--el-color-primary-light-9, rgba(64, 158, 255, 0.1));
  }

  &_item:last-child:hover:not(.is-disabled) {
    background: var(--el-color-primary-light-9, rgba(64, 158, 255, 0.1));
    border-top-right-radius: 20px;
    border-bottom-right-radius: 20px;
  }

  &_line {
    width: 1px;
    height: 16px;
    background: var(--el-color-primary-light-7, rgba(64, 158, 255, 0.2));
  }
}
</style>
