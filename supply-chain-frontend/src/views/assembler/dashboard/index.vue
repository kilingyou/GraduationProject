<template>
  <div class="app-container">
    <el-row :gutter="20" class="mb-20">
      <el-col :xs="12" :sm="8" :md="8" :lg="4" v-for="item in statCards" :key="item.title">
        <el-card shadow="hover" class="stat-card" :body-style="{ padding: '20px' }">
          <div class="stat-card__icon" :style="{ background: item.bgColor }">
            <el-icon :size="28" color="#fff"><component :is="item.icon" /></el-icon>
          </div>
          <div class="stat-card__info">
            <div class="stat-card__value">{{ item.value }}</div>
            <div class="stat-card__title">{{ item.title }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="card-header-text">近 7 日组装产量</span>
          </template>
          <VChart v-if="stats" class="chart" :option="lineOption" autoresize />
          <el-empty v-else description="加载中…" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="card-header-text">整机质检结果分布（已出结果）</span>
          </template>
          <v-chart v-if="stats" class="chart" :option="pieOption" autoresize />
          <el-empty v-else description="加载中…" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover" class="mt-20">
      <template #header>
        <span class="card-header-text">溯源树预览（PDF：输入 SN 查看部件 ECID 挂载）</span>
      </template>
      <el-form :inline="true" @submit.prevent="loadSnTree">
        <el-form-item label="整机 SN">
          <el-input v-model="treeSn" placeholder="输入已组装的 SN" clearable style="width: 280px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="treeLoading" @click="loadSnTree">加载</el-button>
        </el-form-item>
      </el-form>
      <div v-if="treeMeta.assemblyBatchNo" class="meta-line">
        组装批次：{{ treeMeta.assemblyBatchNo }} ｜ 固件版本：{{ treeMeta.firmwareVersion || '-' }}
      </div>
      <VChart v-if="treeOption" class="chart-tree" :option="treeOption" autoresize />
      <el-empty v-else-if="treeTried" description="暂无树数据，请确认 SN 属于本企业" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart, TreeChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { Box, Odometer, CircleCheck, Link, Cpu } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getAssemblerDashboardStats, getAssemblerSnTree } from '@/api/assembler'

use([CanvasRenderer, LineChart, PieChart, TreeChart, GridComponent, TooltipComponent, LegendComponent])

const stats = ref(null)
const statCards = ref([
  { title: '组装批次', value: '-', icon: Box, bgColor: '#409eff' },
  { title: '组装记录', value: '-', icon: Odometer, bgColor: '#67c23a' },
  { title: '已挂载部件 ECID 次数', value: '-', icon: Cpu, bgColor: '#909399' },
  { title: '质检通过率', value: '-', icon: CircleCheck, bgColor: '#e6a23c' },
  { title: '已上链记录', value: '-', icon: Link, bgColor: '#f56c6c' }
])

const treeSn = ref('')
const treeLoading = ref(false)
const treeData = ref(null)
const treeTried = ref(false)
const treeMeta = ref({})

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
  if (!s?.last7DaysAssembled) return {}
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: last7Labels() },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ name: '组装记录数', type: 'line', smooth: true, areaStyle: {}, data: s.last7DaysAssembled }]
  }
})

const pieOption = computed(() => {
  const p = stats.value?.qcPie
  if (!p) return {}
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: '62%',
        data: [
          { name: 'PASS', value: p.pass || 0 },
          { name: 'FAIL', value: p.fail || 0 },
          { name: '未判定', value: p.pending || 0 }
        ]
      }
    ]
  }
})

const treeOption = computed(() => {
  const t = treeData.value
  if (!t) return null
  return {
    tooltip: {
      trigger: 'item',
      formatter(info) {
        const d = info.data
        if (!d) return ''
        const lines = [d.name || d.value || '']
        if (d.manufacturer) lines.push(`制造商: ${d.manufacturer}`)
        if (d.deviceType) lines.push(`器件类型: ${d.deviceType}`)
        return lines.filter(Boolean).join('<br/>')
      }
    },
    series: [
      {
        type: 'tree',
        data: [t],
        top: '5%',
        left: '8%',
        bottom: '5%',
        right: '15%',
        symbolSize: 8,
        label: { position: 'left', fontSize: 11 },
        leaves: { label: { position: 'right' } },
        expandAndCollapse: true,
        animationDuration: 200,
        animationDurationUpdate: 200
      }
    ]
  }
})

async function loadStats() {
  try {
    const res = await getAssemblerDashboardStats()
    stats.value = res.data || {}
    const s = stats.value
    statCards.value[0].value = s.batchCount ?? 0
    statCards.value[1].value = s.recordCount ?? 0
    statCards.value[2].value = s.componentsConsumed ?? 0
    statCards.value[3].value = `${s.qcPassRatePercent ?? 0}%`
    statCards.value[4].value = s.onChainRecords ?? 0
  } catch {
    /* non-critical */
  }
}

async function loadSnTree() {
  if (!treeSn.value.trim()) {
    ElMessage.warning('请输入 SN')
    return
  }
  treeLoading.value = true
  treeTried.value = true
  treeData.value = null
  treeMeta.value = {}
  try {
    const res = await getAssemblerSnTree(treeSn.value.trim())
    const d = res.data || {}
    treeData.value = d.tree || null
    treeMeta.value = {
      assemblyBatchNo: d.assemblyBatchNo,
      firmwareVersion: d.firmwareVersion
    }
  } catch {
    treeData.value = null
  } finally {
    treeLoading.value = false
  }
}

onMounted(loadStats)
</script>

<style scoped lang="scss">
.app-container {
  padding: 20px;
}
.mb-20 {
  margin-bottom: 20px;
}
.mt-20 {
  margin-top: 20px;
}
.stat-card {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  :deep(.el-card__body) {
    display: flex;
    align-items: center;
    gap: 16px;
    width: 100%;
  }
}
.stat-card__icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-card__info {
  flex: 1;
}
.stat-card__value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}
.stat-card__title {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}
.card-header-text {
  font-weight: 600;
}
.chart {
  height: 300px;
  width: 100%;
}
.chart-tree {
  height: 360px;
  width: 100%;
}
.meta-line {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}
</style>
