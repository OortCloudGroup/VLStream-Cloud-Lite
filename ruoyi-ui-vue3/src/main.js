import { createApp } from 'vue'

import Cookies from 'js-cookie'

import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import i18n from '@/i18n'

import '@/assets/styles/index.scss' // global css
import '@/assets/styles/i18n.scss'
import '@/assets/css/iconfont.css' // global css

import App from './App'
import store from './store'
import router from './router'
import directive from './directive' // directive

// 注册指令
import plugins from './plugins' // plugins
import { download } from '@/utils/request'
import { setToken } from '@/utils/auth'

// svg图标
import 'virtual:svg-icons-register'
import SvgIcon from '@/components/SvgIcon'
import elementIcons from '@/components/SvgIcon/svgicon'

import './permission' // permission control

import { useDict } from '@/utils/dict'
import { parseTime, resetForm, addDateRange, handleTree, selectDictLabel, selectDictLabels } from '@/utils/ruoyi'

// 分页组件
import Pagination from '@/components/Pagination'
// 自定义表格工具组件
import RightToolbar from '@/components/RightToolbar'
// 富文本组件
import Editor from "@/components/Editor"
// 文件上传组件
import FileUpload from "@/components/FileUpload"
// 图片上传组件
import ImageUpload from "@/components/ImageUpload"
// 图片预览组件
import ImagePreview from "@/components/ImagePreview"
// 字典标签组件
import DictTag from '@/components/DictTag'
import SearchHeightBox from '@/components/SearchHeightBox'
import ExportExcelPdf from '@/components/ExportExcelPdf'
import ButtonGroup from '@/components/ButtonGroup'
import TableSelf from '@/components/TableSelf'
import DetailPageHeader from '@/components/DetailPageHeader'
import { clacPXToVW } from '@/utils/index'

// 平台从 URL 传入 accessToken 时，需要在路由守卫执行前保存，避免落入传统登录流程。
const platformAccessToken = new URL(window.location.href).searchParams.get('accessToken')
if (platformAccessToken) {
  setToken(platformAccessToken)
}

const app = createApp(App)

// 全局方法挂载
app.config.globalProperties.useDict = useDict
app.config.globalProperties.download = download
app.config.globalProperties.parseTime = parseTime
app.config.globalProperties.resetForm = resetForm
app.config.globalProperties.handleTree = handleTree
app.config.globalProperties.addDateRange = addDateRange
app.config.globalProperties.selectDictLabel = selectDictLabel
app.config.globalProperties.selectDictLabels = selectDictLabels
app.config.globalProperties.clacPXToVW = clacPXToVW

// 全局组件挂载
app.component('DictTag', DictTag)
app.component('Pagination', Pagination)
app.component('FileUpload', FileUpload)
app.component('ImageUpload', ImageUpload)
app.component('ImagePreview', ImagePreview)
app.component('RightToolbar', RightToolbar)
app.component('Editor', Editor)
app.component('SearchHeightBox', SearchHeightBox)
app.component('ExportExcelPdf', ExportExcelPdf)
app.component('ButtonGroup', ButtonGroup)
app.component('TableSelf', TableSelf)
app.component('DetailPageHeader', DetailPageHeader)

app.use(router)
app.use(store)
app.use(i18n)
app.use(plugins)
app.use(elementIcons)
app.component('svg-icon', SvgIcon)

directive(app)

// 使用element-plus 并且设置全局的大小
app.use(ElementPlus, {
  // 支持 large、default、small；语言由根级 el-config-provider 动态切换
  size: Cookies.get('size') || 'default'
})

app.mount('#app')
