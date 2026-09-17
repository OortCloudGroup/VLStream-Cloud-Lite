/**
 * 产品顶栏虚拟分组：工作台 / 视频汇聚 / 系统设置
 *
 * 注意：若依一级 C 菜单（非目录）会建成 MenuFrame：
 *   { path: '/', children: [{ path: 'work', meta: { title: '工作台' } }] }
 * 匹配时必须看 children.path / meta.title，不能只看顶层 path。
 */

export const PRODUCT_TABS = [
  { key: 'workbench', title: '工作台' },
  { key: 'video', title: '视频汇聚' },
  { key: 'system', title: '系统设置' }
]

/** 工作台：按 path 片段 / 标题匹配 */
const WORKBENCH_KEYS = [
  { key: '/index', titles: ['首页'], icon: 'home' },
  { key: '/work', titles: ['工作台'], icon: 'workbench' }
]

/** 视频汇聚 - 电子地图 */
const VIDEO_MAP_KEYS = [
  { key: '/gIsMap', titles: ['电子地图'], icon: 'map' }
]

/**
 * 视频汇聚 - 设备管理下协议
 */
const DEVICE_PROTOCOLS = [
  { key: '/vlstream', title: 'VLStream协议', titles: ['VLStream', 'VLStream协议'], icon: 'vlstream' },
  { key: '/gbmanger', title: '国标协议', titles: ['国标协议', '国际协议'], icon: 'gb' },
  { key: '/onvif', title: 'ONVIF协议', titles: ['onvif协议', 'ONVIF协议', 'ONVIF'], icon: 'onvif' },
  { key: '/rtsp', title: 'RTSP协议', titles: ['rtsp协议', 'RTSP协议', 'RTSP'], icon: 'rtsp' },
  { key: '/isup', title: 'ISUP协议', titles: ['海康协议', 'ISUP协议', 'ISUP'], icon: 'isup' },
  { key: '/ehome', title: 'EHome协议', titles: ['EHome协议', 'EHome'], icon: 'ehome' },
  { key: '/dahua', title: '大华协议', titles: ['大华协议'], icon: 'dahua' },
  { key: '/yingshi', title: '萤石协议', titles: ['萤石协议', '萤石'], icon: 'device' },
  { key: '/lecheng', title: '乐橙协议', titles: ['乐橙协议', '乐橙'], icon: 'device' },
  { key: '/custom', title: '自定义协议', titles: ['自定义协议'], icon: 'device' }
]

const DEVICE_MANAGE_ICON = 'device'
const DEVICE_LEAF_TITLES = ['设备管理', '设备列表']
/** settings.svg 为内嵌 PNG，无法跟随主题色，改用矢量 system */
const FALLBACK_ICON = 'system'
/** 协议功能统一由左侧层级菜单承载，避免和顶部 Tab 重复。 */
const PROTOCOL_TAB_KEYS = []

function joinRoutePath(base, path) {
  if (path === undefined || path === null || path === '') return normalizePath(base)
  if (String(path).startsWith('/')) return normalizePath(path)
  return normalizePath((base || '') + '/' + path)
}

function isProtocolTabTarget(proto) {
  const seg = pathSeg(proto.key)
  return PROTOCOL_TAB_KEYS.includes(seg)
}

/**
 * 协议节点整形：
 * - 单叶子（如 VLStream/设备管理）：侧栏只显示协议名，去掉嵌套「设备管理」
 * - 多子菜单协议：保留完整左侧层级，确保全部功能入口可见
 */
function reshapeProtocolNode(route, proto) {
  let node = shallowCloneRoute(route, proto.title, proto.icon || DEVICE_MANAGE_ICON)
  if (node.path && node.path !== '/' && !String(node.path).startsWith('/')) {
    node.path = '/' + node.path
  }

  const showing = (node.children || []).filter(c => !c.hidden)
  const useTabs = isProtocolTabTarget(proto) && showing.length > 1

  if (useTabs) {
    node.alwaysShow = false
    node.meta = {
      ...(node.meta || {}),
      title: proto.title,
      icon: proto.icon || DEVICE_MANAGE_ICON,
      protocolTabs: true
    }
    // 侧栏仅保留第一项（标题用协议名），其余隐藏，供高亮 path 使用
    node.children = showing.map((child, index) => {
      const cloned = {
        ...child,
        meta: { ...(child.meta || {}) }
      }
      if (index === 0) {
        cloned.hidden = false
        cloned.meta.title = proto.title
        cloned.meta.icon = proto.icon || DEVICE_MANAGE_ICON
      } else {
        cloned.hidden = true
      }
      return cloned
    })
    return node
  }

  // 单子节点：折叠为协议名入口，不再展示嵌套「设备管理」
  node.alwaysShow = false
  if (showing.length === 1) {
    const only = showing[0]
    node.children = [{
      ...only,
      meta: {
        ...(only.meta || {}),
        title: proto.title,
        icon: proto.icon || only.meta?.icon || DEVICE_MANAGE_ICON
      }
    }]
  } else if (showing.length > 1) {
    node.alwaysShow = true
    node.children = showing.map(child => {
      const title = String(child.meta?.title || '')
      if (!DEVICE_LEAF_TITLES.includes(title)) return child
      return {
        ...child,
        meta: {
          ...(child.meta || {}),
          title: '设备管理',
          icon: DEVICE_MANAGE_ICON
        }
      }
    })
  }
  return node
}

