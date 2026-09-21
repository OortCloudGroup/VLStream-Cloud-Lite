<template>
  <div class="exportExcelPDF flexRowAC">
    <el-dropdown trigger="hover">
      <el-button class="exportBtn Btn2 flexRowAC" :title="$tp('导出')">
        <svg-icon class="BtnImg export" icon-class="export" />
        <div class="line" />
        <el-icon>
          <ArrowDown />
        </el-icon>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item :disabled="exportItem.isDisabledExcel" @click.stop="emits('handle', 'Excel')">
            <img class="exportImg" src="@/assets/images/search/exportExcel.png" alt="" />
            Excel
          </el-dropdown-item>
          <el-dropdown-item :disabled="exportItem.isDisabledPDF" @click.stop="emits('handle', 'PDF')">
            <img class="exportImg" src="@/assets/images/search/exportPDF.png" alt="" />
            PDF
          </el-dropdown-item>
          <el-dropdown-item :disabled="exportItem.isDisabledHTML" @click.stop="emits('handle', 'HTML')">
            <img class="exportImg" src="@/assets/images/search/exportHTM.png" alt="" />
            HTML
          </el-dropdown-item>
          <el-dropdown-item :disabled="exportItem.isDisabledXML" @click.stop="emits('handle', 'XML')">
            <img class="exportImg" src="@/assets/images/search/exportXML.png" alt="" />
            XML
          </el-dropdown-item>
          <el-dropdown-item :disabled="exportItem.isDisabledJSON" @click.stop="emits('handle', 'JSON')">
            <img class="exportImg" src="@/assets/images/search/exportJSON.png" alt="" />
            JSON
          </el-dropdown-item>
          <el-dropdown-item :disabled="exportItem.isDisabledCSV" @click.stop="emits('handle', 'CSV')">
            <img class="exportImg" src="@/assets/images/search/exportCSV.png" alt="" />
            CSV
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'

const props = defineProps({
  item: {
    type: Object,
    default: () => ({})
  }
})
const emits = defineEmits(['handle'])

const exportItem = computed(() => ({
  isDisabledExcel: props.item.isDisabledExcel !== false,
  isDisabledPDF: props.item.isDisabledPDF !== false,
  isDisabledHTML: props.item.isDisabledHTML !== false,
  isDisabledXML: props.item.isDisabledXML !== false,
  isDisabledJSON: props.item.isDisabledJSON !== false,
  isDisabledCSV: props.item.isDisabledCSV !== false
}))
</script>

<style lang="scss" scoped>
.exportImg {
  width: 20px;
  height: 20px;
  margin-right: 4px;
}
</style>
