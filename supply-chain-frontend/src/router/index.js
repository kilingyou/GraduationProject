import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

NProgress.configure({ showSpinner: false })

const Layout = () => import('@/layout/index.vue')

export const constantRoutes = [
  {
    path: '/login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/register',
    component: () => import('@/views/login/register.vue'),
    meta: { title: '注册' }
  },
  {
    path: '/trace',
    component: () => import('@/views/enduser/trace/index.vue'),
    meta: { title: '产品溯源查询' }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'HomeFilled' }
      }
    ]
  },
  {
    path: '/system',
    component: Layout,
    meta: { title: '系统管理', icon: 'Setting', roles: ['admin', 'regulator'] },
    children: [
      { path: 'user', name: 'SystemUser', component: () => import('@/views/system/user/index.vue'), meta: { title: '用户管理', icon: 'User' } },
      { path: 'role', name: 'SystemRole', component: () => import('@/views/system/role/index.vue'), meta: { title: '角色管理', icon: 'UserFilled' } },
      { path: 'menu', name: 'SystemMenu', component: () => import('@/views/system/menu/index.vue'), meta: { title: '菜单管理', icon: 'Menu' } },
      { path: 'log', name: 'SystemLog', component: () => import('@/views/system/log/index.vue'), meta: { title: '操作日志', icon: 'Document' } }
    ]
  },
  {
    path: '/supplier',
    component: Layout,
    meta: { title: '供应商管理', icon: 'Shop', roles: ['admin', 'supplier'] },
    children: [
      { path: 'design', name: 'DesignDoc', component: () => import('@/views/supplier/design/index.vue'), meta: { title: '设计文档', icon: 'Document' } },
      { path: 'bom', name: 'BomMgmt', component: () => import('@/views/supplier/bom/index.vue'), meta: { title: 'BOM管理', icon: 'List' } },
      { path: 'order', name: 'ProdOrder', component: () => import('@/views/supplier/order/index.vue'), meta: { title: '生产订单', icon: 'Tickets' } },
      { path: 'track', name: 'OrderTrack', component: () => import('@/views/supplier/track/index.vue'), meta: { title: '进度跟踪', icon: 'DataLine' } },
      { path: 'reject', name: 'SupplierReject', component: () => import('@/views/supplier/reject/index.vue'), meta: { title: '不合格处置', icon: 'Warning' } }
    ]
  },
  {
    path: '/manufacturer',
    component: Layout,
    meta: { title: '制造商管理', icon: 'OfficeBuilding', roles: ['admin', 'manufacturer'] },
    children: [
      { path: 'order', name: 'MfgOrder', component: () => import('@/views/manufacturer/order/index.vue'), meta: { title: '订单接收', icon: 'Tickets' } },
      { path: 'production', name: 'Production', component: () => import('@/views/manufacturer/production/index.vue'), meta: { title: '生产管理', icon: 'Cpu' } },
      { path: 'quality', name: 'MfgQuality', component: () => import('@/views/manufacturer/quality/index.vue'), meta: { title: '质检管理', icon: 'Checked' } },
      { path: 'dashboard', name: 'MfgDashboard', component: () => import('@/views/manufacturer/dashboard/index.vue'), meta: { title: '数据看板', icon: 'DataAnalysis' } }
    ]
  },
  {
    path: '/assembler',
    component: Layout,
    meta: { title: '组装商管理', icon: 'SetUp', roles: ['admin', 'assembler'] },
    children: [
      { path: 'intake', name: 'CompIntake', component: () => import('@/views/assembler/intake/index.vue'), meta: { title: '部件入库', icon: 'Box' } },
      { path: 'assembly', name: 'Assembly', component: () => import('@/views/assembler/assembly/index.vue'), meta: { title: '组装管理', icon: 'Connection' } },
      { path: 'quality', name: 'AsmQuality', component: () => import('@/views/assembler/quality/index.vue'), meta: { title: '整机质检', icon: 'Checked' } },
      {
        path: 'circulation',
        name: 'AsmCirculation',
        component: () => import('@/views/assembler/circulation/ParentView.vue'),
        redirect: { name: 'AsmCircLogistics' },
        meta: { title: '渠道流通', icon: 'Van' },
        children: [
          {
            path: 'logistics',
            name: 'AsmCircLogistics',
            component: () => import('@/views/distributor/logistics/index.vue'),
            meta: { title: '物流流转', icon: 'Ship' }
          },
          {
            path: 'inventory',
            name: 'AsmCircInventory',
            component: () => import('@/views/distributor/inventory/index.vue'),
            meta: { title: '库存管理', icon: 'GoodsFilled' }
          },
          {
            path: 'sales',
            name: 'AsmCircSales',
            component: () => import('@/views/distributor/sales/index.vue'),
            meta: { title: '销售记录', icon: 'Sell' }
          }
        ]
      },
      { path: 'dashboard', name: 'AsmDashboard', component: () => import('@/views/assembler/dashboard/index.vue'), meta: { title: '数据看板', icon: 'DataAnalysis' } }
    ]
  },
  {
    path: '/distributor',
    component: Layout,
    meta: { title: '分销管理', icon: 'Van', roles: ['admin', 'distributor'] },
    children: [
      { path: 'logistics', name: 'Logistics', component: () => import('@/views/distributor/logistics/index.vue'), meta: { title: '物流流转', icon: 'Ship' } },
      { path: 'inventory', name: 'Inventory', component: () => import('@/views/distributor/inventory/index.vue'), meta: { title: '库存管理', icon: 'GoodsFilled' } },
      { path: 'sales', name: 'Sales', component: () => import('@/views/distributor/sales/index.vue'), meta: { title: '销售记录', icon: 'Sell' } }
    ]
  },
  {
    path: '/enduser',
    component: Layout,
    meta: { title: '终端用户', icon: 'UserFilled', roles: ['admin', 'enduser'] },
    children: [
      { path: 'trace', name: 'EnduserTrace', component: () => import('@/views/enduser/trace/index.vue'), meta: { title: '溯源查询', icon: 'Search' } },
      { path: 'bind', name: 'ProductBind', component: () => import('@/views/enduser/bind/index.vue'), meta: { title: '产品绑定', icon: 'Link' } },
      { path: 'complaint', name: 'Complaint', component: () => import('@/views/enduser/complaint/index.vue'), meta: { title: '投诉反馈', icon: 'ChatDotRound' } },
      { path: 'decommission', name: 'Decommission', component: () => import('@/views/enduser/decommission/index.vue'), meta: { title: '报废登记', icon: 'Delete' } }
    ]
  },
  {
    path: '/regulator',
    component: Layout,
    meta: { title: '监管控制台', icon: 'Monitor', roles: ['admin', 'regulator'] },
    children: [
      { path: 'audit', name: 'SupplierAudit', component: () => import('@/views/regulator/audit/index.vue'), meta: { title: '资质审核', icon: 'Stamp' } },
      { path: 'inspection', name: 'Inspection', component: () => import('@/views/regulator/inspection/index.vue'), meta: { title: '抽检任务', icon: 'FirstAidKit' } },
      { path: 'recall', name: 'Recall', component: () => import('@/views/regulator/recall/index.vue'), meta: { title: '召回管理', icon: 'WarningFilled' } },
      { path: 'anomaly', name: 'RegAnomaly', component: () => import('@/views/regulator/anomaly/index.vue'), meta: { title: '串货监控', icon: 'Histogram' } },
      { path: 'log', name: 'AuditLog', component: () => import('@/views/regulator/log/index.vue'), meta: { title: '审计日志', icon: 'Notebook' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes
})

const whiteList = ['/login', '/register', '/trace']

router.beforeEach(async (to, from, next) => {
  NProgress.start()
  const userStore = useUserStore()

  if (whiteList.some(path => to.path.startsWith(path))) {
    if ((to.path === '/login' || to.path === '/register') && userStore.token) {
      const r = to.query.redirect
      next(typeof r === 'string' && r ? r : '/dashboard')
      return
    }
    next()
    return
  }

  if (!userStore.token) {
    next(`/login?redirect=${to.path}`)
    return
  }

  if (!userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch (err) {
      userStore.logout()
      next(`/login?redirect=${to.path}`)
      return
    }
  }

  next()
})

router.afterEach(() => {
  NProgress.done()
})

export default router
