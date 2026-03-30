<template>
  <div class="app-container">
    <!-- Stat Cards -->
    <el-row :gutter="20" class="mb-20">
      <el-col :xs="12" :sm="6" v-for="item in statCards" :key="item.title">
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

    <!-- Chart Placeholder -->
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="card-header-text">本周组装趋势</span>
          </template>
          <div class="chart-placeholder">
            <el-empty description="图表区域（接入 ECharts）" />
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="card-header-text">质检通过率分布</span>
          </template>
          <div class="chart-placeholder">
            <el-empty description="图表区域（接入 ECharts）" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Box, Odometer, CircleCheck, Link } from '@element-plus/icons-vue'
import { getAssemblyBatchList, getAssemblyRecordList } from '@/api/assembler'

const statCards = ref([
  { title: '组装批次', value: '-', icon: Box, bgColor: '#409eff' },
  { title: '今日产量', value: '-', icon: Odometer, bgColor: '#67c23a' },
  { title: '质检通过率', value: '-', icon: CircleCheck, bgColor: '#e6a23c' },
  { title: '上链记录', value: '-', icon: Link, bgColor: '#f56c6c' }
])

async function loadStats() {
  try {
    const [batchRes, recordRes] = await Promise.all([
      getAssemblyBatchList({ page: 1, size: 1 }),
      getAssemblyRecordList({ page: 1, size: 1 })
    ])
    statCards.value[0].value = batchRes.data?.total ?? 0
    statCards.value[3].value = recordRes.data?.total ?? 0
  } catch {
    // dashboard stats are non-critical
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
.chart-placeholder {
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
