import { defineStore } from 'pinia'

const modules = import.meta.glob('../views/**/*.vue')

export const usePermissionStore = defineStore('permission', {
  state: () => ({
    routes: [],
    addedRoutes: false
  }),

  actions: {
    generateRoutes(menus) {
      const asyncRoutes = buildRoutes(menus)
      this.routes = asyncRoutes
      this.addedRoutes = true
      return asyncRoutes
    },
    resetRoutes() {
      this.routes = []
      this.addedRoutes = false
    }
  }
})

function buildRoutes(menus, parentPath = '') {
  if (!menus || menus.length === 0) return []

  return menus.map(menu => {
    const route = {
      path: menu.path,
      name: menu.path?.replace(/\//g, '-').replace(/^-/, ''),
      meta: {
        title: menu.menuName,
        icon: menu.icon,
        perms: menu.perms
      }
    }

    if (menu.component) {
      const componentPath = `../views/${menu.component}.vue`
      route.component = modules[componentPath] || (() => import('@/views/dashboard/index.vue'))
    }

    if (menu.children && menu.children.length > 0) {
      route.children = buildRoutes(menu.children, menu.path)
      route.redirect = menu.children[0]?.path
      if (!route.component) {
        route.component = () => import('@/views/assembler/circulation/ParentView.vue')
      }
    }

    return route
  })
}
