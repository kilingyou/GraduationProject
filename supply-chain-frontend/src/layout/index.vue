<template>
  <el-container class="layout-container">
    <el-aside :width="isCollapse ? '64px' : '220px'" class="layout-aside">
      <div class="logo-container">
        <img src="@/assets/logo.svg" alt="logo" class="logo-img" v-if="!isCollapse" />
        <span v-if="!isCollapse" class="logo-text">供应链管理系统</span>
        <el-icon v-else :size="24" color="#fff"><Connection /></el-icon>
      </div>
      <el-scrollbar>
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapse"
          :unique-opened="true"
          background-color="#001529"
          text-color="#ffffffb3"
          active-text-color="#409eff"
          router
        >
          <template v-if="useApiMenus">
            <el-menu-item index="/dashboard">
              <el-icon><HomeFilled /></el-icon>
              <span>首页</span>
            </el-menu-item>
            <template v-for="top in visibleApiMenus" :key="top.id || top.path">
              <el-sub-menu
                v-if="top.children && top.children.length > 0"
                :index="top.path || String(top.id)"
              >
                <template #title>
                  <el-icon><component :is="top.icon || 'FolderOpened'" /></el-icon>
                  <span>{{ menuDisplayName(top) }}</span>
                </template>
                <template v-for="child in top.children" :key="child.id || child.path">
                  <el-sub-menu
                    v-if="child.children && child.children.length > 0"
                    :index="child.path || String(child.id)"
                  >
                    <template #title>
                      <el-icon><component :is="child.icon || 'FolderOpened'" /></el-icon>
                      <span>{{ menuDisplayName(child) }}</span>
                    </template>
                    <el-menu-item
                      v-for="sub in child.children"
                      :key="sub.id || sub.path"
                      :index="sub.path"
                    >
                      <el-icon><component :is="sub.icon || 'Document'" /></el-icon>
                      <span>{{ menuDisplayName(sub) }}</span>
                    </el-menu-item>
                  </el-sub-menu>
                  <el-menu-item v-else :index="child.path">
                    <el-icon><component :is="child.icon || 'Document'" /></el-icon>
                    <span>{{ menuDisplayName(child) }}</span>
                  </el-menu-item>
                </template>
              </el-sub-menu>
              <el-menu-item v-else-if="top.path" :index="top.path">
                <el-icon><component :is="top.icon || 'Document'" /></el-icon>
                <span>{{ menuDisplayName(top) }}</span>
              </el-menu-item>
            </template>
          </template>
          <template v-else>
            <template v-for="route in menuRoutes" :key="route.path">
              <el-sub-menu v-if="route.children && route.children.length > 1" :index="route.path">
                <template #title>
                  <el-icon><component :is="route.meta?.icon" /></el-icon>
                  <span>{{ route.meta?.title }}</span>
                </template>
                <template v-for="child in route.children" :key="child.path || child.name">
                  <el-sub-menu
                    v-if="child.children && child.children.length > 0"
                    :index="joinPath(route.path, child.path)"
                  >
                    <template #title>
                      <el-icon><component :is="child.meta?.icon" /></el-icon>
                      <span>{{ child.meta?.title }}</span>
                    </template>
                    <el-menu-item
                      v-for="sub in child.children"
                      :key="sub.path"
                      :index="joinPath(joinPath(route.path, child.path), sub.path)"
                    >
                      <el-icon><component :is="sub.meta?.icon" /></el-icon>
                      <span>{{ sub.meta?.title }}</span>
                    </el-menu-item>
                  </el-sub-menu>
                  <el-menu-item
                    v-else
                    :index="joinPath(route.path, child.path)"
                  >
                    <el-icon><component :is="child.meta?.icon" /></el-icon>
                    <span>{{ child.meta?.title }}</span>
                  </el-menu-item>
                </template>
              </el-sub-menu>

              <el-sub-menu
                v-else-if="route.children && route.children.length === 1 && route.children[0].children && route.children[0].children.length > 0"
                :index="joinPath(route.path, route.children[0].path)"
              >
                <template #title>
                  <el-icon><component :is="route.children[0].meta?.icon || route.meta?.icon" /></el-icon>
                  <span>{{ route.children[0].meta?.title || route.meta?.title }}</span>
                </template>
                <el-menu-item
                  v-for="sub in route.children[0].children"
                  :key="sub.path"
                  :index="joinPath(joinPath(route.path, route.children[0].path), sub.path)"
                >
                  <el-icon><component :is="sub.meta?.icon" /></el-icon>
                  <span>{{ sub.meta?.title }}</span>
                </el-menu-item>
              </el-sub-menu>
              <el-menu-item
                v-else-if="route.children && route.children.length === 1"
                :index="joinPath(route.path, route.children[0].path)"
              >
                <el-icon><component :is="route.children[0].meta?.icon || route.meta?.icon" /></el-icon>
                <span>{{ route.children[0].meta?.title || route.meta?.title }}</span>
              </el-menu-item>
            </template>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse" :size="20">
            <component :is="isCollapse ? 'Expand' : 'Fold'" />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
              {{ item.meta?.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <span class="role-tag">
            <el-tag size="small" type="info">{{ roleLabel }}</el-tag>
          </span>
          <el-dropdown trigger="click">
            <span class="user-info">
              <el-avatar :size="32" :src="userStore.userInfo?.avatar">
                {{ userStore.userInfo?.username?.charAt(0)?.toUpperCase() }}
              </el-avatar>
              <span class="username">{{ userStore.userInfo?.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="$router.push('/dashboard')">首页</el-dropdown-item>
                <el-dropdown-item @click="handleSwitchAccount">切换账户</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="layout-main">
        <el-alert
          v-if="showSupplierPendingAlert"
          title="供应商资质审核中：当前仅可查看信息，发布生产订单等操作将在审核通过后开放。"
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
        />
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { constantRoutes } from '@/router'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isCollapse = ref(false)

const roleLabels = {
  admin: '系统管理员',
  supplier: '供应商',
  manufacturer: '制造商',
  assembler: '组装商',
  distributor: '分销商',
  regulator: '监管机构',
  enduser: '终端用户'
}

/** 按 path 覆盖标题，避免 sys_menu.menu_name 因 SQL 客户端编码错误入库后出现乱码 */
const API_MENU_TITLE_BY_PATH = {
  '/enduser': '终端用户',
  '/enduser/trace': '溯源查询',
  '/enduser/bind': '产品绑定',
  '/enduser/complaint': '投诉反馈',
  '/enduser/decommission': '报废登记'
}

function menuDisplayName(item) {
  if (!item) return ''
  const p = item.path
  if (p && API_MENU_TITLE_BY_PATH[p]) return API_MENU_TITLE_BY_PATH[p]
  return item.menuName || ''
}

const roleKey = computed(() => userStore.userInfo?.roleKey || '')
const roleLabel = computed(() => roleLabels[roleKey.value] || roleKey.value)
const showSupplierPendingAlert = computed(() =>
  roleKey.value === 'supplier' && userStore.userInfo?.supplierAuditStatus !== 'APPROVED'
)

const useApiMenus = computed(() => Array.isArray(userStore.menus) && userStore.menus.length > 0)

/** 无 component 的 M 型壳目录：子项与首页平级（各业务角色/监管等）；带子菜单的 M（如渠道流通）整体上移后结构不变 */
const FLATTEN_SHELL_MENU_PATHS = [
  '/assembler',
  '/supplier',
  '/manufacturer',
  '/distributor',
  '/enduser',
  '/regulator'
]

function isShellDirectoryMenu(m) {
  if (!m || !FLATTEN_SHELL_MENU_PATHS.includes(m.path)) return false
  return (
    m.menuType === 'M' &&
    (!m.component || String(m.component).trim() === '') &&
    Array.isArray(m.children) &&
    m.children.length > 0
  )
}

function flattenShellDirectoryMenus(menus) {
  if (!Array.isArray(menus)) return menus
  const out = []
  for (const m of menus) {
    if (isShellDirectoryMenu(m)) {
      out.push(...m.children)
    } else {
      out.push(m)
    }
  }
  return out
}

/** 与动态路由一致：不展示 visible=0 的菜单（如已并入其他页的入口） */
function filterMenusByVisible(nodes) {
  if (!Array.isArray(nodes)) return []
  return nodes
    .filter(n => n.visible === 1 || n.visible == null)
    .map(n => ({
      ...n,
      children: n.children?.length ? filterMenusByVisible(n.children) : n.children
    }))
}

const visibleApiMenus = computed(() => {
  const menus = filterMenusByVisible(Array.isArray(userStore.menus) ? userStore.menus : [])
  const base = roleKey.value === 'admin' ? menus : menus.filter(top => top?.path !== '/system')
  return flattenShellDirectoryMenus(base)
})

function joinPath(base, child) {
  const a = (base || '').toString()
  const b = (child || '').toString()
  if (!a) return b
  if (!b) return a
  if (a === '/') return b.startsWith('/') ? b : `/${b}`
  if (a.endsWith('/') && b.startsWith('/')) return a + b.slice(1)
  if (!a.endsWith('/') && !b.startsWith('/')) return `${a}/${b}`
  return a + b
}

const menuRoutes = computed(() => {
  const role = roleKey.value
  const filtered = constantRoutes.filter(r => {
    if (r.path === '/login' || r.path === '/register' || r.path === '/trace') return false
    if (!r.children) return false
    const allowed = r.meta?.roles
    if (!allowed) return true
    return role === 'admin' || allowed.includes(role)
  })
  return filtered.flatMap(r => {
    if (
      (r.path === '/assembler' ||
        r.path === '/supplier' ||
        r.path === '/manufacturer' ||
        r.path === '/distributor' ||
        r.path === '/enduser' ||
        r.path === '/regulator') &&
      r.children?.length
    ) {
      return r.children.map(ch => ({
        path: r.path,
        meta: {},
        children: [ch]
      }))
    }
    return [r]
  })
})

const activeMenu = computed(() => route.path)

const breadcrumbs = computed(() => route.matched.filter(item => item.meta?.title))

function handleLogout() {
  userStore.logout()
  router.push('/login')
}

function handleSwitchAccount() {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped lang="scss">
.layout-container {
  height: 100vh;
}

.layout-aside {
  background-color: #001529;
  transition: width 0.3s;
  overflow: hidden;

  .logo-container {
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    border-bottom: 1px solid #ffffff1a;

    .logo-img {
      width: 32px;
      height: 32px;
    }

    .logo-text {
      color: #fff;
      font-size: 16px;
      font-weight: 600;
      white-space: nowrap;
    }
  }
}

.layout-header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  height: 60px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;

    .collapse-btn {
      cursor: pointer;
      &:hover { color: #409eff; }
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 16px;

    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;

      .username {
        font-size: 14px;
        color: #333;
      }
    }
  }
}

.layout-main {
  background: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}

:deep(.el-menu) {
  border-right: none;
}
</style>
