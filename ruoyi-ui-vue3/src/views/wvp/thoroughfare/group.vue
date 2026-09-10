<template>
  <div class="thoroughfare-layout">
    <aside v-yResize class="thoroughfare-aside">
      <div class="head-container">
        <el-input v-model="groupName" placeholder="请输入分组名称" clearable prefix-icon="Search"
                  style="margin-bottom: 20px"/>
      </div>
      <div class="head-container thoroughfare-tree">
        <el-tree :data="groupOptions"
                 :props="{label: 'name', children: 'children'}"
                 :expand-on-click-node="false"
                 :filter-node-method="filterNode"
                 ref="groupTreeRef"
                 node-key="id"
                 highlight-current
                 default-expand-all
                 @node-click="handleNodeClick"/>
      </div>
    </aside>
    <main class="thoroughfare-content">
        <div class="toolbar-with-search">
          <div class="toolbar-left">
            <button
              type="button"
              class="exportBtn newBtn flexRowAC"
              :disabled="addDisabled"
              @click="handleAdd"
              v-hasPermi="['wvp:channel:addGroupChannel']"
            >
              <el-icon class="BtnImg"><Plus /></el-icon>新增
            </button>
            <button-group :button-list="toolbarButtons" />
          </div>
          <div class="searchHeight_out flexRowAC">
            <search-height-box
              keyword="query"
              placeholder="请输入关键字"
              :data="searchData"
              @handle="searchResetFn"
            />
            <export-excel-pdf />
          </div>
        </div>

        <table-self
          class="new_table"
          header-cell-class-name="header_tenant_cell"
          stripe
          v-loading="loading"
          :data="channelList"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" :width="clacPXToVW(55)" align="center"/>
          <el-table-column prop="gbName" label="名称" align="center" show-overflow-tooltip/>
          <el-table-column prop="gbDeviceId" label="编号" align="center" show-overflow-tooltip/>
          <el-table-column prop="gbManufacturer" label="厂家" align="center" show-overflow-tooltip/>
          <el-table-column label="类型" :width="clacPXToVW(110)" align="center">
            <template #default="scope">
              <el-tag effect="plain" v-if="scope.row.dataType === 1">国标设备</el-tag>
              <el-tag effect="plain" type="success" v-else-if="scope.row.dataType === 2">推流设备</el-tag>
              <el-tag effect="plain" type="warning" v-else-if="scope.row.dataType === 3">拉流代理</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" :width="clacPXToVW(90)" align="center">
            <template #default="scope">
              <el-tag v-if="scope.row.gbStatus === 'ON'">在线</el-tag>
              <el-tag type="info" v-else>离线</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="right" fixed="right" :width="clacPXToVW(120)">
            <template #default="scope">
              <div class="operateAppBox flexRowAC" style="justify-content: flex-end;">
                <div class="new_table_svg_group" @click.stop="onMap(scope.row)">
                  <span>设置位置</span>
                </div>
              </div>
            </template>
          </el-table-column>
        </table-self>

        <pagination
            v-show="total > 0"
            :total="total"
            v-model:page="queryParams.pageNum"
            v-model:limit="queryParams.pageSize"
            @pagination="getList"
        />

        <el-dialog :title="title" v-model="open" width="70%" append-to-body>
          <el-form :model="queryParamsSelect" ref="querySelectRef" :inline="true" v-show="showSearchSelect"
                   label-width="68px">
            <el-form-item label="关键字" prop="query">
              <el-input v-model="queryParamsSelect.query" placeholder="请输入关键字" clearable style="width: 240px"
                        @keyup.enter="handleSelectQuery"/>
            </el-form-item>
            <el-form-item label="类型" prop="channelType">
              <el-select v-model="queryParamsSelect.channelType" placeholder="请选择类型" style="width: 250px;"
                         default-first-option>
                <el-option label="国标设备" :value="1"></el-option>
                <el-option label="推流设备" :value="2"></el-option>
                <el-option label="拉流代理" :value="3"></el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="在线状态" prop="online">
              <el-select v-model="queryParamsSelect.online" placeholder="请选择在线状态" style="width: 250px;"
                         default-first-option>
                <el-option label="在线" value="true"></el-option>
                <el-option label="离线" value="false"></el-option>
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleSelectQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetSelectQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <el-row :gutter="10" class="mb8">
            <el-col :span="1.5">
              <el-button type="primary"
                         plain
                         icon="Select"
                         :disabled="multipleSelect"
                         @click="handleSelect">
                选择
              </el-button>
            </el-col>
            <right-toolbar v-model:showSearch="showSearchSelect" @queryTable="getChannelList"></right-toolbar>
          </el-row>

          <el-table v-loading="loadingSelect" :data="channelSelectList" @selection-change="handleSelectionSelectChange">
            <el-table-column type="selection" width="55" align="center"/>
            <el-table-column prop="gbName" label="名称" align="center"/>
            <el-table-column prop="gbDeviceId" label="编号" align="center"/>
            <el-table-column prop="gbManufacturer" label="厂家" align="center"/>
            <el-table-column prop="gbAddress" label="位置" align="center"/>
            <el-table-column label="类型" align="center">
              <template #default="scope">
                <div slot="reference" class="name-wrapper">
                  <el-tag effect="plain" v-if="scope.row.dataType === 1">国标设备</el-tag>
                  <el-tag effect="plain" type="success" v-else-if="scope.row.dataType === 2">推流设备</el-tag>
                  <el-tag effect="plain" type="warning" v-else-if="scope.row.dataType === 3">拉流代理</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" align="center">
              <template #default="scope">
                <div slot="reference" class="name-wrapper">
                  <el-tag v-if="scope.row.gbStatus === 'ON'">在线</el-tag>
                  <el-tag type="info" v-if="scope.row.gbStatus !== 'ON'">离线</el-tag>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <pagination
              v-show="totalSelect > 0"
              :total="totalSelect"
              v-model:page="queryParamsSelect.pageNum"
              v-model:limit="queryParamsSelect.pageSize"
              @pagination="getChannelList"
          />
        </el-dialog>
    </main>

    <el-dialog title="修改地址" v-model="showMap" width="50%" append-to-body>
      <MapGaoDe ref="MapContainer" @update-value="updateDialogMap" :position="position" :toponym="formMap.gbAddress"/>
    </el-dialog>
  </div>
