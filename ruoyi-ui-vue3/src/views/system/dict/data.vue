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
            <search-height-box keyword="dictLabel" placeholder="请输入字典标签等关键词" :data="searchData" @handle="searchResetFn" />
            <export-excel-pdf :item="{ isDisabledExcel: false }" @handle="handleExportType" />
         </div>
      </div>

      <table-self
         class="new_table"
         header-cell-class-name="header_tenant_cell"
         stripe
         v-loading="loading"
         :data="dataList"
         current-row-key="dictCode"
         @selection-change="handleSelectionChange"
      >
         <el-table-column type="selection" :width="clacPXToVW(55)" align="center" />
         <el-table-column label="字典编码" align="center" prop="dictCode" />
         <el-table-column label="字典标签" align="center" prop="dictLabel">
            <template #default="scope">
               <span v-if="(scope.row.listClass == '' || scope.row.listClass == 'default') && (scope.row.cssClass == '' || scope.row.cssClass == null)">{{ scope.row.dictLabel }}</span>
               <el-tag v-else :type="scope.row.listClass == 'primary' ? '' : scope.row.listClass" :class="scope.row.cssClass">{{ scope.row.dictLabel }}</el-tag>
            </template>
         </el-table-column>
         <el-table-column label="字典键值" align="center" prop="dictValue" />
         <el-table-column label="字典排序" align="center" prop="dictSort" />
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
         <el-form ref="dataRef" :model="form" :rules="rules" label-width="80px">
            <el-form-item label="字典类型">
               <el-input v-model="form.dictType" :disabled="true" />
            </el-form-item>
            <el-form-item label="数据标签" prop="dictLabel">
               <el-input v-model="form.dictLabel" placeholder="请输入数据标签" />
            </el-form-item>
            <el-form-item label="数据键值" prop="dictValue">
               <el-input v-model="form.dictValue" placeholder="请输入数据键值" />
            </el-form-item>
            <el-form-item label="样式属性" prop="cssClass">
               <el-input v-model="form.cssClass" placeholder="请输入样式属性" />
            </el-form-item>
            <el-form-item label="显示排序" prop="dictSort">
               <el-input-number v-model="form.dictSort" controls-position="right" :min="0" />
            </el-form-item>
            <el-form-item label="回显样式" prop="listClass">
               <el-select v-model="form.listClass">
                  <el-option
                     v-for="item in listClassOptions"
                     :key="item.value"
                     :label="item.label + '(' + item.value + ')'"
                     :value="item.value"
                  ></el-option>
               </el-select>
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

<script setup name="Data">
import useDictStore from '@/store/modules/dict'
import { optionselect as getDictOptionselect, getType } from "@/api/system/dict/type";
import { listData, getData, delData, addData, updateData } from "@/api/system/dict/data";
import { clacPXToVW } from "@/utils/index";

const { proxy } = getCurrentInstance();
const { sys_normal_disable } = proxy.useDict("sys_normal_disable");

const dataList = ref([]);
const open = ref(false);
const loading = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);

const toolbarButtons = computed(() => [
  { name: '修改', svg: 'edit', disabled: single.value, permi: ['system:dict:edit'], clickFn: () => handleUpdate() },
  { name: '删除', svg: 'delete', disabled: multiple.value, permi: ['system:dict:remove'], clickFn: () => handleDelete() },
  { name: '关闭', svg: 'operate', clickFn: () => handleClose() }
]);
const total = ref(0);
const title = ref("");
const defaultDictType = ref("");
const typeOptions = ref([]);
const route = useRoute();
// 数据标签回显样式
const listClassOptions = ref([
  { value: "default", label: "默认" }, 
  { value: "primary", label: "主要" }, 
  { value: "success", label: "成功" },
  { value: "info", label: "信息" },
  { value: "warning", label: "警告" },
  { value: "danger", label: "危险" }
]);

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    dictType: undefined,
    dictLabel: undefined,
    status: undefined
  },
  rules: {
    dictLabel: [{ required: true, message: "数据标签不能为空", trigger: "blur" }],
    dictValue: [{ required: true, message: "数据键值不能为空", trigger: "blur" }],
    dictSort: [{ required: true, message: "数据顺序不能为空", trigger: "blur" }]
  }
});

const { queryParams, form, rules } = toRefs(data);

const searchData = computed(() => [
  {
    label: '字典名称',
    value: 'dictType',
    type: 'select',
    option: (typeOptions.value || []).map(item => ({ label: item.dictName, value: item.dictType })),
    default: queryParams.value.dictType
  },
  {
    label: '状态',
    value: 'status',
    type: 'select',
    option: (sys_normal_disable.value || []).map(d => ({ label: d.label, value: d.value })),
    default: undefined
  }
]);

/** 查询字典类型详细 */
function getTypes(dictId) {
  getType(dictId).then(response => {
    queryParams.value.dictType = response.data.dictType;
    defaultDictType.value = response.data.dictType;
    getList();
  });
}

/** 查询字典类型列表 */
function getTypeList() {
  getDictOptionselect().then(response => {
    typeOptions.value = response.data;
  });
}

/** 查询字典数据列表 */
function getList() {
  loading.value = true;
  listData(queryParams.value).then(response => {
    dataList.value = response.rows;
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
    dictCode: undefined,
    dictLabel: undefined,
    dictValue: undefined,
    cssClass: undefined,
    listClass: "default",
    dictSort: 0,
    status: "0",
    remark: undefined
  };
  proxy.resetForm("dataRef");
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

function searchResetFn(val) {
  queryParams.value.pageNum = 1;
  queryParams.value.dictLabel = val.dictLabel || undefined;
  queryParams.value.dictType = val.dictType || defaultDictType.value;
  queryParams.value.status = val.status || undefined;
  getList();
}

function handleExportType(type) {
  if (type === 'Excel') {
    handleExport();
  }
}

/** 返回按钮操作 */
function handleClose() {
  const obj = { path: "/system/dict" };
  proxy.$tab.closeOpenPage(obj);
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  open.value = true;
  title.value = "添加字典数据";
  form.value.dictType = queryParams.value.dictType;
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.dictCode);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  const dictCode = row?.dictCode || ids.value;
  getData(dictCode).then(response => {
    form.value = response.data;
    open.value = true;
    title.value = "修改字典数据";
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["dataRef"].validate(valid => {
    if (valid) {
      if (form.value.dictCode != undefined) {
        updateData(form.value).then(response => {
          useDictStore().removeDict(queryParams.value.dictType);
          proxy.$modal.msgSuccess("修改成功");
          open.value = false;
          getList();
        });
      } else {
        addData(form.value).then(response => {
          useDictStore().removeDict(queryParams.value.dictType);
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
  const dictCodes = row?.dictCode || ids.value;
  proxy.$modal.confirm('是否确认删除字典编码为"' + dictCodes + '"的数据项？').then(function() {
    return delData(dictCodes);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("删除成功");
    useDictStore().removeDict(queryParams.value.dictType);
  }).catch(() => {});
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("system/dict/data/export", {
    ...queryParams.value
  }, `dict_data_${new Date().getTime()}.xlsx`);
}

getTypes(route.params && route.params.dictId);
getTypeList();
</script>
