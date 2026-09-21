<template>
  <section class="reliability-guide" aria-labelledby="reliability-title">
    <div class="guide-heading">
      <div class="guide-icon"><el-icon :size="24"><CircleCheck /></el-icon></div>
      <div class="guide-intro">
        <h2 id="reliability-title">{{ $tp("对接与运维经验") }}</h2>
        <p>{{ $tp("沉淀现场联调中的问题、验证方法与处理经验，让后续对接有据可查。") }}</p>
      </div>
      <el-button type="primary" plain @click="visible = true">{{ $tp("查看经验说明") }}<el-icon class="button-arrow"><ArrowRight /></el-icon></el-button>
    </div>
    <ol class="recovery-path">
      <li v-for="(step, index) in topics" :key="step.title">
        <span class="step-number">0{{ index + 1 }}</span>
        <div><h3>{{ step.title }}</h3><p>{{ $tp(step.summary) }}</p></div>
      </li>
    </ol>
    <p class="guide-note"><el-icon><InfoFilled /></el-icon>{{ $tp("支持对接多个上级平台，分别配置与联调。经验来自具体现场，应用时需核对设备、平台及网络条件。") }}</p>

    <el-dialog v-model="visible" :title="$tp('对接与运维经验')" width="min(820px, 94vw)" append-to-body destroy-on-close>
      <div class="guide-detail">
        <p class="detail-lead">{{ $tp("平台支持多上级级联，每个上级独立配置。以下将现场案例整理为可复用的排查方法；其中双上级联调是经验来源之一，具体参数需按实际环境确认。") }}</p>
        <el-tabs v-model="activeTab">
          <el-tab-pane :label="$tp('对接经验')" name="cascade">
            <ol class="detail-list">
              <li><strong>{{ $tp("注册与续期：") }}</strong>{{ $tp("逐个核对上级的注册应答、有效期和连续心跳。401 挑战可以是正常认证流程，403 需要核对身份、凭据与接入授权。") }}</li>
              <li><strong>{{ $tp("目录与共享编号：") }}</strong>{{ $tp("同一上级的共享视频编号应唯一，且与平台节点编号区分；上级点播使用实际共享编号。") }}</li>
              <li><strong>{{ $tp("信令与媒体：") }}</strong>{{ $tp("媒体服务器可能与信令服务器不同 IP，按协商的 UDP/TCP 模式核对端口、出口 NAT 和回程流量。") }}</li>
              <li><strong>{{ $tp("画面与持续播放：") }}</strong>{{ $tp("收到媒体流不等于解码成功。在各个已对接上级分别持续播放，并覆盖至少一次续注册周期。") }}</li>
            </ol>
            <p class="detail-callout">{{ $tp("现场曾通过调整编码恢复画面，这属于特定设备与平台的兼容处理，不能推广为所有设备都必须使用 H.265。") }}</p>
          </el-tab-pane>
          <el-tab-pane :label="$tp('恢复经验')" name="mechanisms">
            <article v-for="step in steps" :key="step.title" class="detail-section">
              <h3>{{ step.title }} <el-tag size="small" effect="plain">{{ $tp(step.owner) }}</el-tag></h3>
              <p>{{ $tp(step.detail) }}</p>
            </article>
          </el-tab-pane>
          <el-tab-pane :label="$tp('验收与边界')" name="acceptance">
            <ol class="detail-list">
              <li>{{ $tp("确认 BIOS 来电自动开机，并实际测试恢复供电后无需按电源键。") }}</li>
              <li>{{ $tp("在隔离环境验证服务退出、缓存重启、数据库备份恢复和网络恢复。") }}</li>
              <li>{{ $tp("正常重启后核对服务健康、设备及共享配置，并逐路验证视频。") }}</li>
              <li>{{ $tp("各个已对接上级分别完成注册、心跳、目录、点播和停止验证。") }}</li>
              <li>{{ $tp("安排真实断电冷启动验收；正常重启和强制退出不能替代这一步。") }}</li>
            </ol>
            <p class="detail-callout">{{ $tp("2026-09-13 的现场记录已完成正常重启和本地视频验证；来电自动开机、真实断电和当时下线的双上级联调仍待现场验收。该记录不代表本机当前状态。") }}</p>
            <p>{{ $tp("无供电、磁盘损坏、硬件死机及外部网络或设备故障，无法仅靠应用软件保证恢复。") }}</p>
          </el-tab-pane>
          <el-tab-pane :label="$tp('经验记录')" name="records">
            <p>{{ $tp("每次联调按以下内容记录，供后续相似问题检索、复现和复验。") }}</p>
            <ol class="detail-list">
              <li><strong>{{ $tp("场景与现象：") }}</strong>{{ $tp("记录设备/平台版本、编码、传输方式和触发条件，例如“注册正常，但上级黑屏”。") }}</li>
              <li><strong>{{ $tp("排查与证据：") }}</strong>{{ $tp("保留脱敏的 SIP 事务、目录编号、媒体报文及解码结果，区分观察事实与原因推测。") }}</li>
              <li><strong>{{ $tp("处理与复验：") }}</strong>{{ $tp("记录修改项、前后对照、验证结果和回滚方法；尽量一次只调整一类配置。") }}</li>
              <li><strong>{{ $tp("适用范围：") }}</strong>{{ $tp("注明在哪些设备和上级验证通过、哪些仍待确认，避免将单次成功的参数当成统一配置。") }}</li>
            </ol>
            <p class="detail-callout">{{ $tp("示例：某布控球 H.264 在特定上级有流无画面，切换 H.265 后恢复。可沉淀为编码兼容排查经验；不能据此要求所有设备统一改为 H.265。") }}</p>
          </el-tab-pane>
        </el-tabs>
      </div>
      <template #footer><el-button @click="visible = false">{{ $tp("关闭说明") }}</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { ArrowRight, CircleCheck, InfoFilled } from '@element-plus/icons-vue'

