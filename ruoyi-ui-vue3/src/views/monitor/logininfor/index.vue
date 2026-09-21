<template>
   <div class="app-container">
      <div class="toolbar-with-search">
         <div class="toolbar-left">
            <button-group :button-list="toolbarButtons" />
         </div>
         <div class="searchHeight_out flexRowAC">
            <search-height-box keyword="userName" :placeholder="$tp('请输入用户名称、登录地址等关键词')" :data="searchData" @handle="searchResetFn" />
            <export-excel-pdf :item="{ isDisabledExcel: false }" @handle="handleExportType" />
         </div>
      </div>

      <table-self
         ref="logininforRef"
         class="new_table"
         header-cell-class-name="header_tenant_cell"
         stripe
         v-loading="loading"
         :data="logininforList"
         current-row-key="infoId"
         @selection-change="handleSelectionChange"
         :default-sort="defaultSort"
         @sort-change="handleSortChange"
      >
         <el-table-column type="selection" :width="clacPXToVW(55)" align="center" />
         <el-table-column :label="$tp('访问编号')" align="center" prop="infoId" />
         <el-table-column :label="$tp('用户名称')" align="center" prop="userName" :show-overflow-tooltip="true" sortable="custom" :sort-orders="['descending', 'ascending']" />
         <el-table-column :label="$tp('地址')" align="center" prop="ipaddr" :show-overflow-tooltip="true" />
         <el-table-column :label="$tp('登录地点')" align="center" prop="loginLocation" :show-overflow-tooltip="true" />
         <el-table-column :label="$tp('操作系统')" align="center" prop="os" :show-overflow-tooltip="true" />
         <el-table-column :label="$tp('浏览器')" align="center" prop="browser" :show-overflow-tooltip="true" />
         <el-table-column :label="$tp('登录状态')" align="center" prop="status">
            <template #default="scope">
               <dict-tag :options="sys_common_status" :value="scope.row.status" />
            </template>
         </el-table-column>
         <el-table-column :label="$tp('描述')" align="center" prop="msg" :show-overflow-tooltip="true" />
         <el-table-column :label="$tp('访问时间')" align="center" prop="loginTime" sortable="custom" :sort-orders="['descending', 'ascending']" :width="clacPXToVW(180)">
            <template #default="scope">
               <span>{{ parseTime(scope.row.loginTime) }}</span>
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
   </div>
</template>

<script setup name="Logininfor">
import { list, delLogininfor, cleanLogininfor, unlockLogininfor } from "@/api/monitor/logininfor";
import { clacPXToVW } from "@/utils/index";

const { proxy } = getCurrentInstance();
const { sys_common_status } = proxy.useDict("sys_common_status");

const logininforList = ref([]);
const loading = ref(true);
const showSearch = ref(true);
const searchData = computed(() => [
  { get label() { return translatePhrase("登录地址") }, value: 'ipaddr', type: 'text', default: '' },
  {
    get label() { return translatePhrase("状态") },
    value: 'status',
    type: 'select',
    option: (sys_common_status.value || []).map(d => ({ label: d.label, value: d.value })),
    default: undefined
  },
  {
    get label() { return translatePhrase("登录时间") },
    value: 'dateRange',
    type: 'daterange',
    startP: '开始日期',
    endP: '结束日期',
    format: 'YYYY-MM-DD HH:mm:ss',
    default: []
  }
]);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);

const toolbarButtons = computed(() => [
  { get name() { return translatePhrase("删除") }, svg: 'delete', disabled: multiple.value, permi: ['monitor:logininfor:remove'], clickFn: () => handleDelete() },
  { get name() { return translatePhrase("清空") }, svg: 'delete', permi: ['monitor:logininfor:remove'], clickFn: () => handleClean() },
  { get name() { return translatePhrase("解锁") }, svg: 'lock', disabled: single.value, permi: ['monitor:logininfor:unlock'], clickFn: () => handleUnlock() }
]);
const selectName = ref("");
const total = ref(0);
const dateRange = ref([]);
const defaultSort = ref({ prop: "loginTime", order: "descending" });

// 查询参数
const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  ipaddr: undefined,
  userName: undefined,
  status: undefined,
  orderByColumn: undefined,
  isAsc: undefined
});

/** 查询登录日志列表 */
function getList() {
  loading.value = true;
  list(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    logininforList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

function searchResetFn(val) {
  queryParams.value.pageNum = 1;
  queryParams.value.userName = val.userName || undefined;
  queryParams.value.ipaddr = val.ipaddr || undefined;
  queryParams.value.status = val.status || undefined;
  dateRange.value = val.dateRange && val.dateRange.length ? val.dateRange : [];
  getList();
}

function resetQuery() {
  dateRange.value = [];
  queryParams.value.ipaddr = undefined;
  queryParams.value.userName = undefined;
  queryParams.value.status = undefined;
  handleQuery();
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.infoId);
  multiple.value = !selection.length;
  single.value = selection.length != 1;
  selectName.value = selection.map(item => item.userName);
}

/** 排序触发事件 */
function handleSortChange(column, prop, order) {
  queryParams.value.orderByColumn = column.prop;
  queryParams.value.isAsc = column.order;
  getList();
}

/** 删除按钮操作 */
function handleDelete(row) {
  const infoIds = row?.infoId || ids.value;
  proxy.$modal.confirm(translatePhrase("是否确认删除访问编号为\"") + infoIds + translatePhrase("\"的数据项?")).then(function () {
    return delLogininfor(infoIds);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess(translatePhrase("删除成功"));
  }).catch(() => {});
}

/** 清空按钮操作 */
function handleClean() {
  proxy.$modal.confirm(translatePhrase("是否确认清空所有登录日志数据项?")).then(function () {
    return cleanLogininfor();
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess(translatePhrase("清空成功"));
  }).catch(() => {});
}

/** 解锁按钮操作 */
function handleUnlock() {
  const username = selectName.value;
  proxy.$modal.confirm(translatePhrase("是否确认解锁用户\"") + username + translatePhrase("\"数据项?")).then(function () {
    return unlockLogininfor(username);
  }).then(() => {
    proxy.$modal.msgSuccess(translatePhrase("用户") + username + translatePhrase("解锁成功"));
  }).catch(() => {});
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("monitor/logininfor/export", {
    ...queryParams.value,
  }, `logininfor_${new Date().getTime()}.xlsx`);
}

function handleExportType(type) {
  if (type === 'Excel') {
    handleExport();
  }
}

getList();
</script>
