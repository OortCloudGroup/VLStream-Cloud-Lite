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
 * 视频汇聚 - 设备管理下协议（保留原树，仅覆盖显示名与图标）
 */
const DEVICE_PROTOCOLS = [
  { key: '/vlstream', title: 'VLStream协议', titles: ['VLStream', 'VLStream协议'], icon: 'vlstream' },
  { key: '/gbmanger', title: '国际协议', titles: ['国标协议', '国际协议'], icon: 'gb' },
  { key: '/onvif', title: 'ONVIF协议', titles: ['onvif协议', 'ONVIF协议', 'ONVIF'], icon: 'onvif' },
  { key: '/rtsp', title: 'RTSP协议', titles: ['rtsp协议', 'RTSP协议', 'RTSP'], icon: 'rtsp' },
  { key: '/isup', title: 'ISUP协议', titles: ['海康协议', 'ISUP协议', 'ISUP'], icon: 'isup' },
  { key: '/dahua', title: '大华协议', titles: ['大华协议'], icon: 'dahua' },
  { key: '/custom', title: '自定义协议', titles: ['自定义协议'], icon: 'device' }
]

const DEVICE_MANAGE_ICON = 'device'
/** settings.svg 为内嵌 PNG，无法跟随主题色，改用矢量 system */
const FALLBACK_ICON = 'system'

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

/**
 * 给路由及其可见子节点写入侧栏图标（与 unifiedUser meta.icon 用法一致）
 */
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
  if (lower.startsWith('/user')) return 'system'
  return 'system'
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
      const node = shallowCloneRoute(found, proto.title, proto.icon || DEVICE_MANAGE_ICON)
      if (node.path && node.path !== '/' && !String(node.path).startsWith('/')) {
        node.path = '/' + node.path
      }
      node.alwaysShow = true
      protocolChildren.push(node)
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
