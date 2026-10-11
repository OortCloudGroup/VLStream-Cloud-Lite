<template>
  <el-dialog v-model="visible" :title="mode === 'service' ? $tp('国标设备接入帮助') : $t('gbPlatform.helpTitle')" width="min(920px, 94vw)" append-to-body destroy-on-close>
    <div class="gb-help">
      <p class="help-intro">{{ mode === 'service' ? $tp('把本平台的国标服务参数填写到摄像机、NVR 等设备的 GB28181 配置中，再验证注册与视频回流。') : $t('gbPlatform.helpIntro') }}</p>
      <p class="help-topology">{{ $t('gbPlatform.topology') }}</p>
      <el-tabs v-model="tab">
        <el-tab-pane :label="$t('gbPlatform.fieldsTab')" name="fields">
          <p>{{ $t('gbPlatform.exampleNotice') }}</p>
          <el-table :data="mode === 'service' ? serviceFields : platformFields" border style="width: 100%">
            <el-table-column prop="label" :label="$t('gbPlatform.fieldColumn')" min-width="170" />
            <el-table-column prop="example" :label="$t('gbPlatform.exampleColumn')" min-width="205" />
            <el-table-column prop="description" :label="$t('gbPlatform.descriptionColumn')" min-width="260" />
          </el-table>
          <p v-if="mode === 'service'" class="help-note">{{ $tp("服务信息列出多个 IP 时，只选设备能访问的一个地址；不要把逗号分隔的地址列表整体填入设备。容器或虚拟网卡地址通常不能直接给外部设备使用。") }}</p>
          <p v-else class="help-note">{{ $t('gbPlatform.identityNotice') }}</p>
        </el-tab-pane>
        <el-tab-pane :label="$t('gbPlatform.checksTab')" name="checks">
          <p>{{ $t('gbPlatform.checkIntro') }}</p>
          <ol>
            <li v-for="key in ['networkCheck', 'registerCheck', 'catalogCheck', 'playbackCheck', 'renewalCheck', 'acceptanceCheck']" :key="key">{{ $t(`gbPlatform.${key}`) }}</li>
          </ol>
          <p class="help-note">{{ $t('gbPlatform.verificationNotice') }}</p>
        </el-tab-pane>
      </el-tabs>
    </div>
    <template #footer>
      <a :href="guideUrl" download="VLStream-国标接入与多上级对接经验.md" class="download-guide">{{ $t('gbPlatform.downloadHelp') }}</a>
      <el-button @click="visible = false">{{ $t('gbPlatform.closeHelp') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import guideUrl from '@/assets/docs/gb28181-guide.md?url'
import { platformFields, serviceFields } from '@/utils/gbHelp'
defineProps({ mode: { type: String, default: 'platform' } })
const visible = defineModel({ type: Boolean, default: false })
const tab = ref('fields')
</script>

<style scoped>
.gb-help { color: var(--el-text-color-regular); line-height: 1.8; max-height: 65vh; overflow-y: auto; padding-right: 8px; }
.help-intro { margin-top: 0; }
.help-topology { padding: 15px; background: var(--el-color-primary-light-9); color: var(--el-color-primary); border-radius: 8px; text-align: center; font-weight: 600; }
.help-note { padding: 12px 16px; background: var(--el-fill-color-light); border-left: 3px solid var(--el-color-primary); border-radius: 4px; }
li { margin-bottom: 14px; }
.download-guide { display: inline-block; margin: 8px 18px 8px 0; color: var(--el-color-primary); text-decoration: underline; }
</style>
