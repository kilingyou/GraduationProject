<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div class="title">串货 / 流通异常监控</div>
          <div class="toolbar">
            <el-input v-model="snFilter" placeholder="按 SN 过滤" clearable style="width: 200px" />
            <el-checkbox v-model="onlyRisk">仅显示有风险</el-checkbox>
            <el-button type="primary" :loading="loading" @click="loadData">扫描</el-button>
          </div>
        </div>
      </template>
      <p class="desc">基于近期销售记录中的 SN，检测无物流、收货方与售方不一致、多用户绑定等异常标记。</p>

      <el-table :data="filteredRows" v-loading="loading" border stripe>
        <el-table-column prop="sn" label="SN" min-width="190" />
        <el-table-column label="风险" width="110" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.riskLevel === 'HIGH' ? 'danger' : row.riskLevel === 'MEDIUM' ? 'warning' : 'success'"
              size="small"
            >
              {{ row.riskLevel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="风险标记" min-width="220">
          <template #default="{ row }">
            <el-tag v-for="f in row.riskFlags || []" :key="f" size="small" class="tag-gap" type="danger">{{ f }}</el-tag>
            <span v-if="!row.riskFlags?.length">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="transferCount" label="物流事件" width="100" align="center" />
        <el-table-column prop="bindCount" label="绑定人数" width="100" align="center" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getRecentAnomalies } from '@/api/regulator'

const loading = ref(false)
const rows = ref([])
const onlyRisk = ref(true)
const snFilter = ref('')

const filteredRows = computed(() => {
  const q = (snFilter.value || '').trim().toLowerCase()
  if (!q) return rows.value
  return rows.value.filter(r => (r.sn || '').toLowerCase().includes(q))
})

async function loadData() {
  loading.value = true
  try {
    const res = await getRecentAnomalies({ limit: 80, onlyRisk: onlyRisk.value })
    rows.value = res.data || []
    ElMessage.success('扫描完成')
  } catch {
    ElMessage.error('扫描失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.page-container {
  padding: 10px 20px 30px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px;
}

.title {
  font-size: 16px;
  font-weight: 700;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.desc {
  font-size: 13px;
  color: #606266;
  margin: 0 0 14px;
}

.tag-gap {
  margin-right: 6px;
  margin-bottom: 4px;
}
</style>
