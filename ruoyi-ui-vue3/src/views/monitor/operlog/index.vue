<template>
   <div class="app-container">
      <div class="toolbar-with-search">
         <div class="toolbar-left">
            <button-group :button-list="toolbarButtons" />
         </div>
         <div class="searchHeight_out flexRowAC">
            <search-height-box keyword="title" placeholder="请输入系统模块、操作人员等关键词" :data="searchData" @handle="searchResetFn" />
            <export-excel-pdf :item="{ isDisabledExcel: false }" @handle="handleExportType" />
         </div>
      </div>

      <table-self
         ref="operlogRef"
         class="new_table"
         header-cell-class-name="header_tenant_cell"
         stripe
         v-loading="loading"
         :data="operlogList"
         current-row-key="operId"
         @selection-change="handleSelectionChange"
         :default-sort="defaultSort"
         @sort-change="handleSortChange"
      >
         <el-table-column type="selection" :width="clacPXToVW(55)" align="center" />
         <el-table-column label="日志编号" align="center" prop="operId" />
         <el-table-column label="系统模块" align="center" prop="title" :show-overflow-tooltip="true" />
         <el-table-column label="操作类型" align="center" prop="businessType">
            <template #default="scope">
               <dict-tag :options="sys_oper_type" :value="scope.row.businessType" />
            </template>
         </el-table-column>
         <el-table-column label="操作人员" align="center" :width="clacPXToVW(110)" prop="operName" :show-overflow-tooltip="true" sortable="custom" :sort-orders="['descending', 'ascending']" />
         <el-table-column label="操作地址" align="center" prop="operIp" :width="clacPXToVW(130)" :show-overflow-tooltip="true" />
         <el-table-column label="操作状态" align="center" prop="status">
            <template #default="scope">
               <dict-tag :options="sys_common_status" :value="scope.row.status" />
            </template>
         </el-table-column>
         <el-table-column label="操作日期" align="center" prop="operTime" :width="clacPXToVW(180)" sortable="custom" :sort-orders="['descending', 'ascending']">
            <template #default="scope">
               <span>{{ parseTime(scope.row.operTime) }}</span>
            </template>
         </el-table-column>
         <el-table-column label="消耗时间" align="center" prop="costTime" :width="clacPXToVW(110)" :show-overflow-tooltip="true" sortable="custom" :sort-orders="['descending', 'ascending']">
            <template #default="scope">
               <span>{{ scope.row.costTime }}毫秒</span>
            </template>
         </el-table-column>
         <el-table-column label="操作" align="right" fixed="right" :width="clacPXToVW(100)">
            <template #default="scope">
               <div class="operateAppBox flexRowAC" style="justify-content: flex-end;">
                  <div class="new_table_svg_group" @click.stop="handleView(scope.row, scope.index)" v-hasPermi="['monitor:operlog:query']">
                     <el-icon><View /></el-icon>
                     <span>详细</span>
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

      <!-- 操作日志详细 -->
      <el-dialog title="操作日志详细" v-model="open" width="50%" append-to-body>
         <el-form :model="form" label-width="100px">
            <el-row>
               <el-col :span="12">
                  <el-form-item label="操作模块：">{{ form.title }} / {{ typeFormat(form) }}</el-form-item>
                  <el-form-item
                    label="登录信息："
                  >{{ form.operName }} / {{ form.operIp }} / {{ form.operLocation }}</el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="请求地址：">{{ form.operUrl }}</el-form-item>
                  <el-form-item label="请求方式：">{{ form.requestMethod }}</el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item label="操作方法：">{{ form.method }}</el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item label="请求参数：">{{ form.operParam }}</el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item label="返回参数：">{{ form.jsonResult }}</el-form-item>
               </el-col>
               <el-col :span="8">
                  <el-form-item label="操作状态：">
                     <div v-if="form.status === 0">正常</div>
                     <div v-else-if="form.status === 1">失败</div>
                  </el-form-item>
               </el-col>
               <el-col :span="8">
                  <el-form-item label="消耗时间：">{{ form.costTime }}毫秒</el-form-item>
               </el-col>
               <el-col :span="8">
                  <el-form-item label="操作时间：">{{ parseTime(form.operTime) }}</el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item label="异常信息：" v-if="form.status === 1">{{ form.errorMsg }}</el-form-item>
               </el-col>
            </el-row>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button @click="open = false">关 闭</el-button>
            </div>
         </template>
      </el-dialog>
   </div>
