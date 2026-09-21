<!-- @/views/gat1400/apedevice/components/DeviceDialog.vue -->
<template>
  <el-dialog
      :title="$tp(title)"
      v-model="dialogVisible"
      width="50%"
      append-to-body
      @closed="handleClose"
  >
    <el-form
        ref="deviceManageRef"
        :model="form"
        :rules="rules"
        label-width="120px"
    >
      <el-tooltip class="item" effect="dark" :content="$tp('国标格式设备编号(6位地区编码+0000132+7位随机数)设备唯一不可重复')" placement="top">
        <el-form-item :label="$tp('设备标识')" prop="apeId">
          <el-input v-model="form.apeId" :placeholder="$tp('请输入设备标识')" :disabled="!isAdd" />
        </el-form-item>
      </el-tooltip>

      <el-form-item :label="$tp('设备名称')" prop="name">
        <el-input v-model="form.name" :placeholder="$tp('请输入设备名称')" />
      </el-form-item>

      <el-form-item :label="$tp('设备型号')" prop="model">
        <el-input v-model="form.model" :placeholder="$tp('请输入设备型号')" />
      </el-form-item>

      <el-tooltip class="item" effect="dark" :content="$tp('设备IP地址,单向对接数据可任意填写127.0.0.1')" placement="top">
        <el-form-item :label="$tp('设备地址')" prop="ipAddr">
          <el-input v-model="form.ipAddr" :placeholder="$tp('请输入设备地址')" />
        </el-form-item>
      </el-tooltip>

      <el-tooltip class="item" effect="dark" :content="$tp('设备IP端口,单向对接数据可任意填写8080')" placement="top">
        <el-form-item :label="$tp('设备端口')" prop="port">
          <el-input v-model="form.port" type="number" :placeholder="$tp('请输入设备端口')" />
        </el-form-item>
      </el-tooltip>

      <el-tooltip class="item" effect="dark" :content="$tp('设备安装地点经纬度 1.000001')" placement="top">
        <el-form-item :label="$tp('设备经度')" prop="longitude">
          <el-input v-model="form.longitude" type="number" :placeholder="$tp('请输入设备经度')" />
        </el-form-item>
      </el-tooltip>

      <el-tooltip class="item" effect="dark" :content="$tp('设备安装地点经纬度 1.000001')" placement="top">
        <el-form-item :label="$tp('设备纬度')" prop="latitude">
          <el-input v-model="form.latitude" type="number" :placeholder="$tp('请输入设备纬度')" />
        </el-form-item>
      </el-tooltip>

      <el-tooltip class="item" effect="dark" :content="$tp('12位设备安装地区编码 431021001000')" placement="top">
        <el-form-item :label="$tp('地区编码')" prop="placeCode">
          <el-input v-model="form.placeCode" :placeholder="$tp('请输入地区编码')" />
        </el-form-item>
      </el-tooltip>

      <el-tooltip class="item" effect="dark" :content="$tp('具体到摄像机位置或街道门牌号，由 (乡镇街道)+ (街路巷)+ (门楼牌号)+ (门楼详细地址)构成')" placement="top">
        <el-form-item :label="$tp('位置名')" prop="place">
          <el-input v-model="form.place" :placeholder="$tp('请输入位置名')" />
        </el-form-item>
      </el-tooltip>

      <el-form-item :label="$tp('管辖单位代码')" prop="orgCode">
        <el-input v-model="form.orgCode" :placeholder="$tp('请输入管辖单位代码')" />
      </el-form-item>

      <el-form-item :label="$tp('用户名')" prop="userId">
        <el-input v-model="form.userId" :placeholder="$tp('请输入用户标识')" />
      </el-form-item>

      <el-form-item :label="$tp('密码')" prop="password">
        <el-input v-model="form.password" :placeholder="$tp('请输入密码')" type="password" show-password />
      </el-form-item>

      <el-form-item :label="$tp('车辆抓拍方向')" prop="capDirection">
        <el-select v-model="form.capDirection" :placeholder="$tp('请选择抓拍方向')">
          <el-option value="0" :label="$tp('拍车头')" />
          <el-option value="1" :label="$tp('拍车尾')" />
        </el-select>
      </el-form-item>

      <el-form-item :label="$tp('监视方向')" prop="monitorDirection">
        <el-select v-model="form.monitorDirection" :placeholder="$tp('请选择监视方向')">
          <el-option value="1" :label="$tp('东')" />
          <el-option value="2" :label="$tp('西')" />
          <el-option value="3" :label="$tp('南')" />
          <el-option value="4" :label="$tp('北')" />
          <el-option value="5" :label="$tp('东北')" />
          <el-option value="6" :label="$tp('西南')" />
          <el-option value="7" :label="$tp('东南')" />
          <el-option value="8" :label="$tp('西北')" />
          <el-option value="9" :label="$tp('其他')" />
        </el-select>
      </el-form-item>

      <el-form-item :label="$tp('监视区域说明')" prop="monitorAreaDesc">
        <el-input v-model="form.monitorAreaDesc" :placeholder="$tp('请输入监视区域说明')" type="textarea" />
      </el-form-item>

      <el-form-item :label="$tp('所属采集系统')" prop="ownerApsId">
        <el-select
            v-model="form.ownerApsId"
            clearable
            filterable
            remote
            :remote-method="remoteMethod"
            :placeholder="$tp('请选择采集系统')"
        >
          <el-option
              v-for="item in serverOptions"
              :key="item.id"
              :value="item.id"
              :label="item.label"
          />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" @click="submitForm">{{ $tp("确 定") }}</el-button>
        <el-button @click="cancel">{{ $tp("取 消") }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue';
import { getServerOptions } from '@/api/gat1400/viidutils';

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  formData: {
    type: Object,
    default: () => ({})
  },
  isAdd: {
    type: Boolean,
    default: true
  },
  title: {
    type: String,
    default: ''
  }
});