const visible = ref(false)
const activeTab = ref('cascade')
const topics = [
  { get title() { return translatePhrase("注册与续期") }, get summary() { return translatePhrase("身份认证 · 有效期核对") } },
  { get title() { return translatePhrase("目录与编号") }, get summary() { return translatePhrase("共享映射 · 上级授权") } },
  { get title() { return translatePhrase("媒体与画面") }, get summary() { return translatePhrase("网络链路 · 编码兼容") } },
  { get title() { return translatePhrase("恢复与复验") }, get summary() { return translatePhrase("无人值守 · 经验记录") } }
]
const steps = [
  { get title() { return translatePhrase("供电恢复") }, get summary() { return translatePhrase("来电开机 · 硬件保障") }, get owner() { return translatePhrase("现场配置") }, get detail() { return translatePhrase("工控机 BIOS 需启用来电自动开机。操作系统未启动时，应用无法自行恢复；需要现场完成硬件冷启动验证。") } },
  { get title() { return translatePhrase("服务启动") }, get summary() { return translatePhrase("依赖就绪 · 异常重试") }, get owner() { return translatePhrase("部署环境") }, get detail() { return translatePhrase("容器退出恢复依赖重启策略；开机编排、持续健康巡检、失败阈值和冷却时间需要部署配置。设备或上级离线不应触发整套服务反复重启。") } },
  { get title() { return translatePhrase("业务恢复") }, get summary() { return translatePhrase("配置持久保存 · 会话重建") }, get owner() { return translatePhrase("项目与部署") }, get detail() { return translatePhrase("设备和共享配置依赖业务数据库；工作台布局、位置数据同样需要可靠保存。缓存清空后的媒体与注册状态需要重建，并通过实际重启验证。") } },
  { get title() { return translatePhrase("视频复验") }, get summary() { return translatePhrase("本地点播 · 多上级实播") }, get owner() { return translatePhrase("现场验收") }, get detail() { return translatePhrase("检查设备回流、本地解码和各个已对接上级的画面。注册在线、目录推送成功、媒体发送成功，都不能单独证明视频恢复。") } }
]
</script>

<style scoped lang="scss">
.reliability-guide { margin-top: 24px; padding: 26px; border: 1px solid var(--el-border-color-light); border-radius: 14px; background: linear-gradient(115deg, rgba(64, 158, 255, .12), transparent 65%), var(--el-bg-color); color: var(--el-text-color-primary); }
.guide-heading { display: flex; align-items: center; gap: 16px; }
.guide-icon { display: grid; place-items: center; flex: 0 0 48px; height: 48px; border-radius: 14px; background: var(--el-color-primary-light-8); color: var(--el-color-primary); }
.guide-intro { flex: 1; }
h2 { margin: 0; font-size: 19px; font-weight: 600; }
.guide-intro p, .recovery-path p { margin: 7px 0 0; color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.6; }
.button-arrow { margin-left: 8px; }
.recovery-path { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); padding: 22px 0; margin: 22px 0 0; list-style: none; border-top: 1px solid var(--el-border-color-light); gap: 18px; }
.recovery-path li { display: flex; gap: 12px; align-items: flex-start; }
.step-number { font-size: 20px; font-weight: 600; color: var(--el-color-primary); font-variant-numeric: tabular-nums; }
.recovery-path h3 { margin: 1px 0 0; font-size: 14px; font-weight: 600; }
.guide-note { display: flex; align-items: flex-start; gap: 8px; margin: 0; font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.7; }
.guide-note .el-icon { flex-shrink: 0; margin-top: 3px; }
.guide-detail { line-height: 1.85; color: var(--el-text-color-regular); max-height: 65vh; overflow-y: auto; }
.detail-lead { margin-top: 0; }
.detail-section { padding: 8px 0; }
.detail-section h3 { display: flex; align-items: center; gap: 12px; margin: 0; font-size: 15px; }
.detail-section p { margin: 8px 0; }
.detail-list { padding-left: 22px; }
.detail-list li { margin-bottom: 15px; }
.detail-callout { background: var(--el-fill-color-light); border-left: 3px solid var(--el-color-primary); padding: 12px 16px; border-radius: 4px; }
@media (max-width: 1100px) { .recovery-path { grid-template-columns: repeat(2, minmax(0, 1fr)); } .guide-heading { flex-wrap: wrap; } }
@media (max-width: 600px) { .reliability-guide { padding: 18px; } .guide-icon { display: none; } .guide-intro { flex-basis: 100%; } h2 { font-size: 17px; } .recovery-path { grid-template-columns: 1fr; } }
</style>