</template>

<script setup name="Operlog">
import { list, delOperlog, cleanOperlog } from "@/api/monitor/operlog";
import { clacPXToVW } from "@/utils/index";

const { proxy } = getCurrentInstance();
const { sys_oper_type, sys_common_status } = proxy.useDict("sys_oper_type","sys_common_status");

const operlogList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const searchData = computed(() => [
  { label: '操作地址', value: 'operIp', type: 'text', default: '' },
  { label: '操作人员', value: 'operName', type: 'text', default: '' },
  {
    label: '类型',
    value: 'businessType',
    type: 'select',
    option: (sys_oper_type.value || []).map(d => ({ label: d.label, value: d.value })),
    default: undefined
  },
  {
    label: '状态',
    value: 'status',
    type: 'select',
    option: (sys_common_status.value || []).map(d => ({ label: d.label, value: d.value })),
    default: undefined
  },
  {
    label: '操作时间',
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
  { name: '删除', svg: 'delete', disabled: multiple.value, permi: ['monitor:operlog:remove'], clickFn: () => handleDelete() },
  { name: '清空', svg: 'delete', permi: ['monitor:operlog:remove'], clickFn: () => handleClean() }
]);
const total = ref(0);
const title = ref("");
const dateRange = ref([]);
const defaultSort = ref({ prop: "operTime", order: "descending" });

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    operIp: undefined,
    title: undefined,
    operName: undefined,
    businessType: undefined,
    status: undefined
  }
});

const { queryParams, form } = toRefs(data);

/** 查询登录日志 */
function getList() {
  loading.value = true;
  list(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    operlogList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

/** 操作日志类型字典翻译 */
function typeFormat(row, column) {
  return proxy.selectDictLabel(sys_oper_type.value, row.businessType);
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

function searchResetFn(val) {
  queryParams.value.pageNum = 1;
  queryParams.value.title = val.title || undefined;
  queryParams.value.operIp = val.operIp || undefined;
  queryParams.value.operName = val.operName || undefined;
  queryParams.value.businessType = val.businessType || undefined;
  queryParams.value.status = val.status || undefined;
  dateRange.value = val.dateRange && val.dateRange.length ? val.dateRange : [];
  getList();
}

function resetQuery() {
  dateRange.value = [];
  queryParams.value.operIp = undefined;
  queryParams.value.title = undefined;
  queryParams.value.operName = undefined;
  queryParams.value.businessType = undefined;
  queryParams.value.status = undefined;
  handleQuery();
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.operId);
  multiple.value = !selection.length;
}

/** 排序触发事件 */
function handleSortChange(column, prop, order) {
  queryParams.value.orderByColumn = column.prop;
  queryParams.value.isAsc = column.order;
  getList();
}

/** 详细按钮操作 */
function handleView(row) {
  open.value = true;
  form.value = row;
}

/** 删除按钮操作 */
function handleDelete(row) {
  const operIds = row?.operId || ids.value;
  proxy.$modal.confirm('是否确认删除日志编号为"' + operIds + '"的数据项?').then(function () {
    return delOperlog(operIds);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch(() => {});
}

/** 清空按钮操作 */
function handleClean() {
  proxy.$modal.confirm("是否确认清空所有操作日志数据项?").then(function () {
    return cleanOperlog();
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("清空成功");
  }).catch(() => {});
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("monitor/operlog/export",{
    ...queryParams.value,
  }, `config_${new Date().getTime()}.xlsx`);
}

function handleExportType(type) {
  if (type === 'Excel') {
    handleExport();
  }
}

getList();
</script>