</template>

<script setup name="Group">
import {queryForTree} from "../../../api/wvp/group.js";
import MapGaoDe from "@/components/MapGaoDe/index.vue";
import {
  addChannelToGroup,
  deleteChannelToGroup,
  queryListByCivilCode,
  queryListByParentId, updateChannelData
} from "../../../api/wvp/channel.js";
import { Plus } from '@element-plus/icons-vue'
import { clacPXToVW } from "@/utils/index";

const {proxy} = getCurrentInstance();

const groupName = ref('')
const groupOptions = ref([]);
const channelList = ref([]);
const loading = ref(true);
const total = ref(0);
const groupDeviceId = ref('');
const businessGroup = ref('');
const selectionList = ref([]);
const multiple = ref(true);
const addDisabled = ref(true);
const open = ref(false);
const title = ref("");
const dataType = ref('group');
const searchData = ref([
  {
    label: '类型',
    value: 'channelType',
    type: 'select',
    option: [
      { label: '国标设备', value: 1 },
      { label: '推流设备', value: 2 },
      { label: '拉流代理', value: 3 }
    ],
    default: undefined
  },
  {
    label: '在线状态',
    value: 'online',
    type: 'select',
    option: [
      { label: '在线', value: 'true' },
      { label: '离线', value: 'false' }
    ],
    default: undefined
  }
]);
const toolbarButtons = computed(() => [
  {
    name: '删除',
    svg: 'delete',
    disabled: multiple.value,
    permi: ['wvp:channel:deleteGroupChannel'],
    clickFn: () => handleDelete()
  }
]);

const channelSelectList = ref([]);
const loadingSelect = ref(true);
const totalSelect = ref(0);
const showSearchSelect = ref(true);
const multipleSelect = ref(true);
const selectionSelectList = ref([]);

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    query: undefined,
    online: undefined,
    channelType: undefined,
    groupDeviceId: ' '
  },
  rules: {},

  queryParamsSelect: {
    pageNum: 1,
    pageSize: 10,
    query: undefined,
    online: undefined,
    channelType: undefined,
    groupDeviceId: undefined,
  }
});

const {queryParams, form, rules, queryParamsSelect} = toRefs(data);

/**
 * map
 * @type {*}
 */
const formMap = ref({});
const toponym = ref('');
const showMap = ref(false);
const position = ref(null);
const MapContainer = ref(null);
function onMap(row) {
  formMap.value = row;
  position.value = [formMap.value.gbLongitude, formMap.value.gbLatitude];
  toponym.value = form.value.gbAddress;
  showMap.value = true;
  Create();
}
const Create = () => {
  MapContainer.value?.inGaDeMap();
};
const Destruction = () => {
  MapContainer.value?.Destruction();
};
const updateDialogMap = (value) => {
  formMap.value.gbAddress = value.address + value.detailedStreet;
  formMap.value.gbLongitude = value.lng;
  formMap.value.gbLatitude = value.lat;
  position.value = [formMap.value.gbLongitude, formMap.value.gbLatitude];
  toponym.value = formMap.value.gbAddress;
  updateChannelData(formMap.value).then(res => {
    showMap.value = false;
    Destruction();
    proxy.$modal.msgSuccess("操作成功");
  }).catch(() => {
    proxy.$modal.msgError("操作失败");
  })
}


