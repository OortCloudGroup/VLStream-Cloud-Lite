<template>
  <div class="detail-page">
    <detail-page-header
      :parent-title="$tp('国标级联')"
      :title="$tp('通道共享')"
      back-path="/gbmanger/platform"
    />
    <div class="detail-body">
    <el-tabs v-model="queryParams.hasShare" class="demo-tabs" @tab-click="handleClick">
      <el-tab-pane :label="$tp('未关联')" name="false"/>
      <el-tab-pane :label="$tp('已关联')" name="true"/>

      <div class="toolbar-with-search">
        <div class="toolbar-left">
          <button
            v-if="queryParams.hasShare !== 'true'"
            type="button"
            class="exportBtn newBtn flexRowAC"
            :disabled="multiple"
            @click="handleAdd"
            v-hasPermi="['wvp:platform:channelAdd']"
          >
            <el-icon class="BtnImg"><Plus /></el-icon>{{ $tp("新增") }}
          </button>
          <button
            v-else
            type="button"
            class="exportBtn newBtn flexRowAC"
            :disabled="multiple"
            @click="handleDelete"
            v-hasPermi="['wvp:platform:channelRemove']"
          >
            <el-icon class="BtnImg"><Delete /></el-icon>{{ $tp("删除") }}
          </button>
          <button-group :button-list="toolbarButtons" />
        </div>
        <div class="searchHeight_out flexRowAC">
          <search-height-box keyword="query" :placeholder="$tp('请输入关键字')" :data="searchData" @handle="searchResetFn" />
          <export-excel-pdf />
        </div>
      </div>

      <table-self v-loading="loading" :data="channelList" @selection-change="handleSelectionChange" class="new_table" header-cell-class-name="header_tenant_cell" stripe>
        <el-table-column type="selection" :width="clacPXToVW(55)" align="center"/>
        <el-table-column prop="gbName" :label="$tp('名称')" :min-width="clacPXToVW(180)" align="center"/>
        <el-table-column prop="gbDeviceId" :label="$tp('编号')" :min-width="clacPXToVW(180)" align="center"/>
        <el-table-column prop="gbManufacturer" :label="$tp('厂家')" :min-width="clacPXToVW(100)" align="center"/>
        <el-table-column :label="$tp('类型')" :min-width="clacPXToVW(100)" align="center">
          <template #default="scope">
            <div slot="reference" class="name-wrapper">
              <el-tag effect="plain" v-if="scope.row.dataType === 1">{{ $tp("国标设备") }}</el-tag>
              <el-tag effect="plain" type="success" v-else-if="scope.row.dataType === 2">{{ $tp("推流设备") }}</el-tag>
              <el-tag effect="plain" type="warning" v-else-if="scope.row.dataType === 3">{{ $tp("拉流代理") }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="$tp('状态')" :min-width="clacPXToVW(100)" align="center">
          <template #default="scope">
            <div slot="reference" class="name-wrapper">
              <el-tag v-if="scope.row.gbStatus === 'ON'">{{ $tp("在线") }}</el-tag>
              <el-tag type="info" v-if="scope.row.gbStatus !== 'ON'">{{ $tp("离线") }}</el-tag>
            </div>
          </template>
        </el-table-column>
      </table-self>

      <pagination
          v-show="total > 0"
          :total="total"
          v-model:page="queryParams.page"
          v-model:limit="queryParams.count"
          @pagination="getList"
      />

      <el-dialog :title="$tp(title)" v-model="open" width="50%" append-to-body>
        <div class="searchHeight_out flexRowAC" style="margin-bottom: 12px; justify-content: flex-end;">
          <search-height-box keyword="name" :placeholder="$tp('请输入设备名称等关键词')" :data="deviceSearchData" @handle="deviceSearchResetFn" />
          <export-excel-pdf />
        </div>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain :disabled="multipleDevice" @click="handleSure">{{ $tp("确定") }}</el-button>
          </el-col>
        </el-row>

        <table-self v-loading="loading" :data="deviceList" @selection-change="handleSelectionDeviceChange" class="new_table" header-cell-class-name="header_tenant_cell" stripe>
          <el-table-column type="selection" :width="clacPXToVW(55)" align="center"/>
          <el-table-column prop="name" :label="$tp('名称')" align="center"/>
          <el-table-column prop="deviceId" :label="$tp('设备编号')" align="center"/>
          <el-table-column prop="channelCount" :label="$tp('通道数')" align="center"/>
          <el-table-column prop="manufacturer" :label="$tp('厂家')" align="center"/>
          <el-table-column :label="$tp('地址')" align="center">
            <template #default="scope">
              <div slot="reference" class="name-wrapper">
                <el-tag v-if="scope.row.hostAddress" size="medium">{{ scope.row.hostAddress }}</el-tag>
                <el-tag v-if="!scope.row.hostAddress" size="medium">{{ $tp("未知") }}</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="$tp('状态')" align="center">
            <template #default="scope">
              <div slot="reference" class="name-wrapper">
                <el-tag v-if="scope.row.onLine">{{ $tp("在线") }}</el-tag>
                <el-tag type="info" v-if="!scope.row.onLine">{{ $tp("离线") }}</el-tag>
              </div>
            </template>
          </el-table-column>
        </table-self>

        <pagination
            v-show="totalDevice > 0"
            :total="totalDevice"
            v-model:page="queryParamsDevice.pageNum"
            v-model:limit="queryParamsDevice.pageSize"
            @pagination="getDeviceList"
        />
      </el-dialog>
    </el-tabs>
    </div>
  </div>
</template>

<script setup name="ChooseChannel">
import { clacPXToVW } from "@/utils/index";
import {
  addChannel,
  addChannelByDevice,
  delChannelForGB,
  queryChannelList,
  removeChannelByDevice
} from "../../../api/wvp/platform.js";
import {useRoute} from "vue-router";
import {listDevice} from "../../../api/wvp/device.js";

const {proxy} = getCurrentInstance();
const route = useRoute();

const loading = ref(false)
const channelList = ref([])
const total = ref(0);
const multiple = ref(true);
const selectionList = ref([]);
const open = ref(false);
const title = ref("");

const loadingDevice = ref(false)
const deviceList = ref([])
const totalDevice = ref(0);
const multipleDevice = ref(true);
const selectionDeviceList = ref([]);
const typeDevice = ref("");

const data = reactive({
  form: {},
  queryParams: {
    page: 1,
    count: 10,
    query: undefined,
    online: undefined,
    channelType: undefined,
    hasShare: 'false',
  },
  rules: {},
  queryParamsDevice: {
    pageNum: 1,
    pageSize: 10,
    name: undefined,
    status: undefined,
    ip: undefined,
    manufacturer: undefined,
  }
});

const {queryParams, form, rules, queryParamsDevice} = toRefs(data);

const toolbarButtons = computed(() => {
  if (queryParams.value.hasShare === 'true') {
    return [
      { get name() { return translatePhrase("按设备移除") }, svg: 'delete', permi: ['wvp:platform:channelRemove'], clickFn: () => handleRemoveByDevice() },
      { get name() { return translatePhrase("移除所有通道") }, svg: 'delete', permi: ['wvp:platform:channelRemove'], clickFn: () => handleRemoveAll() }
    ]
  }
  return [
    { get name() { return translatePhrase("按设备添加") }, svg: 'edit', permi: ['wvp:platform:channelAdd'], clickFn: () => handleAddByDevice() },
    { get name() { return translatePhrase("添加所有通道") }, svg: 'edit', permi: ['wvp:platform:channelAdd'], clickFn: () => handleAddAll() }
  ]
});

const searchData = [
  {
    get label() { return translatePhrase("在线状态") },
    value: 'online',
    type: 'select',
    option: [
      { get label() { return translatePhrase("在线") }, value: 'true' },
      { get label() { return translatePhrase("离线") }, value: 'false' }
    ],
    default: undefined
  },
  {
    get label() { return translatePhrase("类型") },
    value: 'channelType',
    type: 'select',
    option: [
      { get label() { return translatePhrase("国标设备") }, value: 1 },
      { get label() { return translatePhrase("推流设备") }, value: 2 },
      { get label() { return translatePhrase("拉流代理") }, value: 3 }
    ],
    default: undefined
  }
];

const deviceSearchData = [
  { get label() { return translatePhrase("地址") }, value: 'ip', type: 'text', default: '' },
  { get label() { return translatePhrase("厂家") }, value: 'manufacturer', type: 'text', default: '' },
  {
    get label() { return translatePhrase("在线状态") },
    value: 'status',
    type: 'select',
    option: [
      { get label() { return translatePhrase("在线") }, value: 'true' },
      { get label() { return translatePhrase("离线") }, value: 'false' }
    ],
    default: undefined
  }
];

function getList() {
  loading.value = true
  queryChannelList(queryParams.value).then((res) => {
    channelList.value = res.data.list
    total.value = res.data.total
    loading.value = false
  })
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.page = 1;
  getList();
}

function searchResetFn(val) {
  queryParams.value.page = 1;
  queryParams.value.query = val.query || undefined;
  queryParams.value.online = val.online || undefined;
  queryParams.value.channelType = val.channelType || undefined;
  getList();
}

function deviceSearchResetFn(val) {
  queryParamsDevice.value.pageNum = 1;
  queryParamsDevice.value.name = val.name || undefined;
  queryParamsDevice.value.ip = val.ip || undefined;
  queryParamsDevice.value.manufacturer = val.manufacturer || undefined;
  queryParamsDevice.value.status = val.status || undefined;
  getDeviceList();
}


/** 选择条数  */
function handleSelectionChange(selection) {
  selectionList.value = selection
  multiple.value = !selection.length;
}

function handleAdd() {
  let channels = []
  for (let i = 0; i < selectionList.value.length; i++) {
    channels.push(selectionList.value[i].gbId)
  }
  if (channels.length === 0) {
    proxy.$modal.msgError(translatePhrase("请选择要关联的通道"));
    return;
  }

  addChannel({
    platformId: queryParams.value.platformId,
    channelIds: channels
  }).then(() => {
    proxy.$modal.msgSuccess(translatePhrase("关联成功"));
    getList()
  })
}

function handleDelete() {
  let channels = []
  for (let i = 0; i < selectionList.value.length; i++) {
    channels.push(selectionList.value[i].gbId)
  }
  if (channels.length === 0) {
    proxy.$modal.msgError(translatePhrase("请选择要关联的通道"));
    return;
  }

  delChannelForGB({
    platformId: queryParams.value.platformId,
    channelIds: channels
  }).then(() => {
    proxy.$modal.msgSuccess(translatePhrase("取消关联成功"));
    getList()
  })

}


const handleClick = () => {
  nextTick(() => {
    getList();
  })
}

function handleAddAll() {
  proxy.$modal.confirm(translatePhrase("添加所有通道将包括已经添加到其他计划的通道，确定添加所有通道？")).then(function () {
    return addChannel({
      platformId: queryParams.value.platformId,
      all: true
    })
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess(translatePhrase("添加成功"));
  }).catch(() => {
  });
}

function handleRemoveAll() {
  proxy.$modal.confirm(translatePhrase("确定移除所有通道？")).then(function () {
    return delChannelForGB({
      platformId: queryParams.value.platformId,
      all: true
    })
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess(translatePhrase("移除成功"));
  }).catch(() => {
  });
}

function handleAddByDevice() {
  open.value = true
  title.value = "添加国标设备通道"
  getDeviceList()
  typeDevice.value = "addDevice"
}

function handleRemoveByDevice() {
  open.value = true
  title.value = "移除国标设备通道"
  getDeviceList()
  typeDevice.value = "removeDevice"
}

function getDeviceList() {
  loadingDevice.value = true
  listDevice(queryParamsDevice.value).then((res) => {
    deviceList.value = res.rows
    totalDevice.value = res.total
    loadingDevice.value = false
  })
}

function handleSelectionDeviceChange(selection) {
  selectionDeviceList.value = selection
  multipleDevice.value = !selection.length;
}

function handleSure() {
  let deviceIds = []
  for (let i = 0; i < selectionDeviceList.value.length; i++) {
    deviceIds.push(selectionDeviceList.value[i].id)
  }
  if (typeDevice.value === "addDevice") {
    addChannelByDevice({
      platformId: queryParams.value.platformId,
      deviceIds: deviceIds
    }).then(() => {
      proxy.$modal.msgSuccess(translatePhrase("关联成功"));
      open.value = false
      getList()
    })
  } else if (typeDevice.value === "removeDevice") {
    removeChannelByDevice({
      platformId: queryParams.value.platformId,
      deviceIds: deviceIds
    }).then(() => {
      proxy.$modal.msgSuccess(translatePhrase("取消关联成功"));
      open.value = false
      getList()
    })
  }
}

onMounted(() => {
  queryParams.value.platformId = route.params && route.params.platformId;
  getList();
})
</script>

<style scoped>

</style>
