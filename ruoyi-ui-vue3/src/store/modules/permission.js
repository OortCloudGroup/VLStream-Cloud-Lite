import auth from '@/plugins/auth'
import router, { constantRoutes, dynamicRoutes } from '@/router'
import { getRouters } from '@/api/menu'
import Layout from '@/layout/index'
import ParentView from '@/components/ParentView'
import InnerLink from '@/layout/components/InnerLink'
import { buildSidebarByGroup, resolveGroupByPath, resolveGroupByRoute } from '@/utils/menuGroups'

// 匹配views里面所有的.vue文件
const modules = import.meta.glob('./../../views/**/*.vue')

const usePermissionStore = defineStore(
  'permission',
  {
    state: () => ({
      routes: [],
      addRoutes: [],
      defaultRoutes: [],
      topbarRouters: [],
      sidebarRouters: [],
      /** 全量侧栏源（常量 + 后端），供产品顶栏分组过滤 */
      menuSourceRoutes: [],
      currentGroup: 'workbench'
    }),
    actions: {
      setRoutes(routes) {
        this.addRoutes = routes
        this.routes = constantRoutes.concat(routes)
      },
      setDefaultRoutes(routes) {
        this.defaultRoutes = constantRoutes.concat(routes)
      },
      setTopbarRoutes(routes) {
        this.topbarRouters = routes
      },
      setSidebarRouters(routes) {
        this.sidebarRouters = routes
      },
      setMenuSourceRoutes(routes) {
        this.menuSourceRoutes = routes
      },
      setCurrentGroup(groupKey) {
        this.currentGroup = groupKey
      },
      /**
       * 按顶栏分组刷新左侧菜单（分组未变时不重建，避免侧栏闪烁）
       */
      applyMenuGroup(groupKey) {
        const key = groupKey || 'workbench'
        if (key === this.currentGroup && Array.isArray(this.sidebarRouters) && this.sidebarRouters.length) {
          return
        }
        this.setCurrentGroup(key)
        this.setSidebarRouters(buildSidebarByGroup(this.menuSourceRoutes, key))
      },
      /**
       * 根据当前 path / 路由同步顶栏组与侧栏
       * 传入 route 时优先按 meta.activeMenu 归组（通道列表等隐藏页）
       */
      syncMenuGroupByPath(pathOrRoute) {
        const group = typeof pathOrRoute === 'object' && pathOrRoute !== null
          ? resolveGroupByRoute(pathOrRoute)
          : resolveGroupByPath(pathOrRoute)
        this.applyMenuGroup(group)
        return group
      },
      generateRoutes(roles) {
        return new Promise(resolve => {
          // 向后端请求路由数据
          getRouters().then(res => {
            const sdata = JSON.parse(JSON.stringify(res.data))
            const rdata = JSON.parse(JSON.stringify(res.data))
            const defaultData = JSON.parse(JSON.stringify(res.data))
            const sidebarRoutes = filterAsyncRouter(sdata)
            const rewriteRoutes = filterAsyncRouter(rdata, false, true)
            const defaultRoutes = filterAsyncRouter(defaultData)
            const asyncRoutes = filterDynamicRoutes(dynamicRoutes)
            asyncRoutes.forEach(route => { router.addRoute(route) })
            this.setRoutes(rewriteRoutes)
            const menuSource = constantRoutes.concat(sidebarRoutes)
            this.setMenuSourceRoutes(menuSource)
            this.setDefaultRoutes(sidebarRoutes)
            this.setTopbarRoutes(defaultRoutes)
            // 按当前路径同步顶栏分组（深链进入协议页时落在「视频汇聚」）
            this.syncMenuGroupByPath(router.currentRoute.value || '/index')
            resolve(rewriteRoutes)
          })
        })
      }
    }
  })

// 遍历后台传来的路由字符串，转换为组件对象
function filterAsyncRouter(asyncRouterMap, lastRouter = false, type = false) {
  return asyncRouterMap.filter(route => {
    if (type && route.children) {
      route.children = filterChildren(route.children)
    }
    if (route.component) {
      // Layout ParentView 组件特殊处理
      if (route.component === 'Layout') {
        route.component = Layout
      } else if (route.component === 'ParentView') {
        route.component = ParentView
      } else if (route.component === 'InnerLink') {
        route.component = InnerLink
      } else {
        route.component = loadView(route.component)
      }
    }
    if (route.children != null && route.children && route.children.length) {
      route.children = filterAsyncRouter(route.children, route, type)
    } else {
      delete route['children']
      delete route['redirect']
    }
    return true
  })
}

function filterChildren(childrenMap, lastRouter = false) {
  var children = []
  childrenMap.forEach(el => {
    el.path = lastRouter ? lastRouter.path + '/' + el.path : el.path
    if (el.children && el.children.length && el.component === 'ParentView') {
      children = children.concat(filterChildren(el.children, el))
    } else {
      children.push(el)
    }
  })
  return children
}

// 动态路由遍历，验证是否具备权限
export function filterDynamicRoutes(routes) {
  const res = []
  routes.forEach(route => {
    if (route.permissions) {
      if (auth.hasPermiOr(route.permissions)) {
        res.push(route)
      }
    } else if (route.roles) {
      if (auth.hasRoleOr(route.roles)) {
        res.push(route)
      }
    }
  })
  return res
}

export const loadView = (view) => {
  let res;
  for (const path in modules) {
    const dir = path.split('views/')[1].split('.vue')[0];
    if (dir === view) {
      res = () => modules[path]();
    }
  }
  return res
}

export default usePermissionStore
