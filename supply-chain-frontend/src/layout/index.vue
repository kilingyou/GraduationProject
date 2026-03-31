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
            <template v-for="top in userStore.menus" :key="top.id || top.path">
              <el-sub-menu
                v-if="top.children && top.children.length > 0"
                :index="top.path || String(top.id)"
              >
                <template #title>
                  <el-icon><component :is="top.icon || 'FolderOpened'" /></el-icon>
                  <span>{{ top.menuName }}</span>
                </template>
                <el-menu-item
                  v-for="child in top.children"
                  :key="child.id || child.path"
                  :index="child.path"
                >
                  <el-icon><component :is="child.icon || 'Document'" /></el-icon>
                  <span>{{ child.menuName }}</span>
                </el-menu-item>
              </el-sub-menu>
              <el-menu-item v-else-if="top.path" :index="top.path">
                <el-icon><component :is="top.icon || 'Document'" /></el-icon>
                <span>{{ top.menuName }}</span>
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
                <el-menu-item
                  v-for="child in route.children"
                  :key="child.path"
                  :index="joinPath(route.path, child.path)"
                >
                  <el-icon><component :is="child.meta?.icon" /></el-icon>
                  <span>{{ child.meta?.title }}</span>
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
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="layout-main">
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
  supplier: '供应商',
  manufacturer: '制造商',
  assembler: '组装商',
  distributor: '分销商',
  regulator: '监管机构',
  enduser: '终端用户'
}

const roleKey = computed(() => userStore.userInfo?.roleKey || '')
const roleLabel = computed(() => roleLabels[roleKey.value] || roleKey.value)

const useApiMenus = computed(() => Array.isArray(userStore.menus) && userStore.menus.length > 0)

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
  return constantRoutes.filter(r => r.path !== '/login' && r.path !== '/register' && r.path !== '/trace' && r.children)
})

const activeMenu = computed(() => route.path)

const breadcrumbs = computed(() => route.matched.filter(item => item.meta?.title))

function handleLogout() {
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
