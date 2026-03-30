<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="card-header">
        <div class="title">供应商资质审核</div>
        <el-button :loading="loading" @click="fetchList">刷新</el-button>
      </div>

      <el-table :data="tableData" border stripe v-loading="loading" style="margin-top: 16px">
        <el-table-column prop="enterpriseName" label="企业名称" min-width="220" />
        <el-table-column prop="creditCode" label="信用代码" min-width="160" />
        <el-table-column prop="auditStatus" label="状态" min-width="120">
          <template #default="{ row }">
            <el-tag
              :type="row.auditStatus === 'PENDING' ? 'warning' : row.auditStatus === 'APPROVED' ? 'success' : 'danger'"
              size="small"
            >
              {{ row.auditStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="营业执照CID" min-width="180">
          <template #default="{ row }">
            <el-tag v-if="row.licenseIpfsCid" type="info" effect="plain" size="small">
              {{ row.licenseIpfsCid }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="资质证书CID" min-width="180">
          <template #default="{ row }">
            <el-tag v-if="row.certIpfsCid" type="info" effect="plain" size="small">
              {{ row.certIpfsCid }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="auditTime" label="审核时间" min-width="180" />
        <el-table-column prop="txHash" label="上链交易哈希" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="260" align="center">
          <template #default="{ row }">
            <el-space>
              <el-button
                v-if="row.auditStatus === 'PENDING'"
                size="small"
                type="success"
                @click="approve(row)"
              >
                通过
              </el-button>
              <el-button
                v-if="row.auditStatus === 'PENDING'"
                size="small"
                type="danger"
                @click="openReject(row)"
              >
                驳回
              </el-button>
            </el-space>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="rejectDialogVisible" title="驳回审核" width="560px" @close="rejectOpinion = ''">
      <div class="tip">请输入驳回意见（本版本不会影响 IPFS 文件，只会写入审核记录）。</div>
      <el-input
        v-model="rejectOpinion"
        type="textarea"
        :rows="5"
        placeholder="驳回原因/意见"
        style="margin-top: 12px"
      />
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejectSubmitting" @click="rejectCurrent">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAuditList, approveSupplier, rejectSupplier } from '@/api/regulator'

const loading = ref(false)
const tableData = ref([])

const rejectDialogVisible = ref(false)
const rejectOpinion = ref('')
const rejectSubmitting = ref(false)
const currentRejectId = ref(null)

async function fetchList() {
  loading.value = true
  try {
    const res = await getAuditList()
    tableData.value = res.data || []
  } catch {
    ElMessage.error('获取审核列表失败')
  } finally {
    loading.value = false
  }
}

async function approve(row) {
  try {
    await approveSupplier(row.id)
    ElMessage.success('审核通过')
    await fetchList()
  } catch {
    ElMessage.error('审核通过失败')
  }
}

function openReject(row) {
  currentRejectId.value = row.id
  rejectOpinion.value = ''
  rejectDialogVisible.value = true
}

async function rejectCurrent() {
  if (!currentRejectId.value) return
  if (!rejectOpinion.value) {
    ElMessage.warning('请填写驳回意见')
    return
  }

  rejectSubmitting.value = true
  try {
    await rejectSupplier(currentRejectId.value, { auditOpinion: rejectOpinion.value })
    ElMessage.success('已驳回')
    rejectDialogVisible.value = false
    await fetchList()
  } catch {
    ElMessage.error('驳回失败')
  } finally {
    rejectSubmitting.value = false
  }
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

.tip {
  color: #666;
  font-size: 13px;
}
</style>

