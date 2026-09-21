<template>
   <div class="app-container">
      <div class="toolbar-with-search">
         <div class="toolbar-left">
            <button type="button" class="exportBtn newBtn flexRowAC" @click="handleAdd" v-hasPermi="['system:config:add']">
               <el-icon class="BtnImg"><Plus /></el-icon>{{ $tp("新增") }}
            </button>
            <button-group :button-list="toolbarButtons" />
         </div>
         <div class="searchHeight_out flexRowAC">
            <search-height-box keyword="configName" :placeholder="$tp('请输入参数名称等关键词')" :data="searchData" @handle="searchResetFn" />
            <export-excel-pdf :item="{ isDisabledExcel: false }" @handle="handleExportType" />
         </div>
      </div>

      <table-self
         class="new_table"
         header-cell-class-name="header_tenant_cell"
         stripe
         v-loading="loading"
         :data="configList"
         current-row-key="configId"
         @selection-change="handleSelectionChange"
      >
         <el-table-column type="selection" :width="clacPXToVW(55)" align="center" />
         <el-table-column :label="$tp('参数主键')" align="center" prop="configId" />
         <el-table-column :label="$tp('参数名称')" align="center" prop="configName" :show-overflow-tooltip="true" />
         <el-table-column :label="$tp('参数键名')" align="center" prop="configKey" :show-overflow-tooltip="true" />
         <el-table-column :label="$tp('参数键值')" align="center" prop="configValue" :show-overflow-tooltip="true" />
         <el-table-column :label="$tp('系统内置')" align="center" prop="configType">
            <template #default="scope">
               <dict-tag :options="sys_yes_no" :value="scope.row.configType" />
            </template>
         </el-table-column>
         <el-table-column :label="$tp('备注')" align="center" prop="remark" :show-overflow-tooltip="true" />
         <el-table-column :label="$tp('创建时间')" align="center" prop="createTime" :width="clacPXToVW(180)">
            <template #default="scope">
               <span>{{ parseTime(scope.row.createTime) }}</span>
            </template>
         </el-table-column>
         <el-table-column :label="$tp('操作')" align="right" fixed="right" :width="clacPXToVW(160)">
            <template #default="scope">
               <div class="operateAppBox flexRowAC" style="justify-content: flex-end;">
                  <div class="new_table_svg_group" @click.stop="handleUpdate(scope.row)" v-hasPermi="['system:config:edit']">
                     <el-icon><Edit /></el-icon>
                     <span>{{ $tp("修改") }}</span>
                  </div>
                  <div class="new_table_svg_group" @click.stop="handleDelete(scope.row)" v-hasPermi="['system:config:remove']">
                     <el-icon><Delete /></el-icon>
                     <span>{{ $tp("删除") }}</span>
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

      <!-- 添加或修改参数配置对话框 -->
      <el-dialog :title="$tp(title)" v-model="open" width="32%" append-to-body>
         <el-form ref="configRef" :model="form" :rules="rules" label-width="80px">
            <el-form-item :label="$tp('参数名称')" prop="configName">
               <el-input v-model="form.configName" :placeholder="$tp('请输入参数名称')" />
            </el-form-item>
            <el-form-item :label="$tp('参数键名')" prop="configKey">
               <el-input v-model="form.configKey" :placeholder="$tp('请输入参数键名')" />
            </el-form-item>
            <el-form-item :label="$tp('参数键值')" prop="configValue">
               <el-input v-model="form.configValue" type="textarea" :placeholder="$tp('请输入参数键值')" />
            </el-form-item>
            <el-form-item :label="$tp('系统内置')" prop="configType">
               <el-radio-group v-model="form.configType">
                  <el-radio
                     v-for="dict in sys_yes_no"
                     :key="dict.value"
                     :value="dict.value"
                  >{{ dict.label }}</el-radio>
               </el-radio-group>
            </el-form-item>
            <el-form-item :label="$tp('备注')" prop="remark">
               <el-input v-model="form.remark" type="textarea" :placeholder="$tp('请输入内容')" />
            </el-form-item>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" @click="submitForm">{{ $tp("确 定") }}</el-button>
               <el-button @click="cancel">{{ $tp("取 消") }}</el-button>
            </div>
         </template>
      </el-dialog>
   </div>
</template>

