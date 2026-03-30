<template>
  <div class="dashboard-container">
    <!-- Stat cards -->
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

    <!-- Quick search -->
    <el-card shadow="never" class="search-card">
      <template #header>
        <span>快速查询</span>
      </template>
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item label="ECID查询">
          <el-input
            v-model="searchEcid"
            placeholder="输入ECID快速查询设备记录"
            clearable
            style="width: 360px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch" :loading="searching">查询</el-button>
        </el-form-item>
      </el-form>

      <el-descriptions
        v-if="deviceRecord"
        :column="2"
        border
        class="search-result"
      >
        <el-descriptions-item label="ECID">{{ deviceRecord.ecid }}</el-descriptions-item>
        <el-descriptions-item label="设备类型">{{ deviceRecord.deviceType }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ deviceRecord.batchId }}</el-descriptions-item>
        <el-descriptions-item label="订单ID">{{ deviceRecord.orderId }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="deviceStatusType(deviceRecord.status)" effect="plain">
            {{ deviceRecord.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="是否上链">
          <el-tag :type="deviceRecord.onChain ? 'success' : 'info'" effect="plain">
            {{ deviceRecord.onChain ? '已上链' : '未上链' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-empty v-if="searchPerformed && !deviceRecord" description="未找到设备记录" />
    </el-card>

    <!-- Chart placeholders -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header><span>生产趋势</span></template>
          <div id="chart-production-trend" class="chart-placeholder">
            <el-empty description="ECharts 图表占位 - 生产趋势" :image-size="80" />
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header><span>良品率统计</span></template>
          <div id="chart-quality-rate" class="chart-placeholder">
            <el-empty description="ECharts 图表占位 - 良品率统计" :image-size="80" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { Tickets, Box, TrophyBase, Cpu } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getEcidList } from '@/api/manufacturer'

const statCards = reactive([
  { title: '订单总数', value: 128, color: '#409eff', icon: 'Tickets' },
  { title: '生产批次', value: 56, color: '#67c23a', icon: 'Box' },
  { title: '良品率', value: '97.3%', color: '#e6a23c', icon: 'TrophyBase' },
  { title: '设备总数', value: 3842, color: '#f56c6c', icon: 'Cpu' }
])

// ================== Quick search ==================
const searchEcid = ref('')
const searching = ref(false)
const searchPerformed = ref(false)
const deviceRecord = ref(null)

const DEVICE_STATUS_MAP = {
  GENERATED: 'info',
  REGISTERED: 'success',
  QC_PASSED: 'success',
  QC_FAILED: 'danger',
  SHIPPED: ''
}
const deviceStatusType = (s) => DEVICE_STATUS_MAP[s] ?? 'info'

async function handleSearch() {
  if (!searchEcid.value.trim()) {
    ElMessage.warning('请输入ECID')
    return
  }
  searching.value = true
  searchPerformed.value = false
  deviceRecord.value = null
  try {
    const { data } = await getEcidList({ ecid: searchEcid.value.trim(), page: 1, pageSize: 1 })
    const list = data.records ?? data.list ?? []
    deviceRecord.value = list.length ? list[0] : null
    searchPerformed.value = true
  } catch { /* handled by interceptor */ } finally {
    searching.value = false
  }
}
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

  .search-card {
    .search-result {
      margin-top: 16px;
    }
  }

  .chart-placeholder {
    height: 300px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
}
</style>
