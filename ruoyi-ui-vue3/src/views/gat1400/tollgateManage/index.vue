<template>
  <div class="app-container">
    <el-card>
      <el-form ref="queryForm" :model="queryParams" :inline="true" v-show="showSearch">
        <el-form-item :label="$tp('卡口标识')" prop="tollgateId">
          <el-input v-model="queryParams.tollgateId" :placeholder="$tp('请输入设备标识')"/>
        </el-form-item>
        <el-form-item :label="$tp('卡口名称')" prop="name">
          <el-input v-model="queryParams.name" :placeholder="$tp('请输入卡口名称')"/>
        </el-form-item>
        <el-form-item :label="$tp('管辖单位代码')" prop="orgCode">
          <el-input v-model="queryParams.orgCode" :placeholder="$tp('请输入组织机构编码')"/>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">{{ $tp("搜索") }}</el-button>
          <el-button icon="Refresh" @click="resetQuery">{{ $tp("重置") }}</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button
              type="primary"
              plain
              icon="Plus"
              @click="handleAdd"
              v-hasPermi="['viid:tollgate:add']"
          >{{ $tp("新增") }}
          </el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table
          v-loading="loading"
          :data="tableList"
          border
          tooltip-effect="dark"
      >
        <template v-for="(item, index) in tableColumns">
          <el-table-column v-if="item.show" :key="index" :prop="item.prop" :label="item.label"
                           :formatter="item.formatter"
                           align="center" show-overflow-tooltip/>
        </template>
        <el-table-column :label="$tp('操作')" align="center" fixed="right">
          <template #default="scope">
            <div style="display:flex; align-items: center;justify-content: center">
              <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
                         v-hasPermi="['viid:tollgate:edit']">{{ $tp("修改") }}
              </el-button>
              <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
                         style="color: #f56c6c"
                         v-hasPermi="['viid:tollgate:remove']">{{ $tp("删除") }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <pagination
          v-show="total>0"
          :total="total"
          v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize"
          @pagination="getList"
      />

      <el-dialog :title="$tp(title)" v-model="open" width="50%" append-to-body>
        <el-form ref="TollgateManageRef" :model="form" :rules="rules" label-width="120px">
          <el-tooltip class="item" effect="dark"
                      :content="$tp('国标格式卡口编号(6位地区编码+0000121+7位随机数)设备唯一不可重复')" placement="top">
            <el-form-item :label="$tp('卡口标识')" prop="tollgateId">
              <el-input v-model="form.tollgateId" :placeholder="$tp('请输入卡口标识')"/>
            </el-form-item>
          </el-tooltip>
          <el-form-item :label="$tp('卡口名称')" prop="name">
            <el-input v-model="form.name" :placeholder="$tp('请输入卡口名称')"/>
          </el-form-item>
          <el-form-item :label="$tp('卡口经度')" prop="longitude">
            <el-input v-model="form.longitude" type="number" :placeholder="$tp('请输入卡口经度')"/>
          </el-form-item>
          <el-form-item :label="$tp('卡口纬度')" prop="latitude">
            <el-input v-model="form.latitude" type="number" :placeholder="$tp('请输入卡口纬度')"/>
          </el-form-item>
          <el-form-item :label="$tp('位置编码')" prop="placeCode">
            <el-input v-model="form.placeCode" :placeholder="$tp('请输入位置编码')"/>
          </el-form-item>
          <el-form-item :label="$tp('卡口类型')" prop="tollgateCat">
            <el-select v-model="form.tollgateCat" :placeholder="$tp('请选择卡口类型')">
              <el-option value="10" :label="$tp('国际')"></el-option>
              <el-option value="20" :label="$tp('省际')"></el-option>
              <el-option value="30" :label="$tp('市际')"></el-option>
              <el-option value="31" :label="$tp('市区')"></el-option>
              <el-option value="40" :label="$tp('县际')"></el-option>
              <el-option value="41" :label="$tp('县区')"></el-option>
              <el-option value="99" :label="$tp('其他')"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item :label="$tp('卡口用途')" prop="tollgateUsage">
            <el-select v-model="form.tollgateUsage" :placeholder="$tp('请选择卡口用途')">
              <el-option value="80" :label="$tp('治安卡口')"></el-option>
              <el-option value="81" :label="$tp('交通卡口')"></el-option>
              <el-option value="82" :label="$tp('其他')"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item :label="$tp('车道号')" prop="laneNum">
            <el-input v-model="form.laneNum" :placeholder="$tp('请输入车道号')"/>
          </el-form-item>
          <el-form-item :label="$tp('管辖单位代码')" type="number" prop="orgCode">
            <el-input v-model="form.orgCode" :placeholder="$tp('请输入组管辖单位代码')"/>
          </el-form-item>
          <el-tooltip class="item" effect="dark" :content="$tp('卡口关联的设备编号')" placement="top">
            <el-form-item :label="$tp('关联采集设备')" type="number" prop="deviceId">
              <el-select v-model="form.deviceId" clearable filterable remote :remote-method="loadDeviceOptions"
                         :placeholder="$tp('请选择关联采集设备')">
                <el-option v-for="item in deviceOptions" :key="item.apeId" :value="item.apeId" :label="item.name"/>
              </el-select>
            </el-form-item>
          </el-tooltip>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button type="primary" @click="submitForm">{{ $tp("确 定") }}</el-button>
            <el-button @click="cancel">{{ $tp("取 消") }}</el-button>
          </div>
        </template>
      </el-dialog>

    </el-card>
  </div>
</template>

<script setup name="TollgateManage">
import {getDeviceOptions} from "@/api/gat1400/apedevice";
import {addTollgate, delTollgates, getTollgate, pageTollgate, updateTollgate} from "@/api/gat1400/tollgateManage.js";

const {proxy} = getCurrentInstance();
const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  tollgateId: undefined,
  name: undefined,
  orgCode: undefined,
})
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const tableList = ref([]);
const deviceOptions = ref([]);
const tableColumns = ref([
  {prop: 'tollgateId', get label() { return translatePhrase("卡口标识") }, show: true},
  {prop: 'name', get label() { return translatePhrase("卡口名称") }, show: true},
  {prop: 'longitude', get label() { return translatePhrase("经度") }, show: true},
  {prop: 'latitude', get label() { return translatePhrase("纬度") }, show: true},
  {prop: 'placeCode', get label() { return translatePhrase("位置编码") }, show: true},
  {prop: 'tollgateCat', get label() { return translatePhrase("卡口类型") }, show: true},
  {prop: 'tollgateUsage', get label() { return translatePhrase("卡口用途") }, show: true},
  {prop: 'laneNum', get label() { return translatePhrase("车道数量") }, show: true},
  {prop: 'orgCode', get label() { return translatePhrase("管辖单位代码") }, show: true}
]);
const open = ref(false);
const form = ref({});
const rules = ref({
  tollgateId: [
    {required: true, get message() { return translatePhrase("设备标识不能为空") }, trigger: 'blur'},
    {min: 20, max: 20, get message() { return translatePhrase("卡口标识必须为20位") }, trigger: 'blur'}
  ],
  name: [{required: true, get message() { return translatePhrase("卡口名称不能为空") }, trigger: 'blur'}],
  longitude: [{required: true, get message() { return translatePhrase("卡口经度不能为空") }, trigger: 'blur'}],
  latitude: [{required: true, get message() { return translatePhrase("卡口纬度不能为空") }, trigger: 'blur'}],
  placeCode: [{required: true, get message() { return translatePhrase("位置编码不能为空") }, trigger: 'blur'}],
  tollgateCat: [{required: true, get message() { return translatePhrase("卡口类型不能为空") }, trigger: 'blur'}],
  tollgateUsage: [{required: true, get message() { return translatePhrase("卡口用途不能为空") }, trigger: 'blur'}],
  laneNum: [{required: true, get message() { return translatePhrase("车道数量不能为空") }, trigger: 'blur'}],
  orgCode: [{required: true, get message() { return translatePhrase("组织机构编码不能为空") }, trigger: 'blur'}],
});
const title = ref("");
const addAndUpdate = ref(false);

