<template>
  <div ref="target" class="searchHeightOut">
    <div class="searchHeight">
      <el-input
        v-model="form[props.keyword]"
        :class="{ large: islarge }"
        clearable
        class="hei_input"
        :placeholder="props.placeholder"
        @focus="isfocus = true; islarge = true"
        @mouseenter="isfocus = true; islarge = true"
        @keyup.enter="searchFn"
      >
        <template #prefix>
          <el-icon v-if="!islarge">
            <Search />
          </el-icon>
        </template>
      </el-input>
      <template v-if="islarge">
        <svg-icon
          v-popover="popoverRef"
          class="icon_sel"
          :class="{ act: visible }"
          icon-class="searchHeight"
          @click.stop="isfocus = false"
        />
        <div class="icon_ser" @click="searchFn">
          <el-icon :size="16">
            <Search />
          </el-icon>
        </div>
      </template>
    </div>
    <el-popover
      ref="popoverRef"
      v-model:visible="visible"
      popper-class="popoverRefHeight"
      trigger="click"
      virtual-triggering
      :persistent="islarge"
    >
      <el-form class="searchBox" :model="form" :rules="rulesTemp">
        <el-form-item v-for="(item, i) in dataTemp" :key="i" class="flexRowAC" :prop="item.value">
          <div v-if="item.type !== 'checkbox' && item.type !== 'serchItem'" class="serchItem flexRowAC">
            <div class="serchItem-label flexRowAC">
              <span>{{ $tp(item.label) }}</span>
              <el-tooltip v-if="item.tips" :content="item.tips" placement="top">
                <el-icon class="label-tip-icon">
                  <QuestionFilled />
                </el-icon>
              </el-tooltip>
            </div>
            <el-input
              v-if="item.type === 'text'"
              v-model="form[item.value]"
              class="wh-input"
              :placeholder="$tp('请输入')"
            />
            <el-select
              v-else-if="item.type === 'select'"
              v-model="form[item.value]"
              :teleported="false"
              clearable
              :placeholder="$tp('请选择')"
            >
              <el-option
                v-for="(dd, tt) in item.option"
                :key="tt"
                :label="dd.label"
                :value="dd.value"
              />
            </el-select>
            <el-tree-select
              v-else-if="item.type === 'tree-select'"
              v-model="form[item.value]"
              class="wh-input"
              :data="item.option || []"
              :props="item.props || { value: 'id', label: 'label', children: 'children' }"
              :value-key="item.valueKey || 'id'"
              :teleported="false"
              check-strictly
              clearable
              :placeholder="item.placeholder || $tp('请选择')"
            />
            <el-radio-group
              v-else-if="item.type === 'radio'"
              v-model="form[item.value]"
              class="datePicker"
            >
              <el-radio v-for="(dd, tt) in item.option" :key="tt" :label="dd.value">
                {{ dd.label }}
              </el-radio>
            </el-radio-group>
            <div v-else-if="item.type === 'daterange' || item.type === 'datetimerange'" class="datePicker">
              <el-date-picker
                v-model="form[item.value]"
                :teleported="false"
                :type="item.type === 'datetimerange' ? 'datetimerange' : 'daterange'"
                range-separator="-"
                :start-placeholder="item?.startP || $tp('开始时间')"
                :end-placeholder="item?.endP || $tp('结束时间')"
                :value-format="item?.format"
                :default-time="item?.defaultTime"
                :shortcuts="item.type === 'datetimerange' ? undefined : shortcuts"
              />
            </div>
            <template v-else>
              <div v-if="item.type !== 'checkbox'">
                <slot :name="item.type" />
              </div>
            </template>
          </div>
          <template v-if="item.type === 'serchItem'">
            <slot :name="item.slotName" />
          </template>
          <div v-if="item.type === 'checkbox'" class="serchItem flexRowAC">
            <el-checkbox v-model="form[item.value]" class="checkbox" :label="item.label" />
          </div>
        </el-form-item>
        <div class="serchItem serBtn">
          <el-button type="primary" class="newBtn" @click="searchFn">
            <img class="searchImg" src="@/assets/images/search/search.png" alt="" />{{ $tp("搜索") }}
          </el-button>
          <el-button class="newBtn" @click="resetFn">
            <el-icon class="searchImg">
              <RefreshRight />
            </el-icon>{{ $tp("重置") }}
          </el-button>
        </div>
      </el-form>
    </el-popover>
  </div>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { onClickOutside } from '@vueuse/core'
import { QuestionFilled, Search, RefreshRight } from '@element-plus/icons-vue'

const props = defineProps({
  placeholder: {
    type: String,
    default: '搜索'
  },
  keyword: {
    type: String,
    default: 'keyword',
    required: true
  },
  data: {
    type: Array,
    default: () => []
  }
})
const emits = defineEmits(['handle'])
const popoverRef = ref()
const target = ref(null)
const dataTemp = ref(props.data || [])
const rulesTemp = reactive({})
const form = reactive({})
const visible = ref(false)
const isfocus = ref(false)
const islarge = ref(false)

