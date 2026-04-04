<template>
  <div class="app-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <el-icon><Box /></el-icon>
          <span>库存管理</span>
        </div>
      </template>

      <el-alert type="info" :closable="false" show-icon class="mb-16"
        title="货权视图：current_holder_id 在您名下的整机（分销商、组装商等任一环节用户均可持有货权）。发货后对方在「物流流转」确认收货后变为在库。旧库请执行 alter_assembly_current_holder.sql。" />

      <!-- Filters -->
      <el-row :gutter="16" class="mb-16">
        <el-col :span="8">
          <el-input v-model="query.sn" placeholder="搜索产品 SN" clearable @clear="loadList" @keyup.enter="loadList">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </el-col>
        <el-col :span="16">
          <el-radio-group v-model="query.status" @change="loadList">
            <el-radio-button label="">全部</el-radio-button>
            <el-radio-button label="IN_STOCK">在库</el-radio-button>
            <el-radio-button label="IN_TRANSIT">在途发出</el-radio-button>
            <el-radio-button label="ON_CHAIN">已上链待出</el-radio-button>
          </el-radio-group>
        </el-col>
      </el-row>

      <!-- Table -->
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="sn" label="SN" width="200" />
        <el-table-column prop="assemblyBatchNo" label="组装批次" width="180" />
        <el-table-column prop="firmwareVersion" label="固件版本" width="120" />
        <el-table-column prop="status" label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" min-width="180" />
      </el-table>
      <el-pagination
        class="mt-16"
        v-model:current-page="page.page"
        v-model:page-size="page.size"
        :total="page.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadList"
        @current-change="loadList"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Box, Search } from '@element-plus/icons-vue'
import { getInventoryList } from '@/api/distributor'

const loading = ref(false)
const list = ref([])
const query = reactive({ sn: '', status: '' })
const page = reactive({ page: 1, size: 10, total: 0 })

async function loadList() {
  loading.value = true
  try {
    const params = { page: page.page, size: page.size }
    if (query.sn) params.sn = query.sn
    if (query.status) params.status = query.status
    const res = await getInventoryList(params)
    list.value = res.data?.records || res.data?.list || []
    page.total = res.data?.total || 0
  } catch {
    ElMessage.error('加载库存列表失败')
  } finally {
    loading.value = false
  }
}

function statusType(s) {
  const m = { IN_STOCK: 'success', IN_TRANSIT: 'warning', ON_CHAIN: 'info', ASSEMBLED: '' }
  return m[s] ?? ''
}
function statusLabel(s) {
  const m = { IN_STOCK: '在库', IN_TRANSIT: '在途', ON_CHAIN: '已上链', ASSEMBLED: '已组装' }
  return m[s] ?? s
}

onMounted(loadList)
</script>

<style scoped lang="scss">
.app-container {
  padding: 20px;
}
.mb-16 {
  margin-bottom: 16px;
}
.mt-16 {
  margin-top: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
</style>
