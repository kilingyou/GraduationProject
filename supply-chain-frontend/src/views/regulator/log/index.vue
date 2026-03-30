<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="card-header">
        <div class="title">全局审计日志</div>
        <el-button :loading="loading" @click="fetchList">刷新</el-button>
      </div>

      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="操作人">
          <el-input v-model="queryParams.username" placeholder="用户名" clearable />
        </el-form-item>
        <el-form-item label="操作描述关键词">
          <el-input v-model="queryParams.operation" placeholder="请输入关键词" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" border stripe style="margin-top: 12px">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="username" label="操作人" min-width="120" />
        <el-table-column prop="operation" label="操作描述" min-width="220" show-overflow-tooltip />
        <el-table-column prop="method" label="方法" min-width="220" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column prop="resultStatus" label="结果" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.resultStatus === 1 ? 'success' : 'danger'" size="small">
              {{ row.resultStatus === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="txHash" label="交易哈希" min-width="200" show-overflow-tooltip />
        <el-table-column prop="operationTime" label="操作时间" min-width="180" />
      </el-table>

      <div class="pagination-wrapper" v-if="total > 0">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          v-model:current-page="queryParams.page"
          v-model:page-size="queryParams.size"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getLogList } from '@/api/system'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const queryParams = reactive({
  username: '',
  operation: '',
  page: 1,
  size: 10
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getLogList(queryParams)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    ElMessage.error('获取日志失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.page = 1
  fetchList()
}

function handleReset() {
  queryParams.username = ''
  queryParams.operation = ''
  queryParams.page = 1
  queryParams.size = 10
  fetchList()
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.page-container {
  padding: 10px 20px 30px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.title {
  font-size: 16px;
  font-weight: 700;
}

.search-form {
  margin-top: 6px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

