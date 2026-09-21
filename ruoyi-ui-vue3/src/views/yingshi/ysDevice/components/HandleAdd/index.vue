<template>
  <el-dialog :title="$tp(dialog.title)" v-model="dialog.visible" width="32%" append-to-body>
    <el-form ref="handleAddFormRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item :label="$tp('设备序列号')" prop="deviceSerial">
        <el-input v-model="form.deviceSerial" show-word-limit :maxlength="50" :placeholder="$tp('请输入设备序列号')"/>
      </el-form-item>
      <el-form-item :label="$tp('设备验证码')" prop="validateCode">
        <el-input v-model="form.validateCode" show-word-limit :maxlength="50" :placeholder="$tp('请输入设备验证码')"/>
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button :loading="buttonLoading" type="primary" @click="submitForm">{{ $tp("确 定") }}</el-button>
        <el-button @click="cancel">{{ $tp("取 消") }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup name="HandleAdd">
import {addDevice} from "@/api/yingshi/ysDevice.js";

const { proxy } = getCurrentInstance();

const buttonLoading = ref(false);
const form = ref({
  deviceSerial: '',
  validateCode: '',
});
const rules = ref({
  deviceSerial: [
    {required: true, get message() { return translatePhrase("请输入设备序列号") }, trigger: "blur"}
  ],
  validateCode: [
    {required: true, get message() { return translatePhrase("请输入设备验证码") }, trigger: "blur"}
  ]
});

const emit = defineEmits();
const handleAddFormRef = ref();

const dialog = reactive({
  visible: false,
  title: ''
});

/**
 * 显示弹窗
 */
const show = async (configId) => {
  dialog.visible = true
  dialog.title = translatePhrase("添加设备")
  buttonLoading.value = false
  reset()
  form.value.configId = configId
}

/** 表单重置 */
const reset = () => {
  form.value = {
    deviceSerial: '',
    validateCode: '',
    configId: null,
  }
  handleAddFormRef.value?.resetFields();
}

/** 提交按钮 */
const submitForm = () => {
  handleAddFormRef.value?.validate(async (valid) => {
    if (valid) {
      buttonLoading.value = true;
      await addDevice(form.value).finally(() => buttonLoading.value = false)
      proxy?.$modal.msgSuccess(translatePhrase("添加成功"));
      dialog.visible = false;
      emit('success');
    }
  });
}



/** 取消按钮 */
const cancel = () => {
  reset();
  dialog.visible = false;
}

//关键点 把 方法暴露给父组件
defineExpose({show})
</script>

<style scoped>

</style>