<script setup name="Config">
import { listConfig, getConfig, delConfig, addConfig, updateConfig, refreshCache } from "@/api/system/config";
import { clacPXToVW } from "@/utils/index";

const { proxy } = getCurrentInstance();
const { sys_yes_no } = proxy.useDict("sys_yes_no");

const configList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const searchData = computed(() => [
  { get label() { return translatePhrase("参数键名") }, value: 'configKey', type: 'text', default: '' },
  {
    get label() { return translatePhrase("系统内置") },
    value: 'configType',
    type: 'select',
    option: (sys_yes_no.value || []).map(d => ({ label: d.label, value: d.value })),
    default: undefined
  },
  {
    get label() { return translatePhrase("创建时间") },
    value: 'dateRange',
    type: 'daterange',
    startP: '开始日期',
    endP: '结束日期',
    format: 'YYYY-MM-DD',
    default: []
  }
]);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);

const toolbarButtons = computed(() => [
  { get name() { return translatePhrase("修改") }, svg: 'edit', disabled: single.value, permi: ['system:config:edit'], clickFn: () => handleUpdate() },
  { get name() { return translatePhrase("删除") }, svg: 'delete', disabled: multiple.value, permi: ['system:config:remove'], clickFn: () => handleDelete() },
  { get name() { return translatePhrase("刷新缓存") }, svg: 'operate', permi: ['system:config:remove'], clickFn: () => handleRefreshCache() }
]);
const total = ref(0);
const title = ref("");
const dateRange = ref([]);

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    configName: undefined,
    configKey: undefined,
    configType: undefined
  },
  rules: {
    configName: [{ required: true, get message() { return translatePhrase("参数名称不能为空") }, trigger: "blur" }],
    configKey: [{ required: true, get message() { return translatePhrase("参数键名不能为空") }, trigger: "blur" }],
    configValue: [{ required: true, get message() { return translatePhrase("参数键值不能为空") }, trigger: "blur" }]
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询参数列表 */
function getList() {
  loading.value = true;
  listConfig(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    configList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

/** 取消按钮 */
function cancel() {
  open.value = false;
  reset();
}

/** 表单重置 */
function reset() {
  form.value = {
    configId: undefined,
    configName: undefined,
    configKey: undefined,
    configValue: undefined,
    configType: "Y",
    remark: undefined
  };
  proxy.resetForm("configRef");
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

function searchResetFn(val) {
  queryParams.value.pageNum = 1;
  queryParams.value.configName = val.configName || undefined;
  queryParams.value.configKey = val.configKey || undefined;
  queryParams.value.configType = val.configType || undefined;
  dateRange.value = val.dateRange && val.dateRange.length ? val.dateRange : [];
  getList();
}

function resetQuery() {
  dateRange.value = [];
  queryParams.value.configName = undefined;
  queryParams.value.configKey = undefined;
  queryParams.value.configType = undefined;
  handleQuery();
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.configId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  open.value = true;
  title.value = "添加参数";
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  const configId = row?.configId || ids.value;
  getConfig(configId).then(response => {
    form.value = response.data;
    open.value = true;
    title.value = "修改参数";
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["configRef"].validate(valid => {
    if (valid) {
      if (form.value.configId != undefined) {
        updateConfig(form.value).then(response => {
          proxy.$modal.msgSuccess(translatePhrase("修改成功"));
          open.value = false;
          getList();
        });
      } else {
        addConfig(form.value).then(response => {
          proxy.$modal.msgSuccess(translatePhrase("新增成功"));
          open.value = false;
          getList();
        });
      }
    }
  });
}

/** 删除按钮操作 */
function handleDelete(row) {
  const configIds = row?.configId || ids.value;
  proxy.$modal.confirm(translatePhrase("是否确认删除参数编号为\"") + configIds + translatePhrase("\"的数据项？")).then(function () {
    return delConfig(configIds);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess(translatePhrase("删除成功"));
  }).catch(() => {});
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("system/config/export", {
    ...queryParams.value
  }, `config_${new Date().getTime()}.xlsx`);
}

function handleExportType(type) {
  if (type === 'Excel') {
    handleExport();
  }
}

/** 刷新缓存按钮操作 */
function handleRefreshCache() {
  refreshCache().then(() => {
    proxy.$modal.msgSuccess(translatePhrase("刷新缓存成功"));
  });
}

getList();
</script>
