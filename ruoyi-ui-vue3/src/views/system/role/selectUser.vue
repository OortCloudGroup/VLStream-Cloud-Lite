<template>
   <!-- 授权用户 -->
   <el-dialog :title="$tp('选择用户')" v-model="visible" width="50%" top="5vh" append-to-body>
      <div class="searchHeight_out flexRowAC" style="margin-bottom: 12px; justify-content: flex-end;">
         <search-height-box keyword="userName" :placeholder="$tp('请输入用户名称等关键词')" :data="searchData" @handle="searchResetFn" />
         <export-excel-pdf />
      </div>
      <el-row>
         <table-self @row-click="clickRow" ref="refTable" :data="userList" @selection-change="handleSelectionChange" height="260px" class="new_table" header-cell-class-name="header_tenant_cell" stripe>
            <el-table-column type="selection" :width="clacPXToVW(55)"></el-table-column>
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
         </table-self>
         <pagination
            v-show="total > 0"
            :total="total"
            v-model:page="queryParams.pageNum"
            v-model:limit="queryParams.pageSize"
            @pagination="getList"
         />
      </el-row>
      <template #footer>
         <div class="dialog-footer">
            <el-button type="primary" @click="handleSelectUser">{{ $tp("确 定") }}</el-button>
            <el-button @click="visible = false">{{ $tp("取 消") }}</el-button>
         </div>
      </template>
   </el-dialog>
</template>

<script setup name="SelectUser">
import { clacPXToVW } from "@/utils/index";
import { authUserSelectAll, unallocatedUserList } from "@/api/system/role";

const props = defineProps({
  roleId: {
    type: [Number, String]
  }
});

const { proxy } = getCurrentInstance();
const { sys_normal_disable } = proxy.useDict("sys_normal_disable");

const userList = ref([]);
const visible = ref(false);
const total = ref(0);
const userIds = ref([]);

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  roleId: undefined,
  userName: undefined,
  phonenumber: undefined
});

const searchData = [
  { get label() { return translatePhrase("手机号码") }, value: 'phonenumber', type: 'text', default: '' }
];

// 显示弹框
function show() {
  queryParams.roleId = props.roleId;
  getList();
  visible.value = true;
}

/**选择行 */
function clickRow(row) {
  proxy.$refs["refTable"].toggleRowSelection(row);
}

// 多选框选中数据
function handleSelectionChange(selection) {
  userIds.value = selection.map(item => item.userId);
}

// 查询表数据
function getList() {
  unallocatedUserList(queryParams).then(res => {
    userList.value = res.rows;
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
  queryParams.userName = val.userName || undefined;
  queryParams.phonenumber = val.phonenumber || undefined;
  getList();
}

const emit = defineEmits(["ok"]);
/** 选择授权用户操作 */
function handleSelectUser() {
  const roleId = queryParams.roleId;
  const uIds = userIds.value.join(",");
  if (uIds == "") {
    proxy.$modal.msgError(translatePhrase("请选择要分配的用户"));
    return;
  }
  authUserSelectAll({ roleId: roleId, userIds: uIds }).then(res => {
    proxy.$modal.msgSuccess(res.msg);
    visible.value = false;
    emit("ok");
  });
}

defineExpose({
  show,
});
</script>
