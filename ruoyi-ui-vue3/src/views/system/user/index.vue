<template>
  <div class="app-container">
    <el-row :gutter="20">
      <splitpanes :horizontal="appStore.device === 'mobile'" class="default-theme">
        <!--部门数据-->
        <pane size="16">
          <el-col>
            <div class="head-container">
              <el-input v-model="deptName" :placeholder="$tp('请输入部门名称')" clearable prefix-icon="Search" style="margin-bottom: 20px" />
            </div>
            <div class="head-container">
              <el-tree :data="deptOptions" :props="{ label: 'label', children: 'children' }" :expand-on-click-node="false" :filter-node-method="filterNode" ref="deptTreeRef" node-key="id" highlight-current default-expand-all @node-click="handleNodeClick" />
            </div>
          </el-col>
        </pane>
        <!--用户数据-->
        <pane size="84">
          <el-col>
            <div class="toolbar-with-search">
              <div class="toolbar-left">
                <button type="button" class="exportBtn newBtn flexRowAC" @click="handleAdd" v-hasPermi="['system:user:add']">
                  <el-icon class="BtnImg"><Plus /></el-icon>{{ $tp("新增") }}
                </button>
                <button-group :button-list="toolbarButtons" />
              </div>
              <div class="searchHeight_out flexRowAC">
                <search-height-box
                  keyword="userName"
                  :placeholder="$tp('请输入用户名称等关键词')"
                  :data="searchData"
                  @handle="searchResetFn"
                />
                <export-excel-pdf :item="{ isDisabledExcel: false }" @handle="handleExportType" />
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
              <el-table-column :label="$tp('用户编号')" align="center" key="userId" prop="userId" v-if="columns[0].visible" />
              <el-table-column :label="$tp('用户名称')" align="center" key="userName" prop="userName" v-if="columns[1].visible" :show-overflow-tooltip="true" />
              <el-table-column :label="$tp('用户昵称')" align="center" key="nickName" prop="nickName" v-if="columns[2].visible" :show-overflow-tooltip="true" />
              <el-table-column :label="$tp('部门')" align="center" key="deptName" prop="dept.deptName" v-if="columns[3].visible" :show-overflow-tooltip="true" />
              <el-table-column :label="$tp('手机号码')" align="center" key="phonenumber" prop="phonenumber" v-if="columns[4].visible" :width="clacPXToVW(120)" />
              <el-table-column :label="$tp('状态')" align="center" key="status" v-if="columns[5].visible" :width="clacPXToVW(100)">
                <template #default="scope">
                  <el-switch
                    v-model="scope.row.status"
                    active-value="0"
                    inactive-value="1"
                    @change="handleStatusChange(scope.row)"
                  ></el-switch>
                </template>
              </el-table-column>
              <el-table-column :label="$tp('创建时间')" align="center" prop="createTime" v-if="columns[6].visible" :width="clacPXToVW(160)">
                <template #default="scope">
                  <span>{{ parseTime(scope.row.createTime) }}</span>
                </template>
              </el-table-column>
              <el-table-column :label="$tp('操作')" align="right" fixed="right" :width="clacPXToVW(220)">
                <template #default="scope">
                  <div class="operateAppBox flexRowAC" style="justify-content: flex-end;" v-if="scope.row.userId !== 1">
                    <div class="new_table_svg_group" @click.stop="handleUpdate(scope.row)" v-hasPermi="['system:user:edit']">
                      <el-icon><Edit /></el-icon>
                      <span>{{ $tp("修改") }}</span>
                    </div>
                    <div class="new_table_svg_group" @click.stop="handleDelete(scope.row)" v-hasPermi="['system:user:remove']">
                      <el-icon><Delete /></el-icon>
                      <span>{{ $tp("删除") }}</span>
                    </div>
                    <el-dropdown @command="(command)=>{userMoreClick(command, scope.row)}">
                      <div class="new_table_svg_group" @click.stop>
                        <span>{{ $tp("更多") }}</span>
                        <el-icon><ArrowDown /></el-icon>
                      </div>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item command="handleResetPwd" v-if="checkPermi(['system:user:resetPwd'])">{{ $tp("重置密码") }}</el-dropdown-item>
                          <el-dropdown-item command="handleAuthRole" v-if="checkPermi(['system:user:edit'])">{{ $tp("分配角色") }}</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </template>
              </el-table-column>
            </table-self>
            <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
          </el-col>
        </pane>
      </splitpanes>
    </el-row>

    <!-- 添加或修改用户配置对话框 -->
    <el-dialog :title="$tp(title)" v-model="open" width="40%" append-to-body>
      <el-form :model="form" :rules="rules" ref="userRef" label-width="80px">
        <el-row>
          <el-col :span="12">
            <el-form-item :label="$tp('用户昵称')" prop="nickName">
              <el-input v-model="form.nickName" :placeholder="$tp('请输入用户昵称')" maxlength="30" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tp('归属部门')" prop="deptId">
              <el-tree-select v-model="form.deptId" :data="enabledDeptOptions" :props="{ value: 'id', label: 'label', children: 'children' }" value-key="id" :placeholder="$tp('请选择归属部门')" check-strictly />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item :label="$tp('手机号码')" prop="phonenumber">
              <el-input v-model="form.phonenumber" :placeholder="$tp('请输入手机号码')" maxlength="11" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tp('邮箱')" prop="email">
              <el-input v-model="form.email" :placeholder="$tp('请输入邮箱')" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item v-if="form.userId == undefined" :label="$tp('用户名称')" prop="userName">
              <el-input v-model="form.userName" :placeholder="$tp('请输入用户名称')" maxlength="30" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="form.userId == undefined" :label="$tp('用户密码')" prop="password">
              <el-input v-model="form.password" :placeholder="$tp('请输入用户密码')" type="password" maxlength="20" show-password />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item :label="$tp('用户性别')">
              <el-select v-model="form.sex" :placeholder="$tp('请选择')">
                <el-option v-for="dict in sys_user_sex" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tp('状态')">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item :label="$tp('岗位')">
              <el-select v-model="form.postIds" multiple :placeholder="$tp('请选择')">
                <el-option v-for="item in postOptions" :key="item.postId" :label="item.postName" :value="item.postId" :disabled="item.status == 1"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tp('角色')">
              <el-select v-model="form.roleIds" multiple :placeholder="$tp('请选择')">
                <el-option v-for="item in roleOptions" :key="item.roleId" :label="item.roleName" :value="item.roleId" :disabled="item.status == 1"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="24">
            <el-form-item :label="$tp('备注')">
              <el-input v-model="form.remark" type="textarea" :placeholder="$tp('请输入内容')"></el-input>
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

    <!-- 用户导入对话框 -->
    <el-dialog :title="$tp(upload.title)" v-model="upload.open" width="26%" append-to-body>
      <el-upload ref="uploadRef" :limit="1" accept=".xlsx, .xls" :headers="upload.headers" :action="upload.url + '?updateSupport=' + upload.updateSupport" :disabled="upload.isUploading" :on-progress="handleFileUploadProgress" :on-success="handleFileSuccess" :auto-upload="false" drag>
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">{{ $tp("将文件拖到此处，或") }}<em>{{ $tp("点击上传") }}</em></div>
        <template #tip>
          <div class="el-upload__tip text-center">
            <div class="el-upload__tip">
              <el-checkbox v-model="upload.updateSupport" />{{ $tp("是否更新已经存在的用户数据") }}
            </div>
            <span>{{ $tp("仅允许导入xls、xlsx格式文件。") }}</span>
            <el-link type="primary" :underline="false" style="font-size: 12px; vertical-align: baseline" @click="importTemplate">{{ $tp("下载模板") }}</el-link>
          </div>
        </template>
      </el-upload>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitFileForm">{{ $tp("确 定") }}</el-button>
          <el-button @click="upload.open = false">{{ $tp("取 消") }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="User">
import { getToken } from "@/utils/auth";
import useAppStore from '@/store/modules/app'
import { changeUserStatus, listUser, resetUserPwd, delUser, getUser, updateUser, addUser, deptTreeSelect } from "@/api/system/user";
import { Splitpanes, Pane } from "splitpanes"
import "splitpanes/dist/splitpanes.css"
import { checkPermi } from "@/utils/permission";
import { clacPXToVW } from "@/utils/index";

const router = useRouter();
const appStore = useAppStore()
const { proxy } = getCurrentInstance();
const { sys_normal_disable, sys_user_sex } = proxy.useDict("sys_normal_disable", "sys_user_sex");

const userList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const searchData = computed(() => [
  { get label() { return translatePhrase("手机号码") }, value: 'phonenumber', type: 'text', default: '' },
  {
    get label() { return translatePhrase("状态") },
    value: 'status',
    type: 'select',
    option: (sys_normal_disable.value || []).map(d => ({ label: d.label, value: d.value })),
    default: undefined
  },
  {
    get label() { return translatePhrase("创建时间") },
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
  { get name() { return translatePhrase("修改") }, svg: 'edit', disabled: single.value, permi: ['system:user:edit'], clickFn: () => handleUpdate() },
  { get name() { return translatePhrase("删除") }, svg: 'delete', disabled: multiple.value, permi: ['system:user:remove'], clickFn: () => handleDelete() },
  { get name() { return translatePhrase("导入") }, svg: 'upload', permi: ['system:user:import'], clickFn: () => handleImport() }
]);
const total = ref(0);
const title = ref("");
const dateRange = ref([]);
const deptName = ref("");
const deptOptions = ref(undefined);
const enabledDeptOptions = ref(undefined);
const initPassword = ref(undefined);
const postOptions = ref([]);
const roleOptions = ref([]);
/*** 用户导入参数 */
const upload = reactive({
  // 是否显示弹出层（用户导入）
  open: false,
  // 弹出层标题（用户导入）
  title: "",
  // 是否禁用上传
  isUploading: false,
  // 是否更新已经存在的用户数据
  updateSupport: 0,
  // 设置上传的请求头部
  headers: { Authorization: "Bearer " + getToken() },
  // 上传的地址
  url: import.meta.env.VITE_APP_BASE_API + "/system/user/importData"
});
// 列显隐信息
const columns = ref([
  { key: 0, get label() { return translatePhrase("用户编号") }, visible: true },
  { key: 1, get label() { return translatePhrase("用户名称") }, visible: true },
  { key: 2, get label() { return translatePhrase("用户昵称") }, visible: true },
  { key: 3, get label() { return translatePhrase("部门") }, visible: true },
  { key: 4, get label() { return translatePhrase("手机号码") }, visible: true },
  { key: 5, get label() { return translatePhrase("状态") }, visible: true },
  { key: 6, get label() { return translatePhrase("创建时间") }, visible: true }
]);

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    userName: undefined,
    phonenumber: undefined,
    status: undefined,
    deptId: undefined
  },
  rules: {
    userName: [{ required: true, get message() { return translatePhrase("用户名称不能为空") }, trigger: "blur" }, { min: 2, max: 20, get message() { return translatePhrase("用户名称长度必须介于 2 和 20 之间") }, trigger: "blur" }],
    nickName: [{ required: true, get message() { return translatePhrase("用户昵称不能为空") }, trigger: "blur" }],
    password: [{ required: true, get message() { return translatePhrase("用户密码不能为空") }, trigger: "blur" }, { min: 5, max: 20, get message() { return translatePhrase("用户密码长度必须介于 5 和 20 之间") }, trigger: "blur" }, { pattern: /^[^<>"'|\\]+$/, get message() { return translatePhrase("不能包含非法字符：< > \" ' \\ |") }, trigger: "blur" }],
    email: [{ type: "email", get message() { return translatePhrase("请输入正确的邮箱地址") }, trigger: ["blur", "change"] }],
    phonenumber: [{ pattern: /^1[3|4|5|6|7|8|9][0-9]\d{8}$/, get message() { return translatePhrase("请输入正确的手机号码") }, trigger: "blur" }]
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 通过条件过滤节点  */
const filterNode = (value, data) => {
  if (!value) return true;
  return data.label.indexOf(value) !== -1;
};

/** 根据名称筛选部门树 */
watch(deptName, val => {
  proxy.$refs["deptTreeRef"].filter(val);
});

/** 查询用户列表 */
function getList() {
  loading.value = true;
  listUser(proxy.addDateRange(queryParams.value, dateRange.value)).then(res => {
    loading.value = false;
    userList.value = res.rows;
    total.value = res.total;
  });
};

/** 查询部门下拉树结构 */
function getDeptTree() {
  deptTreeSelect().then(response => {
    deptOptions.value = response.data;
    enabledDeptOptions.value = filterDisabledDept(JSON.parse(JSON.stringify(response.data)));
  });
};

/** 过滤禁用的部门 */
function filterDisabledDept(deptList) {
  return deptList.filter(dept => {
    if (dept.disabled) {
      return false;
    }
    if (dept.children && dept.children.length) {
      dept.children = filterDisabledDept(dept.children);
    }
    return true;
  });
};

/** 节点单击事件 */
function handleNodeClick(data) {
  queryParams.value.deptId = data.id;
  handleQuery();
};

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
};