const emit = defineEmits(['update:modelValue', 'submit']);

const dialogVisible = ref(false);
const deviceManageRef = ref(null);
const form = ref({});
const serverOptions = ref([]);
const rules = ref({
  apeId: [{ required: true, get message() { return translatePhrase("设备标识不能为空") }, trigger: 'blur' }],
  name: [{ required: true, get message() { return translatePhrase("设备名称不能为空") }, trigger: 'blur' }],
  longitude: [{ required: true, get message() { return translatePhrase("经度不能为空") }, trigger: 'blur' }],
  latitude: [{ required: true, get message() { return translatePhrase("纬度不能为空") }, trigger: 'blur' }],
  placeCode: [{ required: true, get message() { return translatePhrase("位置编码不能为空") }, trigger: 'blur' }],
  userId: [{ required: true, get message() { return translatePhrase("用户标识不能为空") }, trigger: 'blur' }],
  password: [{ required: true, get message() { return translatePhrase("口令不能为空") }, trigger: 'blur' }],
  ipAddr: [{ required: true, get message() { return translatePhrase("设备地址不能为空") }, trigger: 'blur' }],
  port: [{ required: true, get message() { return translatePhrase("设备端口不能为空") }, trigger: 'blur' }]
});


watch(
    () => props.modelValue,
    (val) => {
      dialogVisible.value = val;
      if (val) {
        form.value = { ...props.formData };
      }
    },
    { immediate: true }
);

// 远程搜索采集系统
const remoteMethod = async (keyword) => {
  if (keyword.trim() === '') return;
  const res = await getServerOptions({ serverName: keyword });
  serverOptions.value = res.data || [];
};

const submitForm = () => {
  deviceManageRef.value.validate((valid) => {
    if (valid) {
      emit('submit', form.value);
      dialogVisible.value = false;
    }
  });
};

const cancel = () => {
  deviceManageRef.value?.resetFields?.();
  dialogVisible.value = false;
};

const handleClose = () => {
  emit('update:modelValue', false);
};
</script>

<style scoped>

</style>
