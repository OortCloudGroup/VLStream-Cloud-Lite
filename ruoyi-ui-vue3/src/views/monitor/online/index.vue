<template>
   <div class="app-container">
      <div class="toolbar-with-search">
         <div class="toolbar-left" />
         <div class="searchHeight_out flexRowAC">
            <search-height-box
               keyword="userName"
               :placeholder="$tp('请输入用户名称、登录地址等关键词')"
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
         :data="onlineList.slice((pageNum - 1) * pageSize, pageNum * pageSize)"
         style="width: 100%;"
      >
         <el-table-column :label="$tp('序号')" :width="clacPXToVW(55)" type="index" align="center">
            <template #default="scope">
               <span>{{ (pageNum - 1) * pageSize + scope.$index + 1 }}</span>
            </template>
         </el-table-column>
         <el-table-column :label="$tp('会话编号')" align="center" prop="tokenId" :show-overflow-tooltip="true" />
         <el-table-column :label="$tp('登录名称')" align="center" prop="userName" :show-overflow-tooltip="true" />
         <el-table-column :label="$tp('所属部门')" align="center" prop="deptName" :show-overflow-tooltip="true" />
         <el-table-column :label="$tp('主机')" align="center" prop="ipaddr" :show-overflow-tooltip="true" />
         <el-table-column :label="$tp('登录地点')" align="center" prop="loginLocation" :show-overflow-tooltip="true" />
         <el-table-column :label="$tp('操作系统')" align="center" prop="os" :show-overflow-tooltip="true" />
         <el-table-column :label="$tp('浏览器')" align="center" prop="browser" :show-overflow-tooltip="true" />
         <el-table-column :label="$tp('登录时间')" align="center" prop="loginTime">
            <template #default="scope">
               <span>{{ parseTime(scope.row.loginTime) }}</span>
            </template>
         </el-table-column>
         <el-table-column :label="$tp('操作')" align="right" fixed="right" :width="clacPXToVW(120)">
            <template #default="scope">
               <div class="operateAppBox flexRowAC" style="justify-content: flex-end;">
                  <div
                     class="new_table_svg_group"
                     @click.stop="handleForceLogout(scope.row)"
                     v-hasPermi="['monitor:online:forceLogout']"
                  >
                     <el-icon><Delete /></el-icon>
                     <span>{{ $tp("强退") }}</span>
                  </div>
               </div>
            </template>
         </el-table-column>
      </table-self>

      <pagination v-show="total > 0" :total="total" v-model:page="pageNum" v-model:limit="pageSize" />
   </div>
</template>

<script setup name="Online">
import { forceLogout, list as initData } from "@/api/monitor/online";
import { clacPXToVW } from "@/utils/index";

const { proxy } = getCurrentInstance();

const onlineList = ref([]);
const loading = ref(true);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(10);

const queryParams = ref({
  ipaddr: undefined,
  userName: undefined
});

const searchData = ref([
  { get label() { return translatePhrase("登录地址") }, value: 'ipaddr', type: 'text', default: '' }
]);

/** 查询登录日志列表 */
function getList() {
  loading.value = true;
  initData(queryParams.value).then(response => {
    onlineList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

/** 高级搜索 / 重置 */
function searchResetFn(val) {
  pageNum.value = 1;
  queryParams.value.userName = val.userName || undefined;
  queryParams.value.ipaddr = val.ipaddr || undefined;
  getList();
}

/** 强退按钮操作 */
function handleForceLogout(row) {
    proxy.$modal.confirm(translatePhrase("是否确认强退名称为\"") + row.userName + translatePhrase("\"的用户?")).then(function () {
  return forceLogout(row.tokenId);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess(translatePhrase("删除成功"));
  }).catch(() => {});
}

getList();
</script>