/** 高级搜索 / 重置 */
function searchResetFn(val) {
  queryParams.value.pageNum = 1;
  queryParams.value.userName = val.userName || undefined;
  queryParams.value.phonenumber = val.phonenumber || undefined;
  queryParams.value.status = val.status || undefined;
  dateRange.value = val.dateRange && val.dateRange.length ? val.dateRange : [];
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  dateRange.value = [];
  queryParams.value.userName = undefined;
  queryParams.value.phonenumber = undefined;
  queryParams.value.status = undefined;
  queryParams.value.deptId = undefined;
  proxy.$refs.deptTreeRef.setCurrentKey(null);
  handleQuery();
};

/** 删除按钮操作 */
function handleDelete(row) {
  const userIds = row?.userId || ids.value;
  proxy.$modal.confirm(translatePhrase("是否确认删除用户编号为\"") + userIds + translatePhrase("\"的数据项？")).then(function () {
    return delUser(userIds);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess(translatePhrase("删除成功"));
  }).catch(() => {});
};

/** 导出按钮操作 */
function handleExport() {
  proxy.download("system/user/export", {
    ...queryParams.value,
  },`user_${new Date().getTime()}.xlsx`);
};

function handleExportType(type) {
  if (type === 'Excel') {
    handleExport();
  }
}

