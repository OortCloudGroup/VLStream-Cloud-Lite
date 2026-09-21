
<template>
   <div class="detail-page">
      <detail-page-header
         :parent-title="$tp('角色管理')"
         :title="$tp('分配用户')"
         back-path="/system/role"
      />
      <div class="detail-body">
      <div class="toolbar-with-search">
         <div class="toolbar-left">
            <button type="button" class="exportBtn newBtn flexRowAC" @click="openSelectUser" v-hasPermi="['system:role:add']">
               <el-icon class="BtnImg"><Plus /></el-icon>{{ $tp("添加用户") }}
            </button>
            <button-group :button-list="toolbarButtons" />
         </div>
         <div class="searchHeight_out flexRowAC">
            <search-height-box keyword="userName" :placeholder="$tp('请输入用户名称等关键词')" :data="searchData" @handle="searchResetFn" />
            <export-excel-pdf />
         </div>
      </div>

      <table-self
         class="new_table"
         header-cell-class-name="header_tenant_cell"
         stripe
         v-loading="loading"
         :data="userList"
         current-row-key="userId"
         @selection-change="handleSelectionChange"
      >
         <el-table-column type="selection" :width="clacPXToVW(55)" align="center" />
         <el-table-column :label="$tp('用户名称')" prop="userName" :show-overflow-tooltip="true" />
         <el-table-column :label="$tp('用户昵称')" prop="nickName" :show-overflow-tooltip="true" />
         <el-table-column :label="$tp('邮箱')" prop="email" :show-overflow-tooltip="true" />
         <el-table-column :label="$tp('手机')" prop="phonenumber" :show-overflow-tooltip="true" />
         <el-table-column :label="$tp('状态')" align="center" prop="status">
            <template #default="scope">
               <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
            </template>
         </el-table-column>
         <el-table-column :label="$tp('创建时间')" align="center" prop="createTime" :width="clacPXToVW(180)">
            <template #default="scope">
               <span>{{ parseTime(scope.row.createTime) }}</span>
            </template>
         </el-table-column>
         <el-table-column :label="$tp('操作')" align="right" fixed="right" :width="clacPXToVW(140)">
            <template #default="scope">
               <div class="operateAppBox flexRowAC" style="justify-content: flex-end;">
                  <div class="new_table_svg_group" @click.stop="cancelAuthUser(scope.row)" v-hasPermi="['system:role:remove']">
                     <span>{{ $tp("取消授权") }}</span>
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
      <select-user ref="selectRef" :roleId="queryParams.roleId" @ok="handleQuery" />
      </div>
   </div>
</template>

<script setup name="AuthUser">
import selectUser from "./selectUser";
import { allocatedUserList, authUserCancel, authUserCancelAll } from "@/api/system/role";
import { clacPXToVW } from "@/utils/index";

const route = useRoute();
const { proxy } = getCurrentInstance();
const { sys_normal_disable } = proxy.useDict("sys_normal_disable");

const userList = ref([]);
const loading = ref(true);
const multiple = ref(true);
const total = ref(0);
const userIds = ref([]);

const toolbarButtons = computed(() => [
  { get name() { return translatePhrase("批量取消授权") }, svg: 'delete', disabled: multiple.value, permi: ['system:role:remove'], clickFn: () => cancelAuthUserAll() },
  { get name() { return translatePhrase("关闭") }, svg: 'operate', clickFn: () => handleClose() }
]);

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  roleId: route.params.roleId,
  userName: undefined,
  phonenumber: undefined,
});

const searchData = [
  { get label() { return translatePhrase("手机号码") }, value: 'phonenumber', type: 'text', default: '' }
];

/** 查询授权用户列表 */
function getList() {
  loading.value = true;
  allocatedUserList(queryParams).then(response => {
    userList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

/** 返回按钮 */
function handleClose() {
  const obj = { path: "/system/role" };
  proxy.$tab.closeOpenPage(obj);
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.pageNum = 1;
  getList();
}

function searchResetFn(val) {
  queryParams.pageNum = 1;
  queryParams.userName = val.userName || undefined;
  queryParams.phonenumber = val.phonenumber || undefined;
  getList();
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  userIds.value = selection.map(item => item.userId);
  multiple.value = !selection.length;
}

/** 打开授权用户表弹窗 */
function openSelectUser() {
  proxy.$refs["selectRef"].show();
}

/** 取消授权按钮操作 */
function cancelAuthUser(row) {
  proxy.$modal.confirm(translatePhrase("确认要取消该用户\"") + row.userName + translatePhrase("\"角色吗？")).then(function () {
    return authUserCancel({ userId: row.userId, roleId: queryParams.roleId });
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess(translatePhrase("取消授权成功"));
  }).catch(() => {});
}

/** 批量取消授权按钮操作 */
function cancelAuthUserAll(row) {
  const roleId = queryParams.roleId;
  const uIds = userIds.value.join(",");
  proxy.$modal.confirm(translatePhrase("是否取消选中用户授权数据项?")).then(function () {
    return authUserCancelAll({ roleId: roleId, userIds: uIds });
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess(translatePhrase("取消授权成功"));
  }).catch(() => {});
}

getList();
</script>
