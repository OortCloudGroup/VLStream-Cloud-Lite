<template>
  <el-dialog v-model="open" width="32%" :title="$tp('选择生成类型')" @open="onOpen" @close="onClose">
    <el-form ref="codeTypeForm" :model="formData" :rules="rules" label-width="100px">
      <el-form-item :label="$tp('生成类型')" prop="type">
        <el-radio-group v-model="formData.type">
          <el-radio-button v-for="(item, index) in typeOptions" :key="index" :label="item.value">
            {{ item.label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="showFileName" :label="$tp('文件名')" prop="fileName">
        <el-input v-model="formData.fileName" :placeholder="$tp('请输入文件名')" clearable />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" @click="handelConfirm">{{ $tp("确定") }}</el-button>
        <el-button @click="onClose">{{ $tp("取消") }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
const open = defineModel()
const props = defineProps({
  showFileName: Boolean
})
const emit = defineEmits(['confirm'])
const formData = ref({
  fileName: undefined,
  type: 'file'
})
const codeTypeForm = ref()
const rules = {
  fileName: [{
    required: true,
    get message() { return translatePhrase("请输入文件名") },
    trigger: 'blur'
  }],
  type: [{
    required: true,
    get message() { return translatePhrase("生成类型不能为空") },
    trigger: 'change'
  }]
}
const typeOptions = ref([
  {
    get label() { return translatePhrase("页面") },
    value: 'file'
  },
  {
    get label() { return translatePhrase("弹窗") },
    value: 'dialog'
  }
])
function onOpen() {
  if (props.showFileName) {
    formData.value.fileName = `${+new Date()}.vue`
  }
}
function onClose() {
  open.value = false
}
function handelConfirm() {
  codeTypeForm.value.validate(valid => {
    if (!valid) return
    emit('confirm', { ...formData.value })
    onClose()
  })
}
</script>