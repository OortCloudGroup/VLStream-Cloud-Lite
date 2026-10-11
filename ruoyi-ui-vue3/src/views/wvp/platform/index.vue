<template>
  <div class="app-container">
    <div class="toolbar-with-search">
      <div class="toolbar-left">
        <button type="button" class="exportBtn newBtn flexRowAC" @click="handleAdd" v-hasPermi="['wvp:platform:add']">
          <el-icon class="BtnImg"><Plus /></el-icon>{{ $tp("新增") }}
        </button>
      </div>
      <div class="searchHeight_out flexRowAC">
        <search-height-box
          keyword="query"
          :placeholder="$tp('请输入关键字')"
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
      :data="platformList"
      current-row-key="id"
    >
      <el-table-column prop="name" :label="$t('gbPlatform.name')" align="center" show-overflow-tooltip></el-table-column>
      <el-table-column prop="serverGBId" :label="$t('gbPlatform.platformId')" align="center" show-overflow-tooltip></el-table-column>
      <el-table-column :label="$t('gbPlatform.enabled')" align="center">
        <template #default="scope">
          <div slot="reference" class="name-wrapper">
            <el-tag v-if="scope.row.enable">{{ $tp("已启用") }}</el-tag>
            <el-tag type="info" v-if="!scope.row.enable">{{ $tp("未启用") }}</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column :label="$t('gbPlatform.status')" align="center">
        <template #default="scope">
          <div slot="reference" class="name-wrapper">
            <el-tag v-if="scope.row.status">{{ $tp("在线") }}</el-tag>
            <el-tag type="info" v-if="!scope.row.status">{{ $tp("离线") }}</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column :label="$t('gbPlatform.networkAddress')" align="center" show-overflow-tooltip>
        <template #default="scope">
          <div slot="reference" class="name-wrapper">
            <el-tag>{{ scope.row.serverIp }}:{{ scope.row.serverPort }}</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="deviceGBId" :label="$t('gbPlatform.deviceGBId')" align="center" show-overflow-tooltip></el-table-column>
      <el-table-column prop="transport" :label="$t('gbPlatform.transport')" align="center"></el-table-column>
      <el-table-column prop="channelCount" :label="$t('gbPlatform.channelCount')" align="center"></el-table-column>
      <el-table-column :label="$t('gbPlatform.subscriptions')" :width="clacPXToVW(120)" align="center">
        <template #default="scope">
          <i v-if="scope.row.alarmSubscribe" style="font-size: 20px" :title="$tp('报警订阅')"
             class="iconfont icon-gbaojings subscribe-on "></i>
          <i v-if="!scope.row.alarmSubscribe" style="font-size: 20px" :title="$tp('报警订阅')"
             class="iconfont icon-gbaojings subscribe-off "></i>
          <i v-if="scope.row.catalogSubscribe" :title="$tp('目录订阅')" class="iconfont icon-gjichus subscribe-on"></i>
          <i v-if="!scope.row.catalogSubscribe" :title="$tp('目录订阅')" class="iconfont icon-gjichus subscribe-off"></i>
          <i v-if="scope.row.mobilePositionSubscribe" :title="$tp('位置订阅')"
             class="iconfont icon-gxunjians subscribe-on"></i>
          <i v-if="!scope.row.mobilePositionSubscribe" :title="$tp('位置订阅')"
             class="iconfont icon-gxunjians subscribe-off"></i>
        </template>
      </el-table-column>
      <el-table-column :label="$tp('操作')" align="right" fixed="right" :width="clacPXToVW(220)">
        <template #default="scope">
          <div class="operateAppBox flexRowAC" style="justify-content: flex-end;">
            <div class="new_table_svg_group" @click.stop="handleEdit(scope.row)" v-hasPermi="['wvp:platform:edit']">
              <el-icon><Edit /></el-icon>
              <span>{{ $tp("编辑") }}</span>
            </div>
            <el-dropdown
              @command="(command)=>{moreClick(command, scope.row)}"
              v-if="checkPermi(['wvp:platform:channelList', 'wvp:platform:push', 'wvp:platform:delete'])"
            >
              <div class="new_table_svg_group" @click.stop>
                <span>{{ $tp("更多") }}</span>
                <el-icon><ArrowDown /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="chooseChannel" v-if="checkPermi(['wvp:platform:channelList'])">{{ $tp("通道共享") }}</el-dropdown-item>
                  <el-dropdown-item command="pushChannelFun" v-if="checkPermi(['wvp:platform:push'])">{{ $tp("推送通道") }}</el-dropdown-item>
                  <el-dropdown-item command="handleDelete" v-if="checkPermi(['wvp:platform:delete'])">{{ $tp("删除") }}</el-dropdown-item>
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
        v-model:page="queryParams.page"
        v-model:limit="queryParams.count"
        @pagination="getList"
    />

    <el-dialog :title="platformTitle" v-model="open" width="min(92vw, 1040px)" class="gb-platform-dialog" append-to-body>
      <template #header="{ titleId, titleClass }">
        <div class="gb-dialog-heading">
          <span :id="titleId" :class="titleClass">{{ platformTitle }}</span>
          <el-button link type="primary" @click="showHelp = true"><el-icon class="gb-help-icon"><QuestionFilled /></el-icon>{{ $t('gbPlatform.help') }}</el-button>
        </div>
      </template>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="gb-platform-form">
        <el-row>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.name')" prop="name">
              <el-input v-model="form.name" :placeholder="platformExample.name"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.serverGBId')" prop="serverGBId">
              <el-input v-model="form.serverGBId" :placeholder="platformExample.serverGBId" clearable @input="serverGBIdChange"></el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.serverGBDomain')" prop="serverGBDomain">
              <el-input v-model="form.serverGBDomain" :placeholder="platformExample.serverGBDomain" clearable></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.serverIp')" prop="serverIp">
              <el-input v-model="form.serverIp" :placeholder="platformExample.serverIp" clearable></el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.serverPort')" prop="serverPort">
              <el-input v-model="form.serverPort" :placeholder="platformExample.serverPort" clearable type="number"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.deviceGBId')" prop="deviceGBId">
              <el-input v-model="form.deviceGBId" :placeholder="platformExample.deviceGBId" clearable @input="deviceGBIdChange"></el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.deviceIp')" prop="deviceIp">
              <el-select v-model="form.deviceIp" :placeholder="$t('gbPlatform.ipHint')" style="width: 100%">
                <el-option
                    v-for="ip in deviceIps"
                    :key="ip"
                    :label="ip"
                    :value="ip">
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.devicePort')" prop="devicePort">
              <el-input v-model="form.devicePort" :disabled="true" type="number"></el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.username')" prop="username">
              <el-input v-model="form.username" :placeholder="$t('gbPlatform.usernameHint')"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.password')" prop="password">
              <el-input v-model="form.password" :placeholder="$t('gbPlatform.passwordHint')" show-password></el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.expires')" prop="expires">
              <el-input v-model="form.expires" :placeholder="platformExample.expires"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.keepTimeout')" prop="keepTimeout">
              <el-input v-model="form.keepTimeout" :placeholder="platformExample.keepTimeout"></el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.sendStreamIp')" prop="sendStreamIp">
              <el-input v-model="form.sendStreamIp" :placeholder="platformExample.sendStreamIp"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.transport')" prop="transport">
              <el-select
                  v-model="form.transport"
                  style="width: 100%"
                  :placeholder="$t('gbPlatform.transportHint')"
              >
                <el-option label="UDP" value="UDP"></el-option>
                <el-option label="TCP" value="TCP"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.secrecy')">
              <el-select v-model="form.secrecy" style="width: 100%" :placeholder="$t('gbPlatform.secrecyHint')">
                <el-option :label="$t('gbPlatform.public')" :value="0"></el-option>
                <el-option :label="$t('gbPlatform.secret')" :value="1"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.catalogGroup')" prop="catalogGroup">
              <el-select
                  v-model="form.catalogGroup"
                  style="width: 100%"
                  :placeholder="$t('gbPlatform.groupHint')"
              >
                <el-option label="1" value="1"></el-option>
                <el-option label="2" value="2"></el-option>
                <el-option label="4" value="4"></el-option>
                <el-option label="8" value="8"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.characterSet')" prop="characterSet">
              <el-select
                  v-model="form.characterSet"
                  style="width: 100%"
                  :placeholder="$t('gbPlatform.charsetHint')"
              >
                <el-option label="GB2312" value="GB2312"></el-option>
                <el-option label="UTF-8" value="UTF-8"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.civilCode')" prop="civilCode">
              <el-input v-model="form.civilCode" clearable></el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.manufacturer')" prop="manufacturer">
              <el-input v-model="form.manufacturer" clearable></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.model')" prop="model">
              <el-input v-model="form.model" clearable></el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.address')" prop="address">
              <el-input v-model="form.address" clearable></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('gbPlatform.options')">
              <div class="gb-platform-options">
                <el-checkbox :label="$t('gbPlatform.enable')" v-model="form.enable" @change="checkExpires"></el-checkbox>
                <el-checkbox :label="$t('gbPlatform.ptz')" v-model="form.ptz"></el-checkbox>
                <el-checkbox :label="$t('gbPlatform.rtcp')" v-model="form.rtcp" @change="rtcpCheckBoxChange"></el-checkbox>
                <el-checkbox :label="$t('gbPlatform.asMessageChannel')" v-model="form.asMessageChannel"></el-checkbox>
                <el-checkbox :label="$t('gbPlatform.autoPushChannel')" v-model="form.autoPushChannel"></el-checkbox>
                <el-checkbox :label="$t('gbPlatform.catalogWithPlatform')" :true-label="1" :false-label="0"
                             v-model="form.catalogWithPlatform"></el-checkbox>
                <el-checkbox :label="$t('gbPlatform.catalogWithGroup')" :true-label="1" :false-label="0"
                             v-model="form.catalogWithGroup"></el-checkbox>
                <el-checkbox :label="$t('gbPlatform.catalogWithRegion')" :true-label="1" :false-label="0"
                             v-model="form.catalogWithRegion"></el-checkbox>
              </div>


            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">{{ $t('common.confirm') }}</el-button>
          <el-button @click="cancel">{{ $t('common.cancel') }}</el-button>
        </div>
      </template>
    </el-dialog>
    <GbHelpDialog v-model="showHelp" mode="platform" />
  </div>
</template>

<script setup name="Platform">
import GbHelpDialog from '@/components/GbHelpDialog.vue'
import { platformExample } from '@/utils/gbHelp'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
const platformTitle = computed(() => t(form.value.id != null ? 'gbPlatform.edit' : 'gbPlatform.add'))
const showHelp = ref(false)
import {
  addPlatform,
  delPlatform,
  exitPlatform,
  listPlatform,
  pushChannel,
  serverConfig,
  updatePlatform
} from "../../../api/wvp/platform.js";
import router from "@/router";
import { checkPermi } from "@/utils/permission";
import { clacPXToVW } from "@/utils/index";

const {proxy} = getCurrentInstance();

const platformList = ref([]);
const loading = ref(false);
const total = ref(0);
const searchData = ref([]);
const open = ref(false);
const deviceIps = ref([]);
const defaultPlatform = ref(null);

const deviceGBIdRules = async (rule, value, callback) => {
  if (value === "") {
    callback(new Error(t('gbPlatform.localIdRequired')));
  } else {
    const exit = await deviceGBIdExit(value);
    if (exit) {
      callback(new Error(t('gbPlatform.localIdInvalid')));
    } else {
      callback();
    }
  }
}

async function deviceGBIdExit(deviceGbId) {
  let result = false;
  exitPlatform(deviceGbId).then((res) => {
    result = res.data;
  })
  return result;
}

const data = reactive({
  form: {},
  queryParams: {
    page: 1,
    count: 10,
    query: undefined,
  },
  rules: {
    name: [{required: true, get message() { return t('gbPlatform.nameRequired') }, trigger: "blur"}],
    serverGBId: [
      {required: true, get message() { return t('gbPlatform.idRequired') }, trigger: "blur"},
    ],
    serverGBDomain: [
      {required: true, get message() { return t('gbPlatform.domainRequired') }, trigger: "blur"},
    ],
    serverIp: [{required: true, get message() { return t('gbPlatform.ipRequired') }, trigger: "blur"}],
    serverPort: [{required: true, get message() { return t('gbPlatform.portRequired') }, trigger: "blur"}],
    deviceGBId: [{validator: deviceGBIdRules, trigger: "blur"}],
    username: [{required: false, get message() { return t('gbPlatform.usernameRequired') }, trigger: "blur"}],
    password: [{required: false, get message() { return t('gbPlatform.passwordRequired') }, trigger: "blur"}],
    expires: [{required: true, get message() { return t('gbPlatform.expiresRequired') }, trigger: "blur"}],
    keepTimeout: [{required: true, get message() { return t('gbPlatform.heartbeatRequired') }, trigger: "blur"}],
    transport: [{required: true, get message() { return t('gbPlatform.transportRequired') }, trigger: "blur"}],
    characterSet: [{required: true, get message() { return t('gbPlatform.charsetRequired') }, trigger: "blur"}],
    deviceIp: [{required: true, get message() { return t('gbPlatform.localIpRequired') }, trigger: "blur"}],
  }
});

const {queryParams, form, rules} = toRefs(data);

function getList() {
  loading.value = true;
  listPlatform(queryParams.value).then(response => {
    platformList.value = response.data.list;
    total.value = response.data.total;
    loading.value = false;
  })
}

/** 搜索按钮操作 */
function searchResetFn(val) {
  queryParams.value.page = 1;
  queryParams.value.query = val.query || undefined;
  getList();
}

function reset() {
  form.value = JSON.parse(JSON.stringify(defaultPlatform.value));
  proxy.resetForm("formRef");
}

function handleAdd() {
  reset()
  open.value = true;
}

function serverGBIdChange() {
  if (form.value.serverGBId.length > 10) {
    form.value.serverGBDomain = form.value.serverGBId.substr(0, 10);
  }
}

function deviceGBIdChange() {
  form.value.username = form.value.deviceGBId;
}

function checkExpires() {
  if (form.value.enable && form.value.expires === "0") {
    form.value.expires = "3600";
  }
}

function rtcpCheckBoxChange(result) {
  if (result) {
    proxy.$modal.msgWarning(translatePhrase("开启RTCP保活需要上级平台支持，可以避免无效推流"));
  }
}

/** 取消按钮 */
function cancel() {
  open.value = false;
  reset();
}

function handleEdit(row) {
  reset()
  form.value = JSON.parse(JSON.stringify(row));
  open.value = true;
}

function handleDelete(row) {
  proxy.$modal.confirm(translatePhrase("确认删除？")).then(function () {
    return delPlatform(row.id)
  }).then(() => {
    proxy.$modal.msgSuccess(translatePhrase("删除成功"));
    getList();
  }).catch(() => {
  });
}

function moreClick(command, row) {
  if (command === "chooseChannel") {
    chooseChannel(row);
  } else if (command === "pushChannelFun") {
    pushChannelFun(row);
  } else if (command === "handleDelete") {
    handleDelete(row);
  }
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["formRef"].validate(valid => {
    if (valid) {
      if (form.value.id != undefined) {
        updatePlatform(form.value).then(() => {
          proxy.$modal.msgSuccess(translatePhrase("修改成功"));
          open.value = false;
          getList()
        })
      } else {
        addPlatform(form.value).then(() => {
          proxy.$modal.msgSuccess(translatePhrase("新增成功"));
          open.value = false;
          getList()
        })
      }
    }
  });
}

function chooseChannel(row) {
  router.push(`/platform/chooseChannel/index/${row.id}`);
}

function pushChannelFun(row) {
  pushChannel(row.id).then((res) => {
    proxy.$modal.msgSuccess(translatePhrase("推送成功"));
    getList();
  })
}

onMounted(() => {
  serverConfig().then((res) => {
    deviceIps.value = res.data.deviceIp.split(',');
    defaultPlatform.value = {
      id: null,
      enable: true,
      ptz: true,
      rtcp: false,
      asMessageChannel: false,
      autoPushChannel: false,
      name: null,
      serverGBId: null,
      serverGBDomain: null,
      serverIp: null,
      serverPort: null,
      deviceGBId: res.data.username,
      deviceIp: deviceIps.value[0],
      devicePort: res.data.devicePort,
      username: res.data.username,
      password: res.data.password,
      expires: 3600,
      keepTimeout: 60,
      transport: "UDP",
      characterSet: "GB2312",
      startOfflinePush: false,
      customGroup: false,
      catalogWithPlatform: 0,
      catalogWithGroup: 0,
      catalogWithRegion: 0,
      manufacturer: null,
      model: null,
      address: null,
      secrecy: 1,
      catalogGroup: 1,
      civilCode: null,
      sendStreamIp: res.data.sendStreamIp,
    }
  })
  getList()
})
</script>

<style scoped>
.gb-dialog-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px 24px;
  padding-inline-end: 24px;
}

.gb-platform-form :deep(.el-row) {
  column-gap: 24px;
}

.gb-platform-form :deep(.el-col) {
  flex: 0 0 calc(50% - 12px);
  max-width: calc(50% - 12px);
  min-width: 0;
}

.gb-platform-form :deep(.el-form-item) {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  margin-bottom: 22px;
}

.gb-platform-form :deep(.el-form-item__label) {
  height: auto;
  min-height: 22px;
  line-height: 1.5;
  margin: 0 0 8px;
  padding: 0;
  text-align: start;
  white-space: normal;
  word-break: normal;
  overflow-wrap: break-word;
}

.gb-platform-form :deep(.el-form-item__content) {
  min-width: 0;
  margin-inline-start: 0 !important;
}

.gb-platform-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 16px;
  width: 100%;
}

.gb-platform-options :deep(.el-checkbox) {
  height: auto;
  min-width: 0;
  margin: 0;
  align-items: flex-start;
}

.gb-platform-options :deep(.el-checkbox__input) {
  margin-block-start: 3px;
}

.gb-platform-options :deep(.el-checkbox__label) {
  line-height: 1.5;
  white-space: normal;
  word-break: normal;
}

@media (max-width: 768px) {
  .gb-platform-form :deep(.el-col) {
    flex-basis: 100%;
    max-width: 100%;
  }

  .gb-platform-options {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