/** 用户状态修改  */
function handleStatusChange(row) {
  let text = row.status === "0" ? "启用" : "停用";
  proxy.$modal.confirm(translatePhrase("确认要\"") + text + '""' + row.userName + translatePhrase("\"用户吗?")).then(function () {
    return changeUserStatus(row.userId, row.status);
  }).then(() => {
    proxy.$modal.msgSuccess(text + translatePhrase("成功"));
  }).catch(function () {
    row.status = row.status === "0" ? "1" : "0";
  });
};

/** 更多操作 */
function handleCommand(command, row) {
  switch (command) {
    case "handleResetPwd":
      handleResetPwd(row);
      break;
    case "handleAuthRole":
      handleAuthRole(row);
      break;
    default:
      break;
  }
};

/** 跳转角色分配 */
function handleAuthRole(row) {
  const userId = row.userId;
  router.push("/system/user-auth/role/" + userId);
}

function userMoreClick(command, row) {
  if (command === "handleResetPwd") {
    handleResetPwd(row);
  } else if (command === "handleAuthRole") {
    handleAuthRole(row);
  }
};

/** 重置密码按钮操作 */
function handleResetPwd(row) {
  proxy.$prompt('请输入"' + row.userName + '"的新密码', "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    closeOnClickModal: false,
    inputPattern: /^.{5,20}$/,
    inputErrorMessage: "用户密码长度必须介于 5 和 20 之间",
    inputValidator: (value) => {
      if (/<|>|"|'|\||\\/.test(value)) {
        return "不能包含非法字符：< > \" ' \\\ |"
      }
    },
  }).then(({ value }) => {
    resetUserPwd(row.userId, value).then(response => {
      proxy.$modal.msgSuccess(translatePhrase("修改成功，新密码是：") + value);
    });
  }).catch(() => {});
};

