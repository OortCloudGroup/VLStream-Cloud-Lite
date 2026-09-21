<template>
  <!-- 导入表 -->
  <el-dialog :title="$tp('导入表')" v-model="visible" width="50%" top="5vh" append-to-body>
    <div class="searchHeight_out flexRowAC" style="margin-bottom: 12px; justify-content: flex-end;">
      <search-height-box keyword="tableName" :placeholder="$tp('请输入表名称等关键词')" :data="searchData" @handle="searchResetFn" />
      <export-excel-pdf />
    </div>
    <el-row>
      <table-self @row-click="clickRow" ref="table" :data="dbTableList" @selection-change="handleSelectionChange" height="260px" class="new_table" header-cell-class-name="header_tenant_cell" stripe>
        <el-table-column type="selection" :width="clacPXToVW(55)"></el-table-column>
        <el-table-column prop="tableName" :label="$tp('表名称')" :show-overflow-tooltip="true"></el-table-column>
        <el-table-column prop="tableComment" :label="$tp('表描述')" :show-overflow-tooltip="true"></el-table-column>
        <el-table-column prop="createTime" :label="$tp('创建时间')"></el-table-column>
        <el-table-column prop="updateTime" :label="$tp('更新时间')"></el-table-column>
      </table-self>
      <pagination
        v-show="total>0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-row>
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" @click="handleImportTable">{{ $tp("确 定") }}</el-button>
        <el-button @click="visible = false">{{ $tp("取 消") }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { clacPXToVW } from "@/utils/index";
import { listDbTable, importTable } from "@/api/tool/gen";

const total = ref(0);
const visible = ref(false);
const tables = ref([]);
const dbTableList = ref([]);
const { proxy } = getCurrentInstance();

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  tableName: undefined,
  tableComment: undefined
});

const searchData = [
  { get label() { return translatePhrase("表描述") }, value: 'tableComment', type: 'text', default: '' }
];

const emit = defineEmits(["ok"]);

/** 查询参数列表 */
function show() {
  getList();
  visible.value = true;
}

/** 单击选择行 */
function clickRow(row) {
  proxy.$refs.table.toggleRowSelection(row);
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  tables.value = selection.map(item => item.tableName);
}

/** 查询表数据 */
function getList() {
  listDbTable(queryParams).then(res => {
    dbTableList.value = res.rows;
    total.value = res.total;
  });
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.pageNum = 1;
  getList();
}

function searchResetFn(val) {
  queryParams.pageNum = 1;
  queryParams.tableName = val.tableName || undefined;
  queryParams.tableComment = val.tableComment || undefined;
  getList();
}

/** 导入按钮操作 */
function handleImportTable() {
  const tableNames = tables.value.join(",");
  if (tableNames == "") {
    proxy.$modal.msgError(translatePhrase("请选择要导入的表"));
    return;
  }
  importTable({ tables: tableNames }).then(res => {
    proxy.$modal.msgSuccess(res.msg);
    if (res.code === 200) {
      visible.value = false;
      emit("ok");
    }
  });
}

defineExpose({
  show,
});
</script>