const blurFn = () => {
  setTimeout(() => {
    if (!visible.value) {
      islarge.value = false
      isfocus.value = false
    }
  }, 300)
}

const searchFn = (val) => {
  let reset = false
  if (val === true) reset = true
  emits('handle', form, reset)
  setTimeout(() => {
    visible.value = false
  }, 300)
}

const resetFn = () => {
  props.data?.forEach((item) => {
    form[item.value] = item.default
  })
  form[props.keyword] = ''
  searchFn(true)
}

dataTemp.value.forEach((item) => {
  form[item.value] = item.default
  rulesTemp[item.value] = item.rules
})
form[props.keyword] = form[props.keyword] || ''

watch(() => visible.value, (value) => {
  if (value === false && isfocus.value === false) {
    islarge.value = value
  }
})

onClickOutside(target, () => blurFn())

const shortcuts = [
  {
    text: '昨天',
    value: () => {
      const now = new Date()
      const start = new Date(now)
      start.setDate(now.getDate() - 1)
      start.setHours(0, 0, 0, 0)
      const end = new Date(start)
      end.setHours(23, 59, 59, 999)
      return [start, end]
    }
  },
  {
    text: '本周',
    value: () => {
      const now = new Date()
      const start = new Date(now)
      const day = start.getDay() || 7
      start.setDate(start.getDate() - day + 1)
      start.setHours(0, 0, 0, 0)
      const end = new Date(start)
      end.setDate(start.getDate() + 6)
      end.setHours(23, 59, 59, 999)
      return [start, end]
    }
  },
  {
    text: '本月',
    value: () => {
      const now = new Date()
      const start = new Date(now.getFullYear(), now.getMonth(), 1)
      start.setHours(0, 0, 0, 0)
      const end = new Date(now.getFullYear(), now.getMonth() + 1, 0)
      end.setHours(23, 59, 59, 999)
      return [start, end]
    }
  },
  {
    text: '上月',
    value: () => {
      const now = new Date()
      const start = new Date(now.getFullYear(), now.getMonth() - 1, 1)
      start.setHours(0, 0, 0, 0)
      const end = new Date(now.getFullYear(), now.getMonth(), 0)
      end.setHours(23, 59, 59, 999)
      return [start, end]
    }
  }
]

watch(() => props.data, (val) => {
  if (val && val.length) {
    dataTemp.value = val
    dataTemp.value.forEach((item) => {
      if (form[item.value] === undefined) {
        form[item.value] = item.default
      }
      rulesTemp[item.value] = item.rules
    })
  }
}, { deep: true })
</script>

<style lang="scss" scoped>
.searchHeight {
  position: relative;
  height: 36px;

  :deep(.hei_input) {
    height: 36px;
    border-radius: 170px;
    width: 280px;
    transition: width 0.3s ease-in-out;

    .el-input__wrapper {
      width: 100%;
      height: 36px;
      min-height: 36px;
      border-radius: 170px;
      box-sizing: border-box;
    }

    &.large {
      width: 540px;
      position: absolute;
      right: 0;

      .el-input__wrapper {
        padding: 0 100px 0 16px;
      }
    }
  }

  .icon_ser {
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 0 170px 170px 0;
    background: #F5F5F5;
    width: 55px;
    height: 34px;
    position: absolute;
    right: 1px;
    top: 50%;
    transform: translateY(-50%);
    box-sizing: border-box;
    z-index: 1;

    .el-icon {
      margin: 0;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    &:hover {
      background: var(--el-color-primary-light-9);
      color: var(--el-color-primary);
    }
  }

  .icon_sel {
    cursor: pointer;
    fill: #999 !important;
    color: #999;
    width: 24px;
    height: 24px;
    font-size: 24px;
    position: absolute;
    right: 62px;
    top: 50%;
    transform: translateY(-50%);
    z-index: 2;

    &:hover,
    &.act {
      background: var(--el-color-primary-light-9);
      fill: var(--el-color-primary) !important;
      color: var(--el-color-primary);
    }
  }
}

.searchBox {
  width: 100%;
  gap: 20px;
  padding: 8px;
  flex-wrap: wrap;
  position: relative;

  .serchItem {
    width: 100%;
    min-height: 48px;
    padding-bottom: 0px !important;
    justify-content: space-between;
    position: relative;
  }

  .serBtn {
    padding-bottom: 0;
    text-align: right;

    .searchImg {
      width: 14px;
      height: 14px;
      background-size: cover;
      margin-right: 8px;
    }
  }

  .el-checkbox.checkbox {
    position: absolute;
    left: 20px;
    bottom: 20px;
  }
}

.serchItem-label {
  display: flex;
  align-items: center;
  gap: 4px;

  .label-tip-icon {
    cursor: pointer;
    font-size: 14px;
    color: #999;
    margin-bottom: 10px;
  }
}
</style>
