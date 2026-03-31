<template>
  <div class="dashboard-container">
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="6" v-for="card in statCards" :key="card.title">
        <el-card shadow="hover" class="stat-card" :body-style="{ padding: '20px' }">
          <div class="stat-card-inner">
            <div class="stat-info">
              <div class="stat-label">{{ card.title }}</div>
              <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
            </div>
            <el-icon :size="40" :style="{ color: card.color, opacity: 0.25 }">
              <component :is="card.icon" />
            </el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="search-card">
      <template #header>
        <span>ECID 快速查询（本企业）</span>
      </template>
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item label="ECID">
          <el-input
            v-model="searchEcid"
            placeholder="输入 ECID"
            clearable
            style="width: 360px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch" :loading="searching">查询</el-button>
        </el-form-item>
      </el-form>

      <el-descriptions v-if="deviceRecord" :column="2" border class="search-result">
        <el-descriptions-item label="ECID">{{ deviceRecord.ecid }}</el-descriptions-item>
        <el-descriptions-item label="设备类型">{{ deviceRecord.deviceType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ deviceRecord.batchId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="订单ID">{{ deviceRecord.orderId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="deviceStatusType(deviceRecord.status)" effect="plain">
            {{ deviceRecord.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="设备上链">
          <el-tag :type="deviceRecord.chainRegistered === 1 ? 'success' : 'info'" effect="plain">
            {{ deviceRecord.chainRegistered === 1 ? '已上链' : '未上链' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-empty v-if="searchPerformed && !deviceRecord" description="未找到设备记录" />
    </el-card>

    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header><span>近 7 日新增设备登记</span></template>
          <VChart v-if="stats" class="chart" :option="lineOption" autoresize />
          <el-empty v-else description="加载中…" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header><span>设备状态分布</span></template>
          <VChart v-if="stats" class="chart" :option="pieOption" autoresize />
          <el-empty v-else description="加载中…" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { Tickets, Box, TrophyBase, Cpu } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getManufacturerDashboardStats, lookupManufacturerDevice } from '@/api/manufacturer'

use([CanvasRenderer, LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent])

const stats = ref(null)
const statCards = reactive([
  { title: '订单完工率', value: '-', color: '#409eff', icon: 'Tickets' },
  { title: '批次完工', value: '-', color: '#67c23a', icon: 'Box' },
  { title: '质检合格率', value: '-', color: '#e6a23c', icon: 'TrophyBase' },
  { title: '设备总数', value: '-', color: '#f56c6c', icon: 'Cpu' }
])

const searchEcid = ref('')
const searching = ref(false)
const searchPerformed = ref(false)
const deviceRecord = ref(null)

const DEVICE_STATUS_MAP = {
  PRODUCED: 'info',
  GENERATED: 'info',
  REGISTERED: 'success',
  QC_PASS: 'success',
  REJECTED: 'danger'
}
const deviceStatusType = (s) => DEVICE_STATUS_MAP[s] ?? 'info'

const last7Labels = () => {
  const out = []
  for (let i = 6; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    out.push(`${d.getMonth() + 1}/${d.getDate()}`)
  }
  return out
}

const lineOption = computed(() => {
  const s = stats.value
  if (!s?.last7DaysNewDevices) return {}
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: last7Labels() },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ name: '新建设备', type: 'line', smooth: true, data: s.last7DaysNewDevices }]
  }
})

const pieOption = computed(() => {
  const p = stats.value?.qualityPie
  if (!p) return {}
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: ['40%', '65%'],
        data: [
          { name: '质检合格', value: p.qcPass || 0 },
          { name: '不合格作废', value: p.rejected || 0 },
          { name: '其他状态', value: p.other || 0 }
        ]
      }
    ]
  }
})

async function loadStats() {
  try {
    const res = await getManufacturerDashboardStats()
    stats.value = res.data || {}
    const s = stats.value
    const doneRate = s.orderCompletionRatePercent ?? 0
    const doneOrd = s.ordersCompleted ?? 0
    const relOrd = s.relatedOrders ?? 0
    statCards[0].value = `${doneRate}%（${doneOrd}/${relOrd}）`
    const bDone = s.batchesCompleted ?? 0
    const bAll = s.productionBatches ?? 0
    statCards[1].value = `${bDone}/${bAll}`
    statCards[2].value = `${s.passRatePercent ?? 0}%`
    statCards[3].value = s.deviceTotal ?? 0
  } catch {
    /* interceptor */
  }
}

async function handleSearch() {
  if (!searchEcid.value.trim()) {
    ElMessage.warning('请输入 ECID')
    return
  }
  searching.value = true
  searchPerformed.value = false
  deviceRecord.value = null
  try {
    const res = await lookupManufacturerDevice(searchEcid.value.trim())
    deviceRecord.value = res.data || null
    searchPerformed.value = true
  } catch {
    searchPerformed.value = true
  } finally {
    searching.value = false
  }
}

onMounted(loadStats)
</script>

<style scoped lang="scss">
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .stat-row {
    .stat-card {
      margin-bottom: 0;
      .stat-card-inner {
        display: flex;
        justify-content: space-between;
        align-items: center;
        .stat-info {
          .stat-label {
            font-size: 14px;
            color: #909399;
            margin-bottom: 8px;
          }
          .stat-value {
            font-size: 28px;
            font-weight: 700;
            line-height: 1;
          }
        }
      }
    }
  }

  .search-card .search-result {
    margin-top: 16px;
  }

  .chart {
    height: 300px;
    width: 100%;
  }
}
</style>
