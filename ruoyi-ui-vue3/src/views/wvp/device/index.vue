<template>
  <DeviceClassificationLayout protocol-type="GB28181" :selected-device-keys="classificationDeviceKeys" @filter-change="handleClassificationFilter" @assigned="getList">
  <div class="app-container">
    <div class="toolbar-with-search">
      <div class="toolbar-left">
        <button type="button" class="exportBtn newBtn flexRowAC" @click="showInfo" v-hasPermi="['wvp:server:configInfo']">
          <el-icon class="BtnImg"><InfoFilled /></el-icon>{{ $tp("平台信息") }}
        </button>
        <button
          type="button"
          class="exportBtn newBtn flexRowAC Btn2"
          :disabled="multiple"
          @click="handleBatchDelete"
          v-hasPermi="['wvp:device:remove']"
        >
          <el-icon class="BtnImg"><Delete /></el-icon>{{ $tp("删除") }}
        </button>
      </div>
      <div class="searchHeight_out flexRowAC">
        <search-height-box
          keyword="name"
          :placeholder="$tp('请输入设备名称等关键词')"
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
      :data="deviceList"
      current-row-key="id"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" :width="clacPXToVW(55)" align="center"/>
      <el-table-column type="index" :label="$tp('编号')" :width="clacPXToVW(70)" align="center"/>
      <el-table-column :label="$tp('所属部门')" align="center" prop="deptName" show-overflow-tooltip/>
      <el-table-column prop="name" :label="$tp('名称')" :width="clacPXToVW(100)" align="center" show-overflow-tooltip/>
      <el-table-column prop="deviceId" :label="$tp('设备编号')" align="center" :width="clacPXToVW(150)" show-overflow-tooltip/>
      <el-table-column :label="$tp('地址')" align="center" prop="addressMap" :width="clacPXToVW(150)" show-overflow-tooltip/>
      <el-table-column :label="$tp('IP地址')" :width="clacPXToVW(160)" align="center" show-overflow-tooltip>
        <template #default="scope">
          <el-tag v-if="scope.row.hostAddress">{{ scope.row.hostAddress }}</el-tag>
          <el-tag v-else type="info">{{ $tp("未知") }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="manufacturer" :label="$tp('厂家')" :width="clacPXToVW(90)" align="center" show-overflow-tooltip/>
      <el-table-column prop="transport" :label="$tp('信令传输模式')" :width="clacPXToVW(110)" align="center"/>
      <el-table-column :label="$tp('流传输模式')" :width="clacPXToVW(150)" align="center">
        <template #default="scope">
          <el-select
            v-if="checkPermi(['wvp:device:updateTransport'])"
            @change="transportChange(scope.row)"
            v-model="scope.row.streamMode"
            :placeholder="$tp('请选择')"
            style="width: 120px"
          >
            <el-option key="UDP" label="UDP" value="UDP"/>
            <el-option key="TCP-ACTIVE" :label="$tp('TCP主动模式')" value="TCP-ACTIVE"/>
            <el-option key="TCP-PASSIVE" :label="$tp('TCP被动模式')" value="TCP-PASSIVE"/>
          </el-select>
          <template v-else>
            <el-tag v-if="scope.row.streamMode === 'UDP'">UDP</el-tag>
            <el-tag v-else-if="scope.row.streamMode === 'TCP-ACTIVE'">{{ $tp("TCP主动模式") }}</el-tag>
            <el-tag v-else-if="scope.row.streamMode === 'TCP-PASSIVE'">{{ $tp("TCP被动模式") }}</el-tag>
          </template>
        </template>
      </el-table-column>
      <el-table-column :label="$tp('通道数')" :width="clacPXToVW(80)" align="center">
        <template #default="scope">{{ scope.row.channelCount }}</template>
      </el-table-column>
      <el-table-column :label="$tp('状态')" :width="clacPXToVW(80)" align="center">
        <template #default="scope">
          <el-tag v-if="scope.row.onLine">{{ $tp("在线") }}</el-tag>
          <el-tag type="info" v-else>{{ $tp("离线") }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$tp('订阅')" :min-width="clacPXToVW(220)" align="center">
        <template #default="scope">
          <el-checkbox
            v-if="checkPermi(['wvp:device:subscribeCatalog'])"
            :label="$tp('目录')"
            :checked="scope.row.subscribeCycleForCatalog > 0"
            @change="(e)=>subscribeForCatalog(scope.row.id, e)"
          />
          <el-checkbox
            v-else
            :label="$tp('目录')"
            :checked="scope.row.subscribeCycleForCatalog > 0"
            disabled
          />
          <el-checkbox
            v-if="checkPermi(['wvp:device:subscribeMobilePosition'])"
            :label="$tp('位置')"
            :checked="scope.row.subscribeCycleForMobilePosition > 0"
            @change="(e)=>subscribeForMobilePosition(scope.row.id, e)"
          />
          <el-checkbox
            v-else
            :label="$tp('位置')"
            disabled
            :checked="scope.row.subscribeCycleForMobilePosition > 0"
          />
          <el-checkbox :label="$tp('报警')" disabled :checked="scope.row.subscribeCycleForAlarm > 0"/>
        </template>
      </el-table-column>
      <el-table-column prop="keepaliveTime" :label="$tp('最近心跳')" :width="clacPXToVW(150)" align="center" show-overflow-tooltip/>
      <el-table-column prop="registerTime" :label="$tp('最近注册')" :width="clacPXToVW(150)" align="center" show-overflow-tooltip/>
      <el-table-column :label="$tp('操作')" align="right" fixed="right" :width="clacPXToVW(240)">
        <template #default="scope">
          <div class="operateAppBox flexRowAC" style="justify-content: flex-end;">
            <div
              class="new_table_svg_group"
              :class="{ 'is-disabled': scope.row.online === 0 }"
              @click.stop="scope.row.online !== 0 && refDevice(scope.row)"
              v-hasPermi="['wvp:device:sync']"
            >
              <span>{{ $tp("刷新") }}</span>
            </div>
            <div
              class="new_table_svg_group"
              @click.stop="showChannelList(scope.row)"
              v-if="checkPermi(['wvp:device:channels'])"
            >
              <span>{{ $tp("通道") }}</span>
            </div>
            <div
              class="new_table_svg_group"
              @click.stop="handleUpdate(scope.row)"
              v-if="checkPermi(['wvp:device:edit'])"
            >
              <span>{{ $tp("修改") }}</span>
            </div>
            <el-dropdown
              @command="(command)=>{moreClick(command, scope.row)}"
              v-if="checkPermi(['wvp:device:remove','wvp:control:guardApi','wvp:config:cdownloadApi','wvp:device:edit'])"
            >
              <div class="new_table_svg_group" @click.stop>
                <span>{{ $tp("更多") }}</span>
                <el-icon><ArrowDown /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="handleMap" v-if="checkPermi(['wvp:device:edit'])">{{ $tp("修改位置") }}</el-dropdown-item>
                  <el-dropdown-item command="delete" style="color: #f56c6c" v-if="checkPermi(['wvp:device:remove'])">{{ $tp("删除") }}</el-dropdown-item>
                  <el-dropdown-item command="setGuard" :disabled="!scope.row.onLine" v-if="checkPermi(['wvp:control:guardApi'])">{{ $tp("布防") }}</el-dropdown-item>
                  <el-dropdown-item command="resetGuard" :disabled="!scope.row.onLine" v-if="checkPermi(['wvp:control:guardApi'])">{{ $tp("撤防") }}</el-dropdown-item>
                  <el-dropdown-item command="syncBasicParam" :disabled="!scope.row.onLine" v-if="checkPermi(['wvp:config:cdownloadApi'])">{{ $tp("基础配置同步") }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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

    <!-- 添加或修改参数配置对话框 -->
    <el-dialog :title="$tp(title)" v-model="open" width="32%" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item :label="$tp('设备编号')" prop="deviceId">
          <el-input v-model="form.deviceId" disabled></el-input>
        </el-form-item>
        <el-form-item :label="$tp('所属部门')" prop="deptId">
          <el-tree-select v-model="form.deptId" :data="enabledDeptOptions" :props="{ value: 'id', label: 'label', children: 'children' }" value-key="id" :placeholder="$tp('请选择归属部门')" check-strictly />
        </el-form-item>
        <el-form-item :label="$tp('设备名称')" prop="name">
          <el-input v-model="form.name" clearable></el-input>
        </el-form-item>
        <el-form-item :label="$tp('密码')" prop="password">
          <el-input v-model="form.password" clearable></el-input>
        </el-form-item>
        <el-form-item :label="$tp('收流IP')" prop="sdpIp">
          <el-input v-model="form.sdpIp" clearable></el-input>
        </el-form-item>
        <el-form-item :label="$tp('厂家')" prop="manufacturer">
          <el-input v-model="form.manufacturer" clearable></el-input>
        </el-form-item>
        <el-form-item :label="$tp('流媒体ID')" prop="mediaServerId">
          <el-select v-model="form.mediaServerId" style="float: left; width: 100%">
            <el-option key="auto" :label="$tp('自动负载最小')" value="auto"></el-option>
            <el-option
                v-for="item in mediaServerList"
                :key="item.id"
                :label="item.id"
                :value="item.id">
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item :label="$tp('字符集')" prop="charset">
          <el-select v-model="form.charset" style="float: left; width: 100%">
            <el-option key="GB2312" label="GB2312" value="gb2312"></el-option>
            <el-option key="UTF-8" label="UTF-8" value="utf-8"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item :label="$tp('其他选项')">
          <el-checkbox :label="$tp('SSRC校验')" v-model="form.ssrcCheck" style="float: left"></el-checkbox>
          <el-checkbox :label="$tp('作为消息通道')" v-model="form.asMessageChannel" style="float: left"></el-checkbox>
          <el-checkbox :label="$tp('收到ACK后发流')" v-model="form.broadcastPushAfterAck" style="float: left"></el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">{{ $tp("确 定") }}</el-button>
          <el-button @click="cancel">{{ $tp("取 消") }}</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
        :title="$tp('国标服务信息')"
        width="65%"
        v-model="showDialog"
        append-to-body
    >
      <template #header><div class="gb-dialog-heading"><span>{{ $tp("国标服务信息") }}</span><el-button link type="primary" @click="showHelp = true">{{ $tp("帮助说明") }}</el-button></div></template>
      <div id="shared" style="margin-top: 1rem;">
        <el-descriptions v-if="configInfoData.sip" :span="2" border>
          <el-descriptions-item :label="$tp('编号')">{{ configInfoData.sip.id }}</el-descriptions-item>
          <el-descriptions-item :label="$tp('域')">{{ configInfoData.sip.domain }}</el-descriptions-item>
          <el-descriptions-item label="IP">{{ configInfoData.sip.showIp }}</el-descriptions-item>
          <el-descriptions-item :label="$tp('端口')">{{ configInfoData.sip.port }}</el-descriptions-item>
          <el-descriptions-item :label="$tp('密码')">
            <el-tag size="small">{{ configInfoData.sip.password }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>

    <el-dialog
        :title="$tp('刷新设备')"
        width="20%"
        v-model="showProgress"
        append-to-body
    >
      <div style="display:flex;align-items: center;justify-content: center">
        <el-progress type="dashboard" :percentage="percentage" :color="colors"/>
      </div>
      <div style="display:flex;align-items: center;justify-content: center">
        {{ msg }}
      </div>
    </el-dialog>


    <GbHelpDialog v-model="showHelp" mode="service" />
    <el-dialog :title="$tp('修改地址')" v-model="showMap" width="50%" append-to-body>
      <MapGaoDe ref="MapContainer" @update-value="updateDialogMap" :position="position" :toponym="form.address"/>
    </el-dialog>
  </div>
  </DeviceClassificationLayout>
</template>

<script setup name="Device">
import GbHelpDialog from '@/components/GbHelpDialog.vue'
const showHelp = ref(false)
import {checkPermi} from "@/utils/permission";
import MapGaoDe from "@/components/MapGaoDe/index.vue";
import {
  batchDeleteDevice,
  deleteDevice,
  devicesSync,
  getDeviceById,
  listDevice,
  subscribeCatalog,
  subscribeMobilePosition,
  syncStatus,
  updateDevice,
  updateTransport,
} from "../../../api/wvp/device.js";
import {configInfo, getOnlineMediaServerList} from "../../../api/wvp/wvpMediaServer.js";
import router from "@/router";
import {guardApi} from "../../../api/wvp/control.js";
import {ElMessage} from 'element-plus'
import {configDownloadApi} from "../../../api/wvp/config.js";
import {deptTreeSelect} from "@/api/system/user";
import DeviceClassificationLayout from '@/components/DeviceClassificationLayout/index.vue'
import { clacPXToVW } from "@/utils/index";
import { ArrowDown, Delete, InfoFilled } from '@element-plus/icons-vue'

const {proxy} = getCurrentInstance();

const deviceList = ref([]);
const open = ref(false);
const loading = ref(true);
const total = ref(0);
const title = ref("");
const msg = ref("");
const configInfoData = ref({});
const percentage = ref(0)

const showDialog = ref(false);
const showProgress = ref(false);

const deptOptions = ref(undefined);
const enabledDeptOptions = ref(undefined);
const searchData = ref([
  {
    get label() { return translatePhrase("所属部门") },
    value: 'deptId',
    type: 'tree-select',
    option: [],
    default: undefined
  },
  {
    get label() { return translatePhrase("地址") },
    value: 'ip',
    type: 'text',
    default: undefined
  },
  {
    get label() { return translatePhrase("厂家") },
    value: 'manufacturer',
    type: 'text',
    default: undefined
  },
  {
    get label() { return translatePhrase("在线状态") },
    value: 'status',
    type: 'select',
    option: [
      { get label() { return translatePhrase("在线") }, value: '1' },
      { get label() { return translatePhrase("离线") }, value: '0' }
    ],
    default: undefined
  }
]);

const ids = ref([]);
const classificationDeviceKeys = ref([]);
function handleClassificationFilter(filter) { Object.assign(queryParams.value, filter, { pageNum: 1 }); getList(); }
const multiple = ref(true);

const colors = [
  {color: '#f56c6c', percentage: 20},
  {color: '#e6a23c', percentage: 40},
  {color: '#5cb87a', percentage: 60},
  {color: '#1989fa', percentage: 80},
  {color: '#6f7ad3', percentage: 100},
]

const mediaServerList = ref([]);

const position = ref(null);
const MapContainer = ref(null);
const toponym = ref('');
const showMap = ref(false);

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: undefined,
    status: undefined,
    ip: undefined,
    manufacturer: undefined,
    deptId: undefined,
  },
  rules: {
    deviceId: [{required: true, get message() { return translatePhrase("请输入设备编号") }, trigger: "blur"}],
    deptId: [{ required: true, get message() { return translatePhrase("请选择所属部门") }, trigger: 'blur' }],
  }
});

const {queryParams, form, rules} = toRefs(data);

/** 查询列表 */
function getList() {
  loading.value = true;
  listDevice(queryParams.value).then(response => {
    deviceList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

/** 查询部门下拉树结构 */
function getDeptTree() {
  deptTreeSelect().then(response => {
    deptOptions.value = response.data;
    enabledDeptOptions.value = filterDisabledDept(JSON.parse(JSON.stringify(response.data)));
    const deptField = searchData.value.find(item => item.value === 'deptId');
    if (deptField) {
      deptField.option = enabledDeptOptions.value || [];
    }
  });
};

/** 过滤禁用的部门 */
function filterDisabledDept(deptList) {
  return deptList.filter(dept => {
    if (dept.disabled) {
      return false;
    }
    if (dept.children && dept.children.length) {
      dept.children = filterDisabledDept(dept.children);
    }
    return true;
  });
};

/** 高级搜索 */
function searchResetFn(val) {
  queryParams.value.pageNum = 1;
  queryParams.value.name = val.name || undefined;
  queryParams.value.deptId = val.deptId || undefined;
  queryParams.value.ip = val.ip || undefined;
  queryParams.value.manufacturer = val.manufacturer || undefined;
  queryParams.value.status = val.status || undefined;
  getList();
}

/** 取消按钮 */
function cancel() {
  open.value = false;
  reset();
}

/** 表单重置 */
function reset() {
  form.value = {
    deviceId: undefined,
    name: undefined,
    password: undefined,
    sdpIp: undefined,
    mediaServerId: undefined,
    charset: undefined,
    ssrcCheck: undefined,
    asMessageChannel: undefined,
    broadcastPushAfterAck: undefined,
    addressMap: undefined,
    lng: undefined,
    lat: undefined,
  };
  proxy.resetForm("formRef");
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  getMediaServerList()
  getDeviceById(row.deviceId).then(response => {
    form.value = response.data
    title.value = "修改设备";
    open.value = true;
  })
}

/**
 * 获取流媒体服务列表
 */
function getMediaServerList() {
  getOnlineMediaServerList().then(response => {
    mediaServerList.value = response.data;
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["formRef"].validate(valid => {
    if (valid) {
      updateDevice(form.value).then(response => {
        getList();
        proxy.$modal.msgSuccess(translatePhrase("修改成功"));
        open.value = false;
      })
    }
  });
}

/** 删除按钮操作 */
function handleDelete(row) {
  let msg = "确定删除此设备？"
  if (row.online !== 0) {
    msg = "在线设备删除后仍可通过注册再次上线。如需彻底删除请先将设备离线。确定删除此设备？"
  }
  const deviceId = row.deviceId;
  proxy.$modal.confirm(msg).then(function () {
    return deleteDevice(deviceId);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess(translatePhrase("删除成功"));
  }).catch(() => {
  });
}

/**
 * 修改传输方式
 *
 * @param row
 */
function transportChange(row) {
  updateTransport({deviceId: row.deviceId, streamMode: row.streamMode}).then(response => {
    console.log(`修改传输方式为 ${row.streamMode}：${row.deviceId} 成功`);
  });
}

/**
 * 开启/关闭目录订阅
 *
 * @param data
 * @param value
 */
function subscribeForCatalog(data, value) {
  subscribeCatalog({id: data, cycle: value ? 60 : 0}).then(response => {

  })
}

/**
 * 开启/关闭移动位置订阅
 *
 * @param data
 * @param value
 */
function subscribeForMobilePosition(data, value) {
  subscribeMobilePosition({id: data, cycle: value ? 60 : 0, interval: value ? 5 : 0}).then(response => {

  })
}

/**
 * 获取平台配置信息
 */
function showInfo() {
  configInfo().then(response => {
    showDialog.value = true
    configInfoData.value = response.data
  })
}

/**
 * 刷新对应设备 percentage
 *
 * @param itemData
 */
async function refDevice(itemData) {
  let intervalId = null;
  const res = await devicesSync(itemData.deviceId);
  showProgress.value = true;
  msg.value = res.msg;
  percentage.value = 0;
  intervalId = setInterval(async () => {
    try {
      const ans = await syncStatus(itemData.deviceId);
      if (ans.msg === '成功') {
        clearInterval(intervalId);
        let progressIntervalId = null;
        progressIntervalId = setInterval(() => {
          if (percentage.value < 100) {
            percentage.value += 20;
          } else {
            clearInterval(progressIntervalId);
            msg.value = ans.msg;
          }
        }, 300);
      }
    } catch (e) {
      clearInterval(intervalId);
      msg.value = "同步通道失败";
    }
  }, 500);

}

/**
 * 设备国标编号
 *
 * @param deviceId
 * @returns {Promise<void>}
 */
async function getTooltipContent(deviceId) {

  // syncStatus(deviceId).then(response => {
  //
  // })
}

function moreClick(command, itemData) {
  if (command === "setGuard") {
    setGuard(itemData)
  } else if (command === "resetGuard") {
    resetGuard(itemData)
  } else if (command === "delete") {
    handleDelete(itemData)
  } else if (command === "syncBasicParam") {
    syncBasicParam(itemData)
  }else if(command === "handleMap"){
    handleMap(itemData)
  }
}

/**
 * 修改位置
 *
 * @param row
 */
function handleMap(row){
  form.value = row;
  position.value = [form.value.lng, form.value.lat];
  toponym.value = form.value.addressMap;
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
  form.value.addressMap = value.address + value.detailedStreet;
  form.value.lng = value.lng;
  form.value.lat = value.lat;
  position.value = [form.value.lng, form.value.lat];
  toponym.value = form.value.addressMap;
  updateDevice(form.value).then(res => {
    showMap.value = false;
    Destruction();
    proxy.$modal.msgSuccess(translatePhrase("操作成功"));
  }).catch(() => {
    proxy.$modal.msgError(translatePhrase("操作失败"));
  })
}

/**
 * 显示通道列表
 *
 * @param row
 */
function showChannelList(row) {
  router.push(`/channel/list/index/${row.deviceId}/0`);
}

function setGuard(row) {
  guardApi({
    deviceId: row.deviceId,
    guardCmdStr: 'SetGuard',
  }).then(() => {
    ElMessage({
      message: translatePhrase("布防成功"),
      type: 'success',
    })
  })
}

function resetGuard(row) {
  guardApi({
    deviceId: row.deviceId,
    guardCmdStr: 'ResetGuard',
  }).then(() => {
    ElMessage({
      message: translatePhrase("撤防成功"),
      type: 'success',
    })
  })
}

function syncBasicParam(row) {
  configDownloadApi({
    deviceId: row.deviceId,
    configType: "BasicParam",
  }).then((res) => {
    ElMessage({
      get message() { return translatePhrase("配置已同步，当前心跳间隔：{interval}，心跳次数：{count}", { interval: res.BasicParam.HeartBeatInterval, count: res.BasicParam.HeartBeatCount }) },
      type: 'success',
    })
  })
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.deviceId);
  classificationDeviceKeys.value = selection.map(item => String(item.id));
  multiple.value = !selection.length;
}

const handleBatchDelete = () => {
  const _ids = ids.value;
  proxy.$modal.confirm(translatePhrase("是否确认删除国标设备编号为\"") + _ids + translatePhrase("\"的数据项？")).then(function () {
    return batchDeleteDevice(_ids);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess(translatePhrase("删除成功"));
  }).catch(() => {
  });
}

getDeptTree();
getList();
</script>

<style scoped>
.example-showcase .el-dropdown-link {
  cursor: pointer;
  color: var(--el-color-primary);
  display: flex;
  align-items: center;
}
</style>
