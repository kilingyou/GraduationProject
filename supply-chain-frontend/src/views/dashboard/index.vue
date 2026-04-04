<template>
  <div class="dashboard-container">
    <div class="welcome-section">
      <div class="welcome-text">
        <h2>欢迎回来，{{ userStore.userInfo?.contactPerson || userStore.userInfo?.username }}</h2>
        <p>
          <el-tag type="primary" effect="plain" size="small">{{ roleLabel }}</el-tag>
          <span class="welcome-hint">今天是 {{ today }}，祝您工作顺利！</span>
        </p>
      </div>
    </div>

    <el-row :gutter="20" class="stat-row">
      <el-col :xs="12" :sm="6" v-for="item in statCards" :key="item.title">
        <el-card shadow="hover" class="stat-card" :body-style="{ padding: '24px' }">
          <div class="stat-content">
            <div class="stat-info">
              <span class="stat-label">{{ item.title }}</span>
              <span class="stat-value" :style="{ color: item.color }">{{ item.value }}</span>
            </div>
            <div class="stat-icon" :style="{ background: item.bgColor }">
              <el-icon :size="28" :color="item.color"><component :is="item.icon" /></el-icon>
            </div>
          </div>
          <div class="stat-footer">
            <span class="stat-desc">{{ item.desc }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="24">
        <el-card shadow="hover" class="action-card">
          <template #header>
            <span class="card-title">快捷操作</span>
          </template>
          <div class="quick-actions">
            <el-button
              v-for="action in quickActions"
              :key="action.label"
              :type="action.type || 'default'"
              :icon="action.icon"
              @click="$router.push(action.path)"
            >
              {{ action.label }}
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import { getDashboardStats } from '@/api/auth'

const userStore = useUserStore()

const roleLabels = {
  supplier: '供应商',
  manufacturer: '制造商',
  assembler: '组装商',
  distributor: '分销商',
  regulator: '监管机构',
  enduser: '终端用户',
  admin: '系统管理员'
}

const roleLabel = computed(() => roleLabels[userStore.userInfo?.roleKey] || userStore.userInfo?.roleKey || '')

const today = computed(() => {
  const d = new Date()
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
})

const icons = ['Tickets', 'GoodsFilled', 'Checked', 'UserFilled']
const colors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c']
const bgColors = ['#ecf5ff', '#f0f9eb', '#fdf6ec', '#fef0f0']

const statCards = ref([
  { title: '-', value: '-', icon: icons[0], color: colors[0], bgColor: bgColors[0], desc: '加载中...' },
  { title: '-', value: '-', icon: icons[1], color: colors[1], bgColor: bgColors[1], desc: '加载中...' },
  { title: '-', value: '-', icon: icons[2], color: colors[2], bgColor: bgColors[2], desc: '加载中...' },
  { title: '-', value: '-', icon: icons[3], color: colors[3], bgColor: bgColors[3], desc: '加载中...' }
])

onMounted(async () => {
  try {
    const res = await getDashboardStats()
    const d = res.data
    if (d) {
      const keys = ['card1', 'card2', 'card3', 'card4']
      keys.forEach((k, i) => {
        if (d[k]) {
          statCards.value[i] = {
            title: d[k].title || '-',
            value: d[k].value ?? '-',
            icon: icons[i],
            color: colors[i],
            bgColor: bgColors[i],
            desc: d[k].desc || ''
          }
        }
      })
    }
  } catch (_) { /* keep placeholder values */ }
})

const roleActions = {
  supplier: [
    { label: '设计文档', path: '/supplier/design', icon: 'Document', type: 'primary' },
    { label: 'BOM管理', path: '/supplier/bom', icon: 'List' },
    { label: '生产订单', path: '/supplier/order', icon: 'Tickets' },
    { label: '进度跟踪', path: '/supplier/track', icon: 'DataLine' },
    { label: '不合格处置', path: '/supplier/reject', icon: 'Warning' }
  ],
  manufacturer: [
    { label: '订单接收', path: '/manufacturer/order', icon: 'Tickets', type: 'primary' },
    { label: '生产管理', path: '/manufacturer/production', icon: 'Cpu' },
    { label: '质检管理', path: '/manufacturer/quality', icon: 'Checked' },
    { label: '数据看板', path: '/manufacturer/dashboard', icon: 'DataAnalysis' }
  ],
  assembler: [
    { label: '部件入库', path: '/assembler/intake', icon: 'Box', type: 'primary' },
    { label: '组装管理', path: '/assembler/assembly', icon: 'Connection' },
    { label: '整机质检', path: '/assembler/quality', icon: 'Checked' },
    { label: '物流流转', path: '/distributor/logistics', icon: 'Ship' },
    { label: '渠道库存', path: '/distributor/inventory', icon: 'GoodsFilled' },
    { label: '销售登记', path: '/distributor/sales', icon: 'Sell' },
    { label: '数据看板', path: '/assembler/dashboard', icon: 'DataAnalysis' }
  ],
  distributor: [
    { label: '物流流转', path: '/distributor/logistics', icon: 'Ship', type: 'primary' },
    { label: '库存管理', path: '/distributor/inventory', icon: 'GoodsFilled' },
    { label: '销售记录', path: '/distributor/sales', icon: 'Sell' }
  ],
  enduser: [
    { label: '产品溯源', path: '/trace', icon: 'Search', type: 'primary' },
    { label: '投诉反馈', path: '/enduser/complaint', icon: 'ChatDotRound' },
    { label: '报废登记', path: '/enduser/decommission', icon: 'Delete' }
  ],
  regulator: [
    { label: '资质审核', path: '/regulator/audit', icon: 'Stamp', type: 'primary' },
    { label: '抽检任务', path: '/regulator/inspection', icon: 'FirstAidKit' },
    { label: '召回管理', path: '/regulator/recall', icon: 'WarningFilled' },
    { label: '审计日志', path: '/regulator/log', icon: 'Notebook' }
  ]
}

const defaultActions = [
  { label: '用户管理', path: '/system/user', icon: 'User', type: 'primary' },
  { label: '角色管理', path: '/system/role', icon: 'UserFilled' },
  { label: '菜单管理', path: '/system/menu', icon: 'Menu' },
  { label: '操作日志', path: '/system/log', icon: 'Document' }
]

const quickActions = computed(() => {
  const key = userStore.userInfo?.roleKey
  return roleActions[key] || defaultActions
})
</script>

<style scoped lang="scss">
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.welcome-section {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  padding: 28px 32px;
  color: #fff;

  h2 {
    margin: 0 0 8px;
    font-size: 22px;
    font-weight: 600;
  }

  p {
    margin: 0;
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .welcome-hint {
    font-size: 14px;
    opacity: 0.85;
  }

  :deep(.el-tag) {
    color: #fff;
    border-color: rgba(255, 255, 255, 0.5);
    background: rgba(255, 255, 255, 0.15);
  }
}

.stat-row {
  .stat-card {
    border-radius: 12px;
    border: none;

    .stat-content {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .stat-info {
      display: flex;
      flex-direction: column;
    }

    .stat-label {
      font-size: 14px;
      color: #909399;
      margin-bottom: 8px;
    }

    .stat-value {
      font-size: 30px;
      font-weight: 700;
      line-height: 1;
    }

    .stat-icon {
      width: 56px;
      height: 56px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .stat-footer {
      margin-top: 16px;
      padding-top: 12px;
      border-top: 1px solid #f0f0f0;

      .stat-desc {
        font-size: 13px;
        color: #909399;
      }
    }
  }
}

.action-card {
  border-radius: 12px;
  border: none;

  .card-title {
    font-size: 16px;
    font-weight: 600;
    color: #303133;
  }

  .quick-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
  }
}
</style>
