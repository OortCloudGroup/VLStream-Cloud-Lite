import { currentLocale } from './index'

// Stable system dictionary codes. Custom/business dictionaries intentionally keep their server labels.
const dictionaries = {
  sys_normal_disable: {
    '0': { 'zh-CN': '正常', 'en-US': 'Active' },
    '1': { 'zh-CN': '停用', 'en-US': 'Disabled' }
  },
  sys_user_sex: {
    '0': { 'zh-CN': '男', 'en-US': 'Male' },
    '1': { 'zh-CN': '女', 'en-US': 'Female' },
    '2': { 'zh-CN': '未知', 'en-US': 'Unknown' }
  },
  sys_yes_no: {
    Y: { 'zh-CN': '是', 'en-US': 'Yes' },
    N: { 'zh-CN': '否', 'en-US': 'No' }
  },
  sys_show_hide: {
    '0': { 'zh-CN': '显示', 'en-US': 'Shown' },
    '1': { 'zh-CN': '隐藏', 'en-US': 'Hidden' }
  },
  sys_job_status: {
    '0': { 'zh-CN': '正常', 'en-US': 'Active' },
    '1': { 'zh-CN': '暂停', 'en-US': 'Paused' }
  },
  sys_common_status: {
    '0': { 'zh-CN': '正常', 'en-US': 'Active' },
    '1': { 'zh-CN': '关闭', 'en-US': 'Closed' }
  },
  sys_notice_type: {
    '1': { 'zh-CN': '通知', 'en-US': 'Notice' },
    '2': { 'zh-CN': '公告', 'en-US': 'Announcement' }
  },
  sys_notice_status: {
    '0': { 'zh-CN': '正常', 'en-US': 'Published' },
    '1': { 'zh-CN': '关闭', 'en-US': 'Closed' }
  },
  sys_oper_type: {
    '0': { 'zh-CN': '其它', 'en-US': 'Other' },
    '1': { 'zh-CN': '新增', 'en-US': 'Create' },
    '2': { 'zh-CN': '修改', 'en-US': 'Update' },
    '3': { 'zh-CN': '删除', 'en-US': 'Delete' },
    '4': { 'zh-CN': '授权', 'en-US': 'Authorize' },
    '5': { 'zh-CN': '导出', 'en-US': 'Export' },
    '6': { 'zh-CN': '导入', 'en-US': 'Import' },
    '7': { 'zh-CN': '强退', 'en-US': 'Force Sign-out' },
    '8': { 'zh-CN': '生成代码', 'en-US': 'Generate Code' },
    '9': { 'zh-CN': '清空数据', 'en-US': 'Clear Data' }
  }
}

export function translateDictValue(dictType, value, fallback) {
  const entry = dictionaries[dictType]?.[String(value)]
  if (!entry) return fallback
  return entry[currentLocale.value] || entry['en-US'] || fallback
}
