<template>
   <div class="app-container">
      <div class="toolbar-with-search">
         <div class="toolbar-left">
            <button type="button" class="exportBtn newBtn flexRowAC" @click="handleAdd" v-hasPermi="['system:notice:add']">
               <el-icon class="BtnImg"><Plus /></el-icon>{{ $tp("新增") }}
            </button>
            <button-group :button-list="toolbarButtons" />
         </div>
         <div class="searchHeight_out flexRowAC">
            <search-height-box keyword="noticeTitle" :placeholder="$tp('请输入公告标题等关键词')" :data="searchData" @handle="searchResetFn" />
            <export-excel-pdf />
         </div>
      </div>

      <table-self
         class="new_table"
         header-cell-class-name="header_tenant_cell"
         stripe
         v-loading="loading"
         :data="noticeList"
         current-row-key="noticeId"
         @selection-change="handleSelectionChange"
      >
         <el-table-column type="selection" :width="clacPXToVW(55)" align="center" />
         <el-table-column :label="$tp('序号')" align="center" prop="noticeId" :width="clacPXToVW(100)" />
         <el-table-column
            :label="$tp('公告标题')"
            align="center"
            prop="noticeTitle"
            :show-overflow-tooltip="true"
         />
         <el-table-column :label="$tp('公告类型')" align="center" prop="noticeType" :width="clacPXToVW(100)">
            <template #default="scope">
               <dict-tag :options="sys_notice_type" :value="scope.row.noticeType" />
            </template>
         </el-table-column>
         <el-table-column :label="$tp('状态')" align="center" prop="status" :width="clacPXToVW(100)">
            <template #default="scope">
               <dict-tag :options="sys_notice_status" :value="scope.row.status" />
            </template>
         </el-table-column>
         <el-table-column :label="$tp('创建者')" align="center" prop="createBy" :width="clacPXToVW(100)" />
         <el-table-column :label="$tp('创建时间')" align="center" prop="createTime" :width="clacPXToVW(120)">
            <template #default="scope">
               <span>{{ parseTime(scope.row.createTime, '{y}-{m}-{d}') }}</span>
            </template>
         </el-table-column>
         <el-table-column :label="$tp('操作')" align="right" fixed="right" :width="clacPXToVW(160)">
            <template #default="scope">
               <div class="operateAppBox flexRowAC" style="justify-content: flex-end;">
                  <div class="new_table_svg_group" @click.stop="handleUpdate(scope.row)" v-hasPermi="['system:notice:edit']">
                     <el-icon><Edit /></el-icon>
                     <span>{{ $tp("修改") }}</span>
                  </div>
                  <div class="new_table_svg_group" @click.stop="handleDelete(scope.row)" v-hasPermi="['system:notice:remove']">
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

      <!-- 添加或修改公告对话框 -->
      <el-dialog :title="$tp(title)" v-model="open" width="50%" append-to-body>
         <el-form ref="noticeRef" :model="form" :rules="rules" label-width="80px">
            <el-row>
               <el-col :span="12">
                  <el-form-item :label="$tp('公告标题')" prop="noticeTitle">
                     <el-input v-model="form.noticeTitle" :placeholder="$tp('请输入公告标题')" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item :label="$tp('公告类型')" prop="noticeType">
                     <el-select v-model="form.noticeType" :placeholder="$tp('请选择')">
                        <el-option
                           v-for="dict in sys_notice_type"
                           :key="dict.value"
                           :label="dict.label"
                           :value="dict.value"
                        ></el-option>
                     </el-select>
                  </el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item :label="$tp('状态')">
                     <el-radio-group v-model="form.status">
                        <el-radio
                           v-for="dict in sys_notice_status"
                           :key="dict.value"
                           :value="dict.value"
                        >{{ dict.label }}</el-radio>
                     </el-radio-group>
                  </el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item :label="$tp('内容')">
                    <editor v-model="form.noticeContent" :min-height="192"/>
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

<script setup name="Notice">
import { listNotice, getNotice, delNotice, addNotice, updateNotice } from "@/api/system/notice";
import { clacPXToVW } from "@/utils/index";

const { proxy } = getCurrentInstance();
const { sys_notice_status, sys_notice_type } = proxy.useDict("sys_notice_status", "sys_notice_type");

const noticeList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const searchData = computed(() => [
  { get label() { return translatePhrase("操作人员") }, value: 'createBy', type: 'text', default: '' },
  {
    get label() { return translatePhrase("类型") },
    value: 'noticeType',
    type: 'select',
    option: (sys_notice_type.value || []).map(d => ({ label: d.label, value: d.value })),
    default: undefined
  }
]);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);

const toolbarButtons = computed(() => [
  { get name() { return translatePhrase("修改") }, svg: 'edit', disabled: single.value, permi: ['system:notice:edit'], clickFn: () => handleUpdate() },
  { get name() { return translatePhrase("删除") }, svg: 'delete', disabled: multiple.value, permi: ['system:notice:remove'], clickFn: () => handleDelete() }
]);
const total = ref(0);
const title = ref("");

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    noticeTitle: undefined,
    createBy: undefined,
    status: undefined
  },
  rules: {
    noticeTitle: [{ required: true, get message() { return translatePhrase("公告标题不能为空") }, trigger: "blur" }],
    noticeType: [{ required: true, get message() { return translatePhrase("公告类型不能为空") }, trigger: "change" }]
  },
});

const { queryParams, form, rules } = toRefs(data);

/** 查询公告列表 */
function getList() {
  loading.value = true;
  listNotice(queryParams.value).then(response => {
    noticeList.value = response.rows;
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
    noticeId: undefined,
    noticeTitle: undefined,
    noticeType: undefined,
    noticeContent: undefined,
    status: "0"
  };
  proxy.resetForm("noticeRef");
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

function searchResetFn(val) {
  queryParams.value.pageNum = 1;
  queryParams.value.noticeTitle = val.noticeTitle || undefined;
  queryParams.value.createBy = val.createBy || undefined;
  queryParams.value.noticeType = val.noticeType || undefined;
  getList();
}

function resetQuery() {
  queryParams.value.noticeTitle = undefined;
  queryParams.value.createBy = undefined;
  queryParams.value.noticeType = undefined;
  handleQuery();
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.noticeId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  open.value = true;
  title.value = "添加公告";
}

/**修改按钮操作 */
function handleUpdate(row) {
  reset();
  const noticeId = row?.noticeId || ids.value;
  getNotice(noticeId).then(response => {
    form.value = response.data;
    open.value = true;
    title.value = "修改公告";
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["noticeRef"].validate(valid => {
    if (valid) {
      if (form.value.noticeId != undefined) {
        updateNotice(form.value).then(response => {
          proxy.$modal.msgSuccess(translatePhrase("修改成功"));
          open.value = false;
          getList();
        });
      } else {
        addNotice(form.value).then(response => {
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
  const noticeIds = row?.noticeId || ids.value
  proxy.$modal.confirm(translatePhrase("是否确认删除公告编号为\"") + noticeIds + translatePhrase("\"的数据项？")).then(function() {
    return delNotice(noticeIds);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess(translatePhrase("删除成功"));
  }).catch(() => {});
}

getList();
</script>
