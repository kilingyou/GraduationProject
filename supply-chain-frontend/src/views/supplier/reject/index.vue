<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <span>不合格处置（退货确认）</span>
        <el-text type="info" size="small" style="margin-left: 12px">
          制造商质检不合格并选择「退货」后，由您确认实物收讫；确认后上链 MFG_REJECT_RETURN_DONE
        </el-text>
      </template>
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="orderId" label="订单号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="ecid" label="ECID" min-width="200" show-overflow-tooltip />
        <el-table-column prop="manufacturerName" label="制造商" min-width="120" show-overflow-tooltip />
        <el-table-column prop="reason" label="不合格原因" min-width="160" show-overflow-tooltip />
        <el-table-column prop="disposalStatus" label="处置状态" width="150" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.disposalStatus)" effect="plain">
              {{ statusLabel(row.disposalStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="txHash" label="不合格上链" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <el-text class="hash-text" truncated>{{ row.txHash || '-' }}</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="disposalCompleteTxHash" label="完结上链" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <el-text class="hash-text" truncated>{{ row.disposalCompleteTxHash || '-' }}</el-text>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.disposalStatus === 'AWAITING_SUPPLIER'"
              type="primary"
              link
              :loading="actingId === row.id"
              @click="onConfirm(row)"
            >
              确认退货收讫
            </el-button>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          background
          layout="total, sizes, prev, pager, next"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRejectDispositionList, confirmRejectReturn } from '@/api/supplier'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const actingId = ref(null)

const query = reactive({ pageNum: 1, pageSize: 10 })

function statusLabel(s) {
  const m = {
    AWAITING_SUPPLIER: '待确认退货',
    AWAITING_MFG_DESTROY: '待制造商销毁',
    COMPLETED: '已完结'
  }
  return m[s] || s || '-'
}

function statusTag(s) {
  if (s === 'COMPLETED') return 'success'
  if (s === 'AWAITING_SUPPLIER') return 'warning'
  return 'info'
}

async function fetchList() {
  loading.value = true
  try {
    const { data } = await getRejectDispositionList(query)
    list.value = data.records ?? data.list ?? []
    total.value = data.total ?? 0
  } catch { /* interceptor */ } finally {
    loading.value = false
  }
}

async function onConfirm(row) {
  try {
    await ElMessageBox.confirm(
      `确认已收到订单 ${row.orderId} 的退货实物（ECID: ${row.ecid}）？确认后将上链登记。`,
      '退货收讫确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  actingId.value = row.id
  try {
    await confirmRejectReturn({ id: row.id })
    ElMessage.success('已确认并上链')
    fetchList()
  } catch { /* interceptor */ } finally {
    actingId.value = null
  }
}

onMounted(fetchList)
</script>

<style scoped lang="scss">
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.hash-text {
  font-family: monospace;
  font-size: 12px;
}
.text-muted {
  color: #c0c4cc;
}
</style>
