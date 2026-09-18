import { computed, ref } from 'vue'
import { createI18n } from 'vue-i18n'
import Cookies from 'js-cookie'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import es from 'element-plus/es/locale/lang/es'
import ar from 'element-plus/es/locale/lang/ar'
import de from 'element-plus/es/locale/lang/de'
import fr from 'element-plus/es/locale/lang/fr'
import ja from 'element-plus/es/locale/lang/ja'
import ptBr from 'element-plus/es/locale/lang/pt-br'
import ru from 'element-plus/es/locale/lang/ru'
import ko from 'element-plus/es/locale/lang/ko'
import id from 'element-plus/es/locale/lang/id'
import tr from 'element-plus/es/locale/lang/tr'
import messages from './catalog'

export const DEFAULT_LOCALE = 'zh-CN'
export const LOCALE_COOKIE = 'language'

export const supportedLocales = [
  { code: 'zh-CN', shortCode: 'zh', name: '简体中文', dir: 'ltr', element: zhCn },
  { code: 'en-US', shortCode: 'en', name: 'English', dir: 'ltr', element: en },
  { code: 'es-MX', shortCode: 'es', name: 'Español', dir: 'ltr', element: es },
  { code: 'ar-SA', shortCode: 'ar', name: 'العربية', dir: 'rtl', element: ar },
  { code: 'de-DE', shortCode: 'de', name: 'Deutsch', dir: 'ltr', element: de },
  { code: 'fr-FR', shortCode: 'fr', name: 'Français', dir: 'ltr', element: fr },
  { code: 'ja-JP', shortCode: 'ja', name: '日本語', dir: 'ltr', element: ja },
  { code: 'pt-BR', shortCode: 'pt', name: 'Português', dir: 'ltr', element: ptBr },
  { code: 'ru-RU', shortCode: 'ru', name: 'Русский', dir: 'ltr', element: ru },
  { code: 'ko-KR', shortCode: 'ko', name: '한국어', dir: 'ltr', element: ko },
  { code: 'id-ID', shortCode: 'id', name: 'Bahasa Indonesia', dir: 'ltr', element: id },
  { code: 'tr-TR', shortCode: 'tr', name: 'Türkçe', dir: 'ltr', element: tr }
]

const localeByCode = new Map(supportedLocales.map(item => [item.code.toLowerCase(), item]))
const localeByLanguage = new Map(supportedLocales.map(item => [item.shortCode, item]))

export function normalizeLocale(value) {
  if (!value) return null
  const normalized = String(value).trim().replace('_', '-').toLowerCase()
  return localeByCode.get(normalized)?.code || localeByLanguage.get(normalized.split('-')[0])?.code || null
}

function detectInitialLocale() {
  const saved = normalizeLocale(Cookies.get(LOCALE_COOKIE) || localStorage.getItem(LOCALE_COOKIE))
  if (saved) return saved
  const browserLocales = navigator.languages?.length ? navigator.languages : [navigator.language]
  for (const browserLocale of browserLocales) {
    const matched = normalizeLocale(browserLocale)
    if (matched) return matched
  }
  return DEFAULT_LOCALE
}

export const currentLocale = ref(detectInitialLocale())

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: currentLocale.value,
  fallbackLocale: DEFAULT_LOCALE,
  missingWarn: false,
  fallbackWarn: false,
  messages
})

export const elementLocale = computed(() => {
  return localeByCode.get(currentLocale.value.toLowerCase())?.element || zhCn
})

export function applyDocumentLocale(locale) {
  const config = localeByCode.get(locale.toLowerCase()) || localeByCode.get(DEFAULT_LOCALE.toLowerCase())
  document.documentElement.lang = config.code
  document.documentElement.dir = config.dir
  document.body?.setAttribute('dir', config.dir)
}

export function setLocale(locale) {
  const normalized = normalizeLocale(locale) || DEFAULT_LOCALE
  currentLocale.value = normalized
  i18n.global.locale.value = normalized
  Cookies.set(LOCALE_COOKIE, normalized, { expires: 365, sameSite: 'Lax' })
  localStorage.setItem(LOCALE_COOKIE, normalized)
  applyDocumentLocale(normalized)
  return normalized
}

export function translateRouteTitle(title) {
  if (!title) return ''
  const key = `menu.${title}`
  return i18n.global.te(key, currentLocale.value) ? i18n.global.t(key) : title
}

applyDocumentLocale(currentLocale.value)

export default i18n