/** 选择条数  */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.userId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
};

/** 导入按钮操作 */
function handleImport() {
  upload.title = translatePhrase("用户导入");
  upload.open = true;
};

/** 下载模板操作 */
function importTemplate() {
  proxy.download("system/user/importTemplate", {
  }, `user_template_${new Date().getTime()}.xlsx`);
};

/**文件上传中处理 */
const handleFileUploadProgress = (event, file, fileList) => {
  upload.isUploading = true;
};

/** 文件上传成功处理 */
const handleFileSuccess = (response, file, fileList) => {
  upload.open = false;
  upload.isUploading = false;
  proxy.$refs["uploadRef"].handleRemove(file);
  proxy.$alert("<div style='overflow: auto;overflow-x: hidden;max-height: 70vh;padding: 10px 20px 0;'>" + response.msg + "</div>", "导入结果", { dangerouslyUseHTMLString: true });
  getList();
};

/** 提交上传文件 */
function submitFileForm() {
  proxy.$refs["uploadRef"].submit();
};

/** 重置操作表单 */
function reset() {
  form.value = {
    userId: undefined,
    deptId: undefined,
    userName: undefined,
    nickName: undefined,
    password: undefined,
    phonenumber: undefined,
    email: undefined,
    sex: undefined,
    status: "0",
    remark: undefined,
    postIds: [],
    roleIds: []
  };
  proxy.resetForm("userRef");
};

/** 取消按钮 */
function cancel() {
  open.value = false;
  reset();
};

/** 新增按钮操作 */
function handleAdd() {
  reset();
  getUser().then(response => {
    postOptions.value = response.posts;
    roleOptions.value = response.roles;
    open.value = true;
    title.value = "添加用户";
    form.value.password = initPassword.value;
  });
};

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  const userId = row?.userId || ids.value;
  getUser(userId).then(response => {
    form.value = response.data;
    postOptions.value = response.posts;
    roleOptions.value = response.roles;
    form.value.postIds = response.postIds;
    form.value.roleIds = response.roleIds;
    open.value = true;
    title.value = "修改用户";
    form.password = "";
  });
};

/** 提交按钮 */
function submitForm() {
  proxy.$refs["userRef"].validate(valid => {
    if (valid) {
      if (form.value.userId != undefined) {
        updateUser(form.value).then(response => {
          proxy.$modal.msgSuccess(translatePhrase("修改成功"));
          open.value = false;
          getList();
        });
      } else {
        addUser(form.value).then(response => {
          proxy.$modal.msgSuccess(translatePhrase("新增成功"));
          open.value = false;
          getList();
        });
      }
    }
  });
};

getDeptTree();
getList();
</script>