function submitForm(){
  proxy.$refs["TollgateManageRef"].validate(valid => {
    if (valid) {
      if (addAndUpdate.value){
        addTollgate(form.value).then(response => {
          if(response.success){
            proxy.$modal.msgSuccess(translatePhrase("操作成功"));
            getList();
            cancel();
          }
        })
      } else {
        updateTollgate(form.value).then(response => {
          if(response.success){
            proxy.$modal.msgSuccess(translatePhrase("操作成功"));
            getList();
            cancel();
          }
        })
      }
    }
  });
}

function loadDeviceOptions(keyword, id) {
  getDeviceOptions({name: keyword, apeId: id}).then(response => {
    deviceOptions.value = response.data
  })
}

function cancel(){
  addAndUpdate.value = false;
  open.value = false;
}

function handleAdd() {
  form.value = {
    tollgateCat: "20",
    tollgateUsage: "80",
    deviceId: undefined
  }
  addAndUpdate.value = true;
  open.value = true;
  title.value = "新增卡口";
}

function handleUpdate(row) {
  getTollgate(row.tollgateId).then(res => {
    addAndUpdate.value = false;
    form.value = res.data
    open.value = true;
    title.value = "修改卡口";
  })
}

function handleDelete(row) {
  proxy.$modal.confirm(translatePhrase("选中数据将被永久删除, 是否继续？")).then(function () {
    delTollgates(row.tollgateId);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess(translatePhrase("删除成功"));
  });
}

// 搜索按钮操作
const handleQuery = () => {
  queryParams.value.pageNum = 1
  getList()
}

// 重置按钮操作
const resetQuery = () => {
  queryParams.value = {
    pageNum: 1,
    pageSize: 10,
    tollgateId: undefined,
    name: undefined,
    orgCode: undefined,
    taskName: ""
  }
  handleQuery()
}

async function getList() {
  loading.value = true;
  const res = await pageTollgate(queryParams.value);
  tableList.value = res.data
  total.value = res.total
  loading.value = false;
}

onMounted(() => {
  getList();
});
</script>

<style scoped>

</style>
