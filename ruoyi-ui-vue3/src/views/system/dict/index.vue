<template>
   <div class="app-container">
      <div class="toolbar-with-search">
         <div class="toolbar-left">
            <button type="button" class="exportBtn newBtn flexRowAC" @click="handleAdd" v-hasPermi="['system:dict:add']">
               <el-icon class="BtnImg"><Plus /></el-icon>新增
            </button>
            <button-group :button-list="toolbarButtons" />
         </div>
         <div class="searchHeight_out flexRowAC">
            <search-height-box keyword="dictName" placeholder="请输入字典名称等关键词" :data="searchData" @handle="searchResetFn" />
            <export-excel-pdf :item="{ isDisabledExcel: false }" @handle="handleExportType" />
         </div>
      </div>

      <table-self
         class="new_table"
         header-cell-class-name="header_tenant_cell"
         stripe
         v-loading="loading"
         :data="typeList"
         current-row-key="dictId"
         @selection-change="handleSelectionChange"
      >
         <el-table-column type="selection" :width="clacPXToVW(55)" align="center" />
         <el-table-column label="字典编号" align="center" prop="dictId" />
         <el-table-column label="字典名称" align="center" prop="dictName" :show-overflow-tooltip="true"/>
         <el-table-column label="字典类型" align="center" :show-overflow-tooltip="true">
            <template #default="scope">
               <router-link :to="'/system/dict-data/index/' + scope.row.dictId" class="link-type">
                  <span>{{ scope.row.dictType }}</span>
               </router-link>
            </template>
         </el-table-column>
         <el-table-column label="状态" align="center" prop="status">
            <template #default="scope">
               <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
            </template>
         </el-table-column>
         <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
         <el-table-column label="创建时间" align="center" prop="createTime" :width="clacPXToVW(180)">
            <template #default="scope">
               <span>{{ parseTime(scope.row.createTime) }}</span>
            </template>
         </el-table-column>
         <el-table-column label="操作" align="right" fixed="right" :width="clacPXToVW(160)">
            <template #default="scope">
               <div class="operateAppBox flexRowAC" style="justify-content: flex-end;">
                  <div class="new_table_svg_group" @click.stop="handleUpdate(scope.row)" v-hasPermi="['system:dict:edit']">
                     <el-icon><Edit /></el-icon>
                     <span>修改</span>
                  </div>
                  <div class="new_table_svg_group" @click.stop="handleDelete(scope.row)" v-hasPermi="['system:dict:remove']">
                     <el-icon><Delete /></el-icon>
                     <span>删除</span>
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
      <el-dialog :title="title" v-model="open" width="32%" append-to-body>
         <el-form ref="dictRef" :model="form" :rules="rules" label-width="80px">
            <el-form-item label="字典名称" prop="dictName">
               <el-input v-model="form.dictName" placeholder="请输入字典名称" />
            </el-form-item>
            <el-form-item label="字典类型" prop="dictType">
               <el-input v-model="form.dictType" placeholder="请输入字典类型" />
            </el-form-item>
            <el-form-item label="状态" prop="status">
               <el-radio-group v-model="form.status">
                  <el-radio
                     v-for="dict in sys_normal_disable"
                     :key="dict.value"
                     :value="dict.value"
                  >{{ dict.label }}</el-radio>
               </el-radio-group>
            </el-form-item>
            <el-form-item label="备注" prop="remark">
               <el-input v-model="form.remark" type="textarea" placeholder="请输入内容"></el-input>
            </el-form-item>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" @click="submitForm">确 定</el-button>
               <el-button @click="cancel">取 消</el-button>
            </div>
         </template>
      </el-dialog>
   </div>
</template>

<script setup name="Dict">
import useDictStore from '@/store/modules/dict'
import { listType, getType, delType, addType, updateType, refreshCache } from "@/api/system/dict/type";
import { clacPXToVW } from "@/utils/index";

const { proxy } = getCurrentInstance();
const { sys_normal_disable } = proxy.useDict("sys_normal_disable");

const typeList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const searchData = computed(() => [
  { label: '字典类型', value: 'dictType', type: 'text', default: '' },
  {
    label: '状态',
    value: 'status',
    type: 'select',
    option: (sys_normal_disable.value || []).map(d => ({ label: d.label, value: d.value })),
    default: undefined
  },
  {
    label: '创建时间',
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
  { name: '修改', svg: 'edit', disabled: single.value, permi: ['system:dict:edit'], clickFn: () => handleUpdate() },
  { name: '删除', svg: 'delete', disabled: multiple.value, permi: ['system:dict:remove'], clickFn: () => handleDelete() },
  { name: '刷新缓存', svg: 'operate', permi: ['system:dict:remove'], clickFn: () => handleRefreshCache() }
]);
const total = ref(0);
const title = ref("");
const dateRange = ref([]);

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    dictName: undefined,
    dictType: undefined,
    status: undefined
  },
  rules: {
    dictName: [{ required: true, message: "字典名称不能为空", trigger: "blur" }],
    dictType: [{ required: true, message: "字典类型不能为空", trigger: "blur" }]
  },
});

const { queryParams, form, rules } = toRefs(data);

/** 查询字典类型列表 */
function getList() {
  loading.value = true;
  listType(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    typeList.value = response.rows;
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
    dictId: undefined,
    dictName: undefined,
    dictType: undefined,
    status: "0",
    remark: undefined
  };
  proxy.resetForm("dictRef");
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

function searchResetFn(val) {
  queryParams.value.pageNum = 1;
  queryParams.value.dictName = val.dictName || undefined;
  queryParams.value.dictType = val.dictType || undefined;
  queryParams.value.status = val.status || undefined;
  dateRange.value = val.dateRange && val.dateRange.length ? val.dateRange : [];
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  dateRange.value = [];
  queryParams.value.dictName = undefined;
  queryParams.value.dictType = undefined;
  queryParams.value.status = undefined;
  handleQuery();
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  open.value = true;
  title.value = "添加字典类型";
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.dictId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  const dictId = row?.dictId || ids.value;
  getType(dictId).then(response => {
    form.value = response.data;
    open.value = true;
    title.value = "修改字典类型";
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["dictRef"].validate(valid => {
    if (valid) {
      if (form.value.dictId != undefined) {
        updateType(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功");
          open.value = false;
          getList();
        });
      } else {
        addType(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功");
          open.value = false;
          getList();
        });
      }
    }
  });
}

/** 删除按钮操作 */
function handleDelete(row) {
  const dictIds = row?.dictId || ids.value;
  proxy.$modal.confirm('是否确认删除字典编号为"' + dictIds + '"的数据项？').then(function() {
    return delType(dictIds);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch(() => {});
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("system/dict/type/export", {
    ...queryParams.value
  }, `dict_${new Date().getTime()}.xlsx`);
}

function handleExportType(type) {
  if (type === 'Excel') {
    handleExport();
  }
}

/** 刷新缓存按钮操作 */
function handleRefreshCache() {
  refreshCache().then(() => {
    proxy.$modal.msgSuccess("刷新成功");
    useDictStore().cleanDict();
  });
}

getList();
</script>
