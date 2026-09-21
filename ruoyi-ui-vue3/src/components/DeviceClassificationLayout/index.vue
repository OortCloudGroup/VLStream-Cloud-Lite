<template>
  <div class="classification-layout">
    <aside v-yResize class="classification-sidebar">
      <el-tabs v-model="activeType" class="left-tabs" @tab-change="handleTabChange">
        <el-tab-pane v-for="tab in tabs" :key="tab.value" :label="tab.label" :name="tab.value" />
      </el-tabs>

      <div class="tree-title">{{ activeLabel }}</div>
      <div class="tree-search-content">
        <el-input v-model="treeSearchKeyword" :placeholder="$tp('搜索')" clearable prefix-icon="Search" />
      </div>

      <el-tree
        ref="treeRef"
        class="classification-tree"
        :data="displayTree"
        :props="treeProps"
        node-key="id"
        default-expand-all
        highlight-current
        :expand-on-click-node="false"
        :filter-node-method="filterNode"
        @node-click="handleNodeClick"
      >
        <template #empty>
          <div class="tree-empty" :class="{ readonly }" @click="openRootAdd">
            {{ readonly ? $tp('暂无数据') : $tp('暂无数据，点击新增') }}
          </div>
        </template>
        <template #default="{ node, data }">
          <div
            class="custom-tree-node"
            @mouseenter="hoveredTreeNodeId = data.id"
            @mouseleave="hoveredTreeNodeId = null"
          >
            <div class="tree-node-main">
              <el-icon class="tree-icon"><Folder /></el-icon>
              <el-tooltip :open-delay="500" effect="light" :content="node.label" placement="top">
                <div
                  class="tree-node-label"
                  :class="{ active: selectedCategory && String(selectedCategory.id) === String(data.id) }"
                  @dblclick.stop="handleEditNode(data)"
                >
                  {{ data.categoryName }} ({{ data.deviceCount || 0 }})
                </div>
              </el-tooltip>
            </div>
            <div
              v-if="!readonly"
              v-show="hoveredTreeNodeId === data.id || (selectedCategory && String(selectedCategory.id) === String(data.id))"
              class="tree-node-actions"
              @click.stop
            >
              <el-tooltip :content="$tp('删除')" placement="top">
                <el-icon class="tree-action-icon danger" @click="handleRemoveNode(data)"><Delete /></el-icon>
              </el-tooltip>
              <el-tooltip :content="$tp('新增子分类')" placement="top">
                <el-icon class="tree-action-icon" @click="handleAddChild(data)"><Plus /></el-icon>
              </el-tooltip>
            </div>
          </div>
        </template>
      </el-tree>

      <template v-if="showAssignment">
        <el-button class="assign-button" type="primary" plain :disabled="normalizedDeviceKeys.length === 0" @click="openAssignment">
          {{ $tp("设置分类") }}<span v-if="normalizedDeviceKeys.length">（{{ normalizedDeviceKeys.length }}）</span>
        </el-button>
        <div class="selection-hint">{{ $tp("勾选一台可单独设置，勾选多台可批量设置") }}</div>
      </template>
    </aside>

    <main class="classification-content">
      <div class="classification-content-body"><slot /></div>
    </main>

    <el-dialog v-model="categoryDialog.visible" :title="$tp(categoryDialog.mode === 'add' ? `新增${activeLabel}` : `修改${activeLabel}`)" width="30%" append-to-body>
      <el-form ref="categoryFormRef" :model="categoryForm" :rules="categoryRules" label-width="90px">
        <el-form-item :label="$tp('上级节点')" prop="parentId">
          <el-tree-select
            v-model="categoryForm.parentId"
            :data="parentOptions"
            node-key="id"
            check-strictly
            default-expand-all
            :props="treeProps"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$tp('分类名称')" prop="categoryName">
          <el-input v-model="categoryForm.categoryName" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item :label="$tp('显示顺序')" prop="sortNum">
          <el-input-number v-model="categoryForm.sortNum" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialog.visible = false">{{ $tp("取消") }}</el-button>
        <el-button type="primary" :loading="categoryDialog.saving" @click="submitCategory">{{ $tp("确定") }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-if="showAssignment" v-model="assignmentDialog.visible" :title="$tp('设置设备分类')" width="34%" append-to-body>
      <el-alert
        v-if="normalizedDeviceKeys.length > 1"
        :title="$tp('批量设置会用本次选择覆盖这些设备原有的区域、分组和标签')"
        type="warning"
        :closable="false"
        show-icon
        class="assignment-alert"
      />
      <el-form label-width="80px">
        <el-form-item :label="$tp('区域')">
          <el-tree-select v-model="assignmentForm.regionId" :data="treeCache.REGION.tree" node-key="id" check-strictly clearable default-expand-all :props="treeProps" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$tp('分组')">
          <el-tree-select v-model="assignmentForm.groupId" :data="treeCache.GROUP.tree" node-key="id" check-strictly clearable default-expand-all :props="treeProps" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$tp('标签')">
          <el-tree-select v-model="assignmentForm.tagIds" :data="treeCache.TAG.tree" node-key="id" multiple show-checkbox check-strictly clearable default-expand-all :props="treeProps" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignmentDialog.visible = false">{{ $tp("取消") }}</el-button>
        <el-button type="primary" :loading="assignmentDialog.saving" @click="submitAssignment">{{ $tp("保存") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { Delete, Folder, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addClassificationCategory,
  deleteClassificationCategory,
  getClassificationTree,
  getDeviceClassification,
  saveDeviceClassification,
  updateClassificationCategory
} from '@/api/device/classification'

const props = defineProps({
  protocolType: { type: String, required: true },
  selectedDeviceKeys: { type: Array, default: () => [] },
  showAssignment: { type: Boolean, default: true },
  readonly: { type: Boolean, default: false }
})
const emit = defineEmits(['filter-change', 'assigned'])

const tabs = [
  { get label() { return translatePhrase("区域") }, value: 'REGION' },
  { get label() { return translatePhrase("分组") }, value: 'GROUP' },
  { get label() { return translatePhrase("标签") }, value: 'TAG' }
]
const treeProps = { label: 'categoryName', children: 'children' }
const activeType = ref('REGION')
const treeRef = ref()
const selectedCategory = ref(null)
const hoveredTreeNodeId = ref(null)
const treeSearchKeyword = ref('')
const treeCache = reactive({
  REGION: { tree: [], totalCount: 0, unclassifiedCount: 0 },
  GROUP: { tree: [], totalCount: 0, unclassifiedCount: 0 },
  TAG: { tree: [], totalCount: 0, unclassifiedCount: 0 }
})

const normalizedDeviceKeys = computed(() => props.selectedDeviceKeys.filter(key => key !== null && key !== undefined && key !== '').map(key => String(key)))
const activeLabel = computed(() => tabs.find(tab => tab.value === activeType.value)?.label || '')
const displayTree = computed(() => treeCache[activeType.value].tree)

watch(treeSearchKeyword, value => treeRef.value?.filter(value))

function filterNode(value, data) {
  return !value || (data.categoryName || '').includes(value)
}

async function loadTree(type = activeType.value) {
  const response = await getClassificationTree(type, props.protocolType)
  const data = response.data || {}
  treeCache[type].tree = data.tree || []
  treeCache[type].totalCount = data.totalCount || 0
  treeCache[type].unclassifiedCount = data.unclassifiedCount || 0
}

async function loadAllTrees() {
  await Promise.all(tabs.map(tab => loadTree(tab.value)))
}

async function handleTabChange(type) {
  selectedCategory.value = null
  treeSearchKeyword.value = ''
  hoveredTreeNodeId.value = null
  await loadTree(type)
  emit('filter-change', { categoryType: undefined, categoryId: undefined, unclassified: undefined })
  nextTick(() => treeRef.value?.setCurrentKey(null))
}

function handleNodeClick(node) {
  selectedCategory.value = node
  emit('filter-change', { categoryType: activeType.value, categoryId: String(node.id), unclassified: false })
}

const categoryDialog = reactive({ visible: false, mode: 'add', saving: false })
const categoryFormRef = ref()
const categoryForm = reactive({ id: undefined, categoryType: 'REGION', parentId: '0', categoryName: '', sortNum: 0 })
const categoryRules = { categoryName: [{ required: true, get message() { return translatePhrase("请输入分类名称") }, trigger: 'blur' }] }

function cloneWithoutNode(nodes, excludedId) {
  return nodes.filter(node => String(node.id) !== String(excludedId)).map(node => ({
    ...node,
    children: cloneWithoutNode(node.children || [], excludedId)
  }))
}

const parentOptions = computed(() => [
  { id: '0', categoryName: '顶级节点', children: cloneWithoutNode(treeCache[activeType.value].tree, categoryDialog.mode === 'edit' ? categoryForm.id : null) }
])

function openCategoryDialog(mode, node) {
  const target = node || selectedCategory.value
  if (mode === 'edit' && !target) return
  categoryDialog.mode = mode
  categoryForm.id = mode === 'edit' ? String(target.id) : undefined
  categoryForm.categoryType = activeType.value
  categoryForm.parentId = mode === 'edit' ? String(target.parentId || 0) : (target ? String(target.id) : '0')
  categoryForm.categoryName = mode === 'edit' ? target.categoryName : ''
  categoryForm.sortNum = mode === 'edit' ? (target.sortNum || 0) : 0
  categoryDialog.visible = true
  nextTick(() => categoryFormRef.value?.clearValidate())
}

function openRootAdd() {
  if (!props.readonly) openCategoryDialog('add')
}

function handleAddChild(data) {
  openCategoryDialog('add', data)
}

function handleEditNode(data) {
  if (!props.readonly) openCategoryDialog('edit', data)
}

function handleRemoveNode(data) {
  removeCategory(data)
}

async function submitCategory() {
  await categoryFormRef.value.validate()
  categoryDialog.saving = true
  try {
    const payload = { ...categoryForm }
    if (categoryDialog.mode === 'add') await addClassificationCategory(payload)
    else await updateClassificationCategory(payload)
    ElMessage.success(translatePhrase("保存成功"))
    categoryDialog.visible = false
    selectedCategory.value = null
    await loadTree(activeType.value)
  } finally {
    categoryDialog.saving = false
  }
}

async function removeCategory(node) {
  const target = node || selectedCategory.value
  if (!target) return
  await ElMessageBox.confirm(translatePhrase("确认删除“{categoryName}”吗？", { categoryName: target.categoryName }), translatePhrase("提示"), { type: 'warning' })
  await deleteClassificationCategory(String(target.id))
  ElMessage.success(translatePhrase("删除成功"))
  selectedCategory.value = null
  await loadTree(activeType.value)
  emit('filter-change', { categoryType: undefined, categoryId: undefined, unclassified: undefined })
}

const assignmentDialog = reactive({ visible: false, saving: false })
const assignmentForm = reactive({ regionId: undefined, groupId: undefined, tagIds: [] })

async function openAssignment() {
  if (!normalizedDeviceKeys.value.length) return
  await loadAllTrees()
  assignmentForm.regionId = undefined
  assignmentForm.groupId = undefined
  assignmentForm.tagIds = []
  if (normalizedDeviceKeys.value.length === 1) {
    const response = await getDeviceClassification(props.protocolType, normalizedDeviceKeys.value[0])
    const data = response.data || {}
    assignmentForm.regionId = data.regionId ? String(data.regionId) : undefined
    assignmentForm.groupId = data.groupId ? String(data.groupId) : undefined
    assignmentForm.tagIds = (data.tagIds || []).map(id => String(id))
  }
  assignmentDialog.visible = true
}

async function submitAssignment() {
  assignmentDialog.saving = true
  try {
    await saveDeviceClassification({
      protocolType: props.protocolType,
      deviceKeys: normalizedDeviceKeys.value,
      regionId: assignmentForm.regionId,
      groupId: assignmentForm.groupId,
      tagIds: assignmentForm.tagIds
    })
    ElMessage.success(translatePhrase("分类设置成功"))
    assignmentDialog.visible = false
    await loadAllTrees()
    emit('assigned')
  } finally {
    assignmentDialog.saving = false
  }
}

onMounted(() => loadTree('REGION'))
</script>

<style lang="scss" scoped>
.classification-layout {
  display: flex;
  align-items: stretch;
  gap: 20px;
  min-height: calc(100vh - 84px);
  padding: 16px 20px;
  box-sizing: border-box;
  background: #fff;
  border-radius: 10px;
}

.classification-sidebar {
  width: 300px;
  padding-right: 20px;
  flex-shrink: 0;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.left-tabs {
  margin-bottom: 12px;
  flex-shrink: 0;

  :deep(.el-tabs__header) { margin: 0; border-bottom: 1px solid #e4e7ed; }
  :deep(.el-tabs__nav-wrap::after) { display: none; }
  :deep(.el-tabs__nav) { display: flex; width: 100%; }
  :deep(.el-tabs__item) { flex: 1; height: 40px; padding: 0; line-height: 40px; text-align: center; }
}

.tree-title {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 0 20px;
  color: var(--el-color-primary);
  flex-shrink: 0;

  &::before { content: ''; width: 3px; height: 18px; background: var(--el-color-primary); }
}

.tree-search-content {
  padding-bottom: 10px;
  flex-shrink: 0;

  :deep(.el-input__wrapper) { background: #fff; border: 1px solid #dcdfe6; border-radius: 4px; box-shadow: none; }
}

.classification-tree {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: #fff;
  scrollbar-width: none;
  -ms-overflow-style: none;

  &::-webkit-scrollbar { display: none; }
  :deep(.el-tree-node__content) {
    --el-tree-node-hover-bg-color: var(--el-menu-hover-bg-color);
    height: 38px;
    color: #333;
    font-size: 14px;
  }
  :deep(.el-tree-node.is-current.is-focusable > .el-tree-node__content) { color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
}

.custom-tree-node,
.tree-node-main,
.tree-node-actions { display: flex; align-items: center; }
.custom-tree-node { width: 100%; min-width: 0; justify-content: space-between; padding-right: 4px; }
.tree-node-main { flex: 1; min-width: 0; gap: 4px; overflow: hidden; }
.tree-node-label { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tree-node-label.active { color: var(--el-color-primary); }
.tree-node-actions { flex-shrink: 0; gap: 8px; margin-left: 8px; }
.tree-action-icon { cursor: pointer; color: var(--el-color-primary); }
.tree-action-icon.danger { color: var(--el-color-danger); }
.tree-icon { flex-shrink: 0; color: var(--el-color-primary); font-size: 14px; }

.assign-button { width: 100%; margin-top: 8px; flex-shrink: 0; }
.selection-hint { margin-top: 8px; color: #909399; font-size: 12px; line-height: 18px; text-align: center; flex-shrink: 0; }
.tree-empty { padding: 24px 0; color: #909399; font-size: 14px; text-align: center; cursor: pointer; }
.tree-empty.readonly { cursor: default; }

.classification-content { flex: 1; min-width: 0; min-height: 0; overflow: hidden; display: flex; flex-direction: column; }
.classification-content-body { flex: 1; min-height: 0; overflow: auto; }
.classification-content-body :deep(.app-container) { padding: 0; }
.assignment-alert { margin-bottom: 18px; }
</style>