function getList() {
  loading.value = true
  queryListByParentId(queryParams.value).then((res) => {
    channelList.value = res.rows
    total.value = res.total
    loading.value = false
  })
}

/** 根据名称筛选树 */
watch(groupName, val => {
  proxy.$refs["groupTreeRef"].filter(val);
});

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

function searchResetFn(val) {
  queryParams.value.pageNum = 1;
  queryParams.value.query = val.query || undefined;
  queryParams.value.channelType = val.channelType || undefined;
  queryParams.value.online = val.online || undefined;
  getList();
}

/** 选择条数  */
function handleSelectionChange(selection) {
  if(queryParams.value.groupDeviceId === null){
    multiple.value = true
  }else {
    multiple.value = !selection.length;
  }
  selectionList.value = selection
}

function handleDelete() {
  let channels = []
  for (let i = 0; i < selectionList.value.length; i++) {
    channels.push(selectionList.value[i].gbId)
  }
  proxy.$modal.confirm('是否删除选择的数据？').then(function () {
    return deleteChannelToGroup({channelIds: channels});
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功");
  }).catch(() => {
  });
}

/** 通过条件过滤节点  */
const filterNode = (value, data) => {
  if (!value) return true;
  return data.name.indexOf(value) !== -1;
};

/** 节点单击事件 */
function handleNodeClick(data) {
  if (data.deviceId != null || data.deviceId != undefined)  {
    queryParams.value.groupDeviceId = data.deviceId;
    addDisabled.value = false
  } else {
    queryParams.value.groupDeviceId = null;
    addDisabled.value = true
  }

  groupDeviceId.value = queryParams.value.groupDeviceId;
  businessGroup.value = data.businessGroup;
  handleQuery();
}

/** 新增按钮操作 */
function handleAdd() {
  if (groupDeviceId.value === "" || groupDeviceId.value === ' ') {
    proxy.$modal.msgError("请选择左侧业务分组");
    return;
  }

  title.value = "添加国标通道";
  open.value = true;

  getChannelList()
}

function getChannelList() {
  if (dataType.value === "civilCode") {
    loadingSelect.value = true
    queryListByCivilCode(queryParamsSelect.value).then((res) => {
      channelSelectList.value = res.rows
      totalSelect.value = res.total
      loadingSelect.value = false
    })
  } else {
    loadingSelect.value = true
    queryListByParentId(queryParamsSelect.value).then((res) => {
      channelSelectList.value = res.rows
      totalSelect.value = res.total
      loadingSelect.value = false
    })
  }
}

/** 搜索按钮操作 */
function handleSelectQuery() {
  queryParams.value.pageNum = 1;
  getChannelList();
}

/** 重置按钮操作 */
function resetSelectQuery() {
  proxy.resetForm("querySelectRef");
  handleSelectQuery();
}

function handleSelectionSelectChange(selection) {
  selectionSelectList.value = selection;
  multipleSelect.value = !selection.length;
}

function handleSelect() {
  proxy.$modal.msgSuccess("选择成功");
  open.value = false;
  addChannelToCivilCode(groupDeviceId.value, businessGroup.value, selectionSelectList.value)
}

function addChannelToCivilCode(groupDeviceId, businessGroup, data) {
  let channels = []
  for (let i = 0; i < data.length; i++) {
    channels.push(data[i].gbId)
  }
  addChannelToGroup({
    parentId: groupDeviceId,
    businessGroup: businessGroup,
    channelIds: channels
  }).then(() => {
    getList()
  })
}

onMounted(() => {
  queryForTree({}).then((res) => {
    let data = [
      {
        name: "根资源组",
        children: []
      }
    ]
    data[0].children = proxy.handleTree(res.data, "id")
    groupOptions.value = data
  })

  getList()
})
</script>

<style scoped>
.thoroughfare-layout {
  display: flex;
  align-items: stretch;
  gap: 20px;
  min-height: calc(100vh - 160px);
  background: #fff;
}

.thoroughfare-aside {
  width: 280px;
  flex-shrink: 0;
  padding-right: 20px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
}

.thoroughfare-tree {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: #fff;
}

.thoroughfare-content {
  flex: 1;
  min-width: 0;
  background: #fff;
}
</style>