/**
 * 兼容历史顶部 Tab 配置；当前协议入口均由左侧菜单提供。
 */
export function getProtocolTabs(allRoutes) {
  const routes = Array.isArray(allRoutes) ? allRoutes : []
  const visible = routes.filter(r => !r.hidden)
  const proto = DEVICE_PROTOCOLS.find(p => isProtocolTabTarget(p))
  if (!proto) return []
  const found = findRouteBySpec(visible, proto)
  if (!found) return []

  const base = found.path && String(found.path).startsWith('/')
    ? normalizePath(found.path)
    : normalizePath('/' + (found.path || pathSeg(proto.key)))

  return (found.children || [])
    .filter(c => !c.hidden && c.meta?.title)
    .map(c => ({
      title: String(c.meta.title),
      path: joinRoutePath(base, c.path)
    }))
}

/**
 * 当前路由是否落在历史协议 Tab 范围内
 */
export function matchProtocolTabsByRoute(route, allRoutes) {
  const tabs = getProtocolTabs(allRoutes)
  if (!tabs.length) return null

  const candidates = []
  if (route?.meta?.activeMenu) candidates.push(normalizePath(route.meta.activeMenu))
  if (route?.path) candidates.push(normalizePath(route.path))

  let active = null
  for (const path of candidates) {
    const hit = tabs.find(t => path === t.path || path.startsWith(t.path + '/'))
    if (hit) {
      active = hit.path
      break
    }
  }

  // 隐藏详情页等：path 以 /gbmanger 开头也归入
  if (!active) {
    const raw = normalizePath(route?.path || '')
    if (!raw.toLowerCase().startsWith('/gbmanger')) return null
    active = tabs[0].path
  }

  return {
    tabs,
    active,
    sidebarActive: tabs[0].path
  }
}

/**
 * 左侧有树、Tab 应放在右侧的国际协议页（国标设备 / 分屏监控）
 * 无树页（国标级联、录像计划等）仍用 AppMain 顶栏 Tab
 */
export function isProtocolTreeLayoutRoute(route) {
  const path = normalizePath(route?.meta?.activeMenu || route?.path || '').toLowerCase()
  if (!path) return false
  // 国标设备及设备相关详情
  if (path === '/gbmanger/device' || path.startsWith('/gbmanger/device/')) return true
  // 分屏监控：菜单 path 为 wvpLive → /gbmanger/wvplive
  if (
    path === '/gbmanger/wvplive' ||
    path.startsWith('/gbmanger/wvplive/') ||
    path === '/gbmanger/live' ||
    path.startsWith('/gbmanger/live/') ||
    /\/gbmanger\/.*live/.test(path)
  ) {
    return true
  }
  const title = String(route?.meta?.title || '')
  if (title.includes('分屏') && path.includes('/gbmanger')) return true
  return false
}

const WORKBENCH_PATHS = WORKBENCH_KEYS.map(i => i.key)
const VIDEO_MAP_PATHS = VIDEO_MAP_KEYS.map(i => i.key)
const VIDEO_DEVICE_KEYS = DEVICE_PROTOCOLS.map(p => p.key)
const VIDEO_ALL_KEYS = [...VIDEO_MAP_PATHS, ...VIDEO_DEVICE_KEYS]

function normalizePath(p) {
  if (p === undefined || p === null) return ''
  if (p === '') return ''
  const s = ('/' + String(p)).replace(/\/+/g, '/')
  return s.length > 1 && s.endsWith('/') ? s.slice(0, -1) : s
}

