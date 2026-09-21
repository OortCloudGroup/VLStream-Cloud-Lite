<template>
   <div class="app-container">
      <div class="toolbar-with-search">
         <div class="toolbar-left">
            <button type="button" class="exportBtn newBtn flexRowAC" @click="handleAdd" v-hasPermi="['system:dept:add']">
               <el-icon class="BtnImg"><Plus /></el-icon>{{ $tp("新增") }}
            </button>
            <button-group :button-list="toolbarButtons" />
         </div>
         <div class="searchHeight_out flexRowAC">
            <search-height-box keyword="deptName" :placeholder="$tp('请输入部门名称')" :data="searchData" @handle="searchResetFn" />
            <export-excel-pdf />
         </div>
      </div>

      <table-self
         v-if="refreshTable"
         v-loading="loading"
         :data="deptList"
         row-key="deptId"
         :default-expand-all="isExpandAll"
         :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
         class="new_table"
         header-cell-class-name="header_tenant_cell"
         stripe
      >
         <el-table-column prop="deptName" :label="$tp('部门名称')" :show-overflow-tooltip="true"></el-table-column>
         <el-table-column prop="orderNum" :label="$tp('排序')" :width="clacPXToVW(100)"></el-table-column>
         <el-table-column prop="status" :label="$tp('状态')" :width="clacPXToVW(100)">
            <template #default="scope">
               <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
            </template>
         </el-table-column>
         <el-table-column :label="$tp('创建时间')" align="center" prop="createTime" :width="clacPXToVW(180)">
            <template #default="scope">
               <span>{{ parseTime(scope.row.createTime) }}</span>
            </template>
         </el-table-column>
         <el-table-column :label="$tp('操作')" align="right" fixed="right" :width="clacPXToVW(220)">
            <template #default="scope">
               <div class="operateAppBox flexRowAC" style="justify-content: flex-end;">
                  <div class="new_table_svg_group" @click.stop="handleUpdate(scope.row)" v-hasPermi="['system:dept:edit']">
                     <el-icon><Edit /></el-icon>
                     <span>{{ $tp("修改") }}</span>
                  </div>
                  <div class="new_table_svg_group" @click.stop="handleAdd(scope.row)" v-hasPermi="['system:dept:add']">
                     <el-icon><Plus /></el-icon>
                     <span>{{ $tp("新增") }}</span>
                  </div>
                  <div class="new_table_svg_group" @click.stop="handleDelete(scope.row)" v-if="scope.row.parentId != 0" v-hasPermi="['system:dept:remove']">
                     <el-icon><Delete /></el-icon>
                     <span>{{ $tp("删除") }}</span>
                  </div>
               </div>
            </template>
         </el-table-column>
      </table-self>

      <!-- 添加或修改部门对话框 -->
      <el-dialog :title="$tp(title)" v-model="open" width="40%" append-to-body>
         <el-form ref="deptRef" :model="form" :rules="rules" label-width="80px">
            <el-row>
               <el-col :span="24" v-if="form.parentId !== 0">
                  <el-form-item :label="$tp('上级部门')" prop="parentId">
                     <el-tree-select
                        v-model="form.parentId"
                        :data="deptOptions"
                        :props="{ value: 'deptId', label: 'deptName', children: 'children' }"
                        value-key="deptId"
                        :placeholder="$tp('选择上级部门')"
                        check-strictly
                     />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item :label="$tp('部门名称')" prop="deptName">
                     <el-input v-model="form.deptName" :placeholder="$tp('请输入部门名称')" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item :label="$tp('显示排序')" prop="orderNum">
                     <el-input-number v-model="form.orderNum" controls-position="right" :min="0" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item :label="$tp('负责人')" prop="leader">
                     <el-input v-model="form.leader" :placeholder="$tp('请输入负责人')" maxlength="20" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item :label="$tp('联系电话')" prop="phone">
                     <el-input v-model="form.phone" :placeholder="$tp('请输入联系电话')" maxlength="11" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item :label="$tp('邮箱')" prop="email">
                     <el-input v-model="form.email" :placeholder="$tp('请输入邮箱')" maxlength="50" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item :label="$tp('部门状态')">
                     <el-radio-group v-model="form.status">
                        <el-radio
                           v-for="dict in sys_normal_disable"
                           :key="dict.value"
                           :value="dict.value"
                        >{{ dict.label }}</el-radio>
                     </el-radio-group>
                  </el-form-item>
               </el-col>
            </el-row>
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

