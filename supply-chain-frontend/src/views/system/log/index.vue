<template>
  <div class="log-manage">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="操作人">
          <el-input v-model="queryParams.username" placeholder="请输入操作人" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="操作关键词">
          <el-input v-model="queryParams.operation" placeholder="匹配操作描述" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="username" label="操作人" width="120" />
        <el-table-column prop="operation" label="操作描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="method" label="方法" min-width="220" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column prop="resultStatus" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.resultStatus === 1 ? 'success' : 'danger'" size="small">
              {{ row.resultStatus === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="txHash" label="交易哈希" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.txHash" class="tx-hash">{{ row.txHash }}</span>
            <span v-else class="no-data">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="operationTime" label="操作时间" width="170" align="center" />
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getLogList } from '@/api/system'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({
  username: '',
  operation: '',
  pageNum: 1,
  pageSize: 10
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getLogList({
      page: queryParams.pageNum,
      size: queryParams.pageSize,
      username: queryParams.username || undefined,
      operation: queryParams.operation || undefined
    })
    tableData.value = res.data.records || res.data.list || []
    total.value = res.data.total || 0
  } catch {
    ElMessage.error('获取日志列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.pageNum = 1
  fetchList()
}

function handleReset() {
  queryParams.username = ''
  queryParams.operation = ''
  queryParams.pageNum = 1
  fetchList()
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.log-manage {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-card {
  border-radius: 8px;
  :deep(.el-card__body) {
    padding-bottom: 2px;
  }
}

.table-card {
  border-radius: 8px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.tx-hash {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #409eff;
}

.no-data {
  color: #c0c4cc;
}
</style>