function pathSeg(key) {
  return normalizePath(key).replace(/^\//, '').toLowerCase()
}

function collectRouteTitles(route) {
  const titles = []
  if (route?.meta?.title) titles.push(String(route.meta.title))
  if (Array.isArray(route?.children)) {
    route.children.forEach(c => {
      if (c?.meta?.title) titles.push(String(c.meta.title))
    })
  }
  return titles
}

function collectRoutePathSegs(route) {
  const segs = new Set()
  const push = (p) => {
    const n = normalizePath(p)
    if (!n || n === '/') return
    segs.add(n.toLowerCase())
    segs.add(n.replace(/^\//, '').toLowerCase())
  }
  push(route?.path)
  if (Array.isArray(route?.children)) {
    route.children.forEach(c => push(c.path))
  }
  if (route?.meta?.link) push(route.meta.link)
  if (Array.isArray(route?.children)) {
    route.children.forEach(c => {
      if (c?.meta?.link) push(c.meta.link)
    })
  }
  return segs
}

/**
 * 判断路由是否对应某个业务 key（兼容 MenuFrame path='/'）
 */
function routeMatchKey(route, key, titles = []) {
  const target = normalizePath(key)
  const seg = pathSeg(key)
  if (!target && !titles.length) return false

  const pathSegs = collectRoutePathSegs(route)
  if (pathSegs.has(target.toLowerCase()) || pathSegs.has(seg)) {
    return true
  }

  if (target === '/index' && Array.isArray(route.children)) {
    if (route.children.some(c => normalizePath(c.path) === '/index' || c.path === 'index')) {
      return true
    }
  }

  if (titles.length) {
    const routeTitles = collectRouteTitles(route).map(t => t.toLowerCase())
    if (titles.some(t => routeTitles.includes(String(t).toLowerCase()))) {
      return true
    }
  }

  return false
}

/** 给路由及其可见子节点写入侧栏图标 */
function applyRouteIcon(route, icon) {
  if (!route || !icon) return route
  const cloned = {
    ...route,
    meta: { ...(route.meta || {}), icon }
  }
  if (Array.isArray(route.children) && route.children.length) {
    // MenuFrame（path=/ 且单子节点）侧栏展示的是子节点，图标写到子节点
    if (route.path === '/' || route.children.length === 1) {
      cloned.children = route.children.map(c => ({
        ...c,
        meta: { ...(c.meta || {}), icon }
      }))
    } else {
      cloned.children = route.children.map(c => ({
        ...c,
        meta: c.meta ? { ...c.meta } : {}
      }))
    }
  }
  return cloned
}

function shallowCloneRoute(route, titleOverride, iconOverride) {
  const cloned = {
    ...route,
    meta: route.meta ? { ...route.meta } : {}
  }
  if (titleOverride) {
    if (!cloned.meta || Object.keys(cloned.meta).length === 0) {
      cloned.meta = {
        title: titleOverride,
        icon: iconOverride || route.children?.[0]?.meta?.icon || FALLBACK_ICON
      }
    } else {
      cloned.meta.title = titleOverride
    }
  }
  if (iconOverride) {
    cloned.meta.icon = iconOverride
  }
  if (Array.isArray(route.children) && route.children.length) {
    if (titleOverride && route.children.length === 1 && route.path === '/') {
      cloned.children = route.children.map(c => ({
        ...c,
        meta: {
          ...(c.meta || {}),
          title: titleOverride || c.meta?.title,
          icon: iconOverride || c.meta?.icon
        }
      }))
      return cloned
    }
    cloned.children = route.children.map(c => ({
      ...c,
      meta: c.meta ? { ...c.meta } : {}
    }))
  }
  if (iconOverride) {
    return applyRouteIcon(cloned, iconOverride)
  }
  return cloned
}

function findRouteBySpec(routes, spec) {
  if (!Array.isArray(routes) || !spec) return null
  const titles = spec.titles || (spec.title ? [spec.title] : [])
  for (const route of routes) {
    if (route.hidden) continue
    if (routeMatchKey(route, spec.key, titles)) {
      return route
    }
  }
  return null
}

function isReservedRoute(route) {
  for (const spec of WORKBENCH_KEYS) {
    if (routeMatchKey(route, spec.key, spec.titles)) return true
  }
  for (const spec of VIDEO_MAP_KEYS) {
    if (routeMatchKey(route, spec.key, spec.titles)) return true
  }
  for (const proto of DEVICE_PROTOCOLS) {
    if (routeMatchKey(route, proto.key, proto.titles)) return true
  }
  return false
}

/** 系统设置等无专用图标时的兜底（递归子节点） */
function ensureFallbackIcon(route, parentIcon = FALLBACK_ICON) {
  const cloned = shallowCloneRoute(route)
  if (!cloned.meta) cloned.meta = {}
  if (!cloned.meta.icon || cloned.meta.icon === '#') {
    cloned.meta.icon = parentIcon || FALLBACK_ICON
  }
  if (Array.isArray(cloned.children) && cloned.children.length) {
    const childFallback = cloned.meta.icon || FALLBACK_ICON
    cloned.children = cloned.children.map(c => ensureFallbackIcon(c, childFallback))
  }
  return cloned
}

/**
 * 收集可点击的第一个菜单 path（用于切换顶栏后跳转）
 */
export function findFirstMenuPath(routes, parentPath = '') {
  if (!Array.isArray(routes)) return null
  for (const route of routes) {
    if (route.hidden) continue
    const raw = route.path || ''
    let full
    if (raw === '/') {
      full = ''
    } else if (String(raw).startsWith('/')) {
      full = normalizePath(raw)
    } else {
      full = normalizePath((parentPath ? parentPath + '/' : '/') + raw)
    }

    if (route.children && route.children.length) {
      const childPath = findFirstMenuPath(route.children, full === '/' ? '' : full)
      if (childPath) return childPath
    }

    if (route.meta && route.meta.title && full && full !== '/device-manage') {
      return full
    }
  }
  return null
}

/**
 * 按当前路由 path 解析所属顶栏分组
 */
export function resolveGroupByPath(routePath) {
  const path = normalizePath(routePath)
  if (!path || path === '/') return 'workbench'

  const lower = path.toLowerCase()
  for (const key of WORKBENCH_PATHS) {
    const k = key.toLowerCase()
    if (lower === k || lower.startsWith(k + '/')) return 'workbench'
  }
  for (const key of VIDEO_ALL_KEYS) {
    const k = key.toLowerCase()
    if (lower === k || lower.startsWith(k + '/')) return 'video'
  }
  // 隐藏详情页（通道列表等）不在协议 path 下，按前缀归入视频汇聚
  const VIDEO_DETAIL_PREFIXES = [
    '/channel',
    '/recordplan',
    '/platform/choosechannel',
    '/yingshi/yschannel',
    '/lecheng/lechannel'
  ]
  for (const prefix of VIDEO_DETAIL_PREFIXES) {
    if (lower === prefix || lower.startsWith(prefix + '/')) return 'video'
  }
  if (lower.startsWith('/user')) return 'system'
  return 'system'
}

/**
 * 优先用 meta.activeMenu（隐藏页高亮父菜单）解析顶栏分组
 */
export function resolveGroupByRoute(route) {
  if (!route) return 'workbench'
  const activeMenu = route.meta?.activeMenu
  if (activeMenu) {
    return resolveGroupByPath(activeMenu)
  }
  return resolveGroupByPath(route.path)
}

/**
 * 根据分组生成左侧侧栏路由
 */
export function buildSidebarByGroup(allRoutes, groupKey) {
  const routes = Array.isArray(allRoutes) ? allRoutes : []
  const visible = routes.filter(r => !r.hidden)

  if (groupKey === 'workbench') {
    const list = []
    for (const spec of WORKBENCH_KEYS) {
      const found = findRouteBySpec(visible, spec)
      if (found) list.push(shallowCloneRoute(found, null, spec.icon))
    }
    return list
  }

  if (groupKey === 'video') {
    const list = []
    for (const spec of VIDEO_MAP_KEYS) {
      const found = findRouteBySpec(visible, spec)
      if (found) list.push(shallowCloneRoute(found, null, spec.icon))
    }

    const protocolChildren = []
    for (const proto of DEVICE_PROTOCOLS) {
      const found = findRouteBySpec(visible, proto)
      if (!found) continue
      protocolChildren.push(reshapeProtocolNode(found, proto))
    }

    if (protocolChildren.length) {
      list.push({
        path: '/device-manage',
        name: 'VideoDeviceManage',
        alwaysShow: true,
        redirect: 'noRedirect',
        meta: { title: '设备管理', icon: DEVICE_MANAGE_ICON },
        children: protocolChildren
      })
    }
    return list
  }

  // 系统设置：排除已归入工作台 / 视频汇聚的菜单；缺图标用 settings
  return visible
    .filter(r => !isReservedRoute(r))
    .map(r => ensureFallbackIcon(r))
}
