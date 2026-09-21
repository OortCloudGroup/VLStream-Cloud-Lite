<template>
  <div class="app-container">
    <div class="toolbar-with-search">
      <div class="toolbar-left">
        <button type="button" class="exportBtn newBtn flexRowAC" @click="handleAdd" v-hasPermi="['wvp:record:add']">
          <el-icon class="BtnImg"><Plus /></el-icon>{{ $tp("新增") }}
        </button>
      </div>
      <div class="searchHeight_out flexRowAC">
        <search-height-box
          keyword="query"
          :placeholder="$tp('请输入关键字')"
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
      :data="recordList"
      current-row-key="id"
    >
      <el-table-column prop="name" :label="$tp('名称')" align="center" show-overflow-tooltip/>
      <el-table-column prop="channelCount" :label="$tp('关联通道')" align="center"/>
      <el-table-column prop="updateTime" :label="$tp('更新时间')" align="center"/>
      <el-table-column prop="createTime" :label="$tp('创建时间')" align="center"/>
      <el-table-column :label="$tp('操作')" align="right" fixed="right" :width="clacPXToVW(220)">
        <template #default="scope">
          <div class="operateAppBox flexRowAC" style="justify-content: flex-end;">
            <div class="new_table_svg_group" @click.stop="handleLink(scope.row)" v-hasPermi="['wvp:record:channelList']">
              <span>{{ $tp("关联通道") }}</span>
            </div>
            <div class="new_table_svg_group" @click.stop="handleEdit(scope.row)" v-hasPermi="['wvp:record:edit']">
              <el-icon><Edit /></el-icon>
              <span>{{ $tp("编辑") }}</span>
            </div>
            <div class="new_table_svg_group" @click.stop="handleDelete(scope.row)" v-hasPermi="['wvp:record:delete']">
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

    <el-dialog :title="$tp(title)" v-model="open" width="720px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item :label="$tp('名称')" prop="name">
          <el-input v-model="form.name" :placeholder="$tp('请输入名称')" />
        </el-form-item>
        <el-form-item :label="$tp('录像时间')">
          <el-radio-group v-model="repeat">
            <el-radio value="day">{{ $tp("每天") }}</el-radio>
            <el-radio value="next">{{ $tp("隔天") }}</el-radio>
            <el-radio value="week">{{ $tp("每周") }}</el-radio>
            <el-radio value="month">{{ $tp("每月") }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="open" label-width="0" class="timeline-form-item">
          <RecordPlanTimeline ref="timelineRef" v-model="planItemList" :repeat="repeat" />
        </el-form-item>
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

<script setup name="RecordPlan">
import { addRecord, deleteRecord, getRecord, listRecord, updateRecord } from "../../../api/wvp/record.js";
import RecordPlanTimeline from "./RecordPlanTimeline.vue";
import { ElMessage } from "element-plus";
import router from "@/router";
import { clacPXToVW } from "@/utils/index";

const { proxy } = getCurrentInstance();

const loading = ref(false);
const total = ref(0);
const recordList = ref([]);
const searchData = ref([]);
const title = ref("");
const open = ref(false);
const repeat = ref("day");
const planItemList = ref([]);
const timelineRef = ref(null);

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    query: undefined,
  },
  rules: {
    name: [{ required: true, get message() { return translatePhrase("请输入名称") }, trigger: "blur" }],
  }
});

const { queryParams, form, rules } = toRefs(data);

function getList() {
  loading.value = true;
  listRecord(queryParams.value).then(res => {
    total.value = res.total;
    recordList.value = res.rows;
    loading.value = false;
  });
}

function searchResetFn(val) {
  queryParams.value.pageNum = 1;
  queryParams.value.query = val.query || undefined;
  getList();
}

function reset() {
  form.value = {
    id: undefined,
    name: undefined,
    planItemList: undefined,
  };
  repeat.value = "day";
  planItemList.value = [];
  proxy.resetForm("formRef");
}

function handleAdd() {
  reset();
  open.value = true;
  title.value = "新增录像计划";
  nextTick(() => {
    timelineRef.value?.reset();
  });
}

function cancel() {
  reset();
  open.value = false;
}

function handleEdit(row) {
  reset();
  open.value = true;
  title.value = "修改录像计划";
  getRecord(row.id).then(res => {
    form.value.name = res.data.name;
    form.value.id = res.data.id;
    const items = res.data.planItemList || [];
    nextTick(() => {
      const inferred = timelineRef.value?.fromPlanItemList(items) || "day";
      repeat.value = inferred;
      planItemList.value = timelineRef.value?.toPlanItemList(inferred) || items;
    });
  });
}

function handleDelete(row) {
  proxy.$modal.confirm(translatePhrase("是否确认删除该录制计划？")).then(function () {
    deleteRecord(row.id).then(() => {
      ElMessage({
        type: "success",
        message: translatePhrase("删除成功"),
      });
      getList();
    });
  });
}

function handleLink(row) {
  router.push(`/recordPlan/associatedChannel/index/${row.id}`);
}

function submitForm() {
  proxy.$refs["formRef"].validate(valid => {
    if (!valid) return;
    const items = timelineRef.value?.toPlanItemList() || planItemList.value || [];
    if (!items.length) {
      proxy.$modal.msgWarning(translatePhrase("请选择录像时间段"));
      return;
    }
    form.value.planItemList = items;
    const req = form.value.id != undefined ? updateRecord(form.value) : addRecord(form.value);
    req.then(() => {
      open.value = false;
      getList();
      proxy.$modal.msgSuccess(form.value.id != undefined ? translatePhrase("修改成功") : translatePhrase("新增成功"));
    });
  });
}

getList();
</script>

<style scoped>
.timeline-form-item {
  margin-bottom: 0;
}

.timeline-form-item :deep(.el-form-item__content) {
  display: block;
  width: 100%;
}
</style>