<script setup name="Dept">
import { listDept, getDept, delDept, addDept, updateDept, listDeptExcludeChild } from "@/api/system/dept";
import { clacPXToVW } from "@/utils/index";

const { proxy } = getCurrentInstance();
const { sys_normal_disable } = proxy.useDict("sys_normal_disable");

const deptList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const searchData = computed(() => [
  {
    get label() { return translatePhrase("状态") },
    value: 'status',
    type: 'select',
    option: (sys_normal_disable.value || []).map(d => ({ label: d.label, value: d.value })),
    default: undefined
  }
]);
const title = ref("");
const deptOptions = ref([]);
const isExpandAll = ref(true);
const refreshTable = ref(true);

const toolbarButtons = computed(() => [
  { get name() { return translatePhrase("展开/折叠") }, svg: 'list', clickFn: () => toggleExpandAll() }
]);

const data = reactive({
  form: {},
  queryParams: {
    deptName: undefined,
    status: undefined
  },
  rules: {
    parentId: [{ required: true, get message() { return translatePhrase("上级部门不能为空") }, trigger: "blur" }],
    deptName: [{ required: true, get message() { return translatePhrase("部门名称不能为空") }, trigger: "blur" }],
    orderNum: [{ required: true, get message() { return translatePhrase("显示排序不能为空") }, trigger: "blur" }],
    email: [{ type: "email", get message() { return translatePhrase("请输入正确的邮箱地址") }, trigger: ["blur", "change"] }],
    phone: [{ pattern: /^1[3|4|5|6|7|8|9][0-9]\d{8}$/, get message() { return translatePhrase("请输入正确的手机号码") }, trigger: "blur" }]
  },
});

const { queryParams, form, rules } = toRefs(data);

/** 查询部门列表 */
function getList() {
  loading.value = true;
  listDept(queryParams.value).then(response => {
    deptList.value = proxy.handleTree(response.data, "deptId");
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
    deptId: undefined,
    parentId: undefined,
    deptName: undefined,
    orderNum: 0,
    leader: undefined,
    phone: undefined,
    email: undefined,
    status: "0"
  };
  proxy.resetForm("deptRef");
}

/** 搜索按钮操作 */
function handleQuery() {
  getList();
}

function searchResetFn(val) {
  queryParams.value.deptName = val.deptName || undefined;
  queryParams.value.status = val.status || undefined;
  getList();
}

function resetQuery() {
  queryParams.value.deptName = undefined;
  queryParams.value.status = undefined;
  handleQuery();
}

/** 新增按钮操作 */
function handleAdd(row) {
  reset();
  listDept().then(response => {
    deptOptions.value = proxy.handleTree(response.data, "deptId");
  });
  if (row != undefined) {
    form.value.parentId = row.deptId;
  }
  open.value = true;
  title.value = "添加部门";
}

/** 展开/折叠操作 */
function toggleExpandAll() {
  refreshTable.value = false;
  isExpandAll.value = !isExpandAll.value;
  nextTick(() => {
    refreshTable.value = true;
  });
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  listDeptExcludeChild(row.deptId).then(response => {
    deptOptions.value = proxy.handleTree(response.data, "deptId");
  });
  getDept(row.deptId).then(response => {
    form.value = response.data;
    open.value = true;
    title.value = "修改部门";
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["deptRef"].validate(valid => {
    if (valid) {
      if (form.value.deptId != undefined) {
        updateDept(form.value).then(response => {
          proxy.$modal.msgSuccess(translatePhrase("修改成功"));
          open.value = false;
          getList();
        });
      } else {
        addDept(form.value).then(response => {
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
  proxy.$modal.confirm(translatePhrase("是否确认删除名称为\"") + row.deptName + translatePhrase("\"的数据项?")).then(function() {
    return delDept(row.deptId);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess(translatePhrase("删除成功"));
  }).catch(() => {});
}

getList();
</script>
