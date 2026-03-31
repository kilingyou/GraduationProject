<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="card-header">
        <div class="title">供应商资质审核</div>
        <el-button :loading="loading" @click="fetchList">刷新</el-button>
      </div>

      <el-tabs v-model="activeTab" style="margin-top: 16px">
        <el-tab-pane label="待审核" name="pending">
          <el-table :data="pendingData" border stripe v-loading="loading">
            <el-table-column prop="enterpriseName" label="企业名称" min-width="220" />
            <el-table-column prop="creditCode" label="信用代码" min-width="160" />
            <el-table-column label="营业执照" min-width="220">
              <template #default="{ row }">
                <el-space>
                  <el-button
                    v-if="row.licenseIpfsCid"
                    type="primary"
                    link
                    @click="openFileByCid(row.licenseIpfsCid)"
                  >
                    查看文件
                  </el-button>
                  <el-tag v-if="row.licenseIpfsCid" type="info" effect="plain" size="small">
                    {{ shortCid(row.licenseIpfsCid) }}
                  </el-tag>
                  <span v-if="!row.licenseIpfsCid">-</span>
                </el-space>
              </template>
            </el-table-column>
            <el-table-column label="资质证书" min-width="220">
              <template #default="{ row }">
                <el-space>
                  <el-button
                    v-if="row.certIpfsCid"
                    type="primary"
                    link
                    @click="openFileByCid(row.certIpfsCid)"
                  >
                    查看文件
                  </el-button>
                  <el-tag v-if="row.certIpfsCid" type="info" effect="plain" size="small">
                    {{ shortCid(row.certIpfsCid) }}
                  </el-tag>
                  <span v-if="!row.certIpfsCid">-</span>
                </el-space>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="提交时间" min-width="180" />
            <el-table-column label="操作" width="220" align="center">
              <template #default="{ row }">
                <el-space>
                  <el-button size="small" type="success" @click="approve(row)">通过</el-button>
                  <el-button size="small" type="danger" @click="openReject(row)">驳回</el-button>
                </el-space>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="已通过" name="approved">
          <el-table :data="approvedData" border stripe v-loading="loading">
            <el-table-column prop="enterpriseName" label="企业名称" min-width="220" />
            <el-table-column prop="creditCode" label="信用代码" min-width="160" />
            <el-table-column label="营业执照" min-width="220">
              <template #default="{ row }">
                <el-space>
                  <el-button
                    v-if="row.licenseIpfsCid"
                    type="primary"
                    link
                    @click="openFileByCid(row.licenseIpfsCid)"
                  >
                    查看文件
                  </el-button>
                  <el-tag v-if="row.licenseIpfsCid" type="info" effect="plain" size="small">
                    {{ shortCid(row.licenseIpfsCid) }}
                  </el-tag>
                  <span v-if="!row.licenseIpfsCid">-</span>
                </el-space>
              </template>
            </el-table-column>
            <el-table-column label="资质证书" min-width="220">
              <template #default="{ row }">
                <el-space>
                  <el-button
                    v-if="row.certIpfsCid"
                    type="primary"
                    link
                    @click="openFileByCid(row.certIpfsCid)"
                  >
                    查看文件
                  </el-button>
                  <el-tag v-if="row.certIpfsCid" type="info" effect="plain" size="small">
                    {{ shortCid(row.certIpfsCid) }}
                  </el-tag>
                  <span v-if="!row.certIpfsCid">-</span>
                </el-space>
              </template>
            </el-table-column>
            <el-table-column prop="auditTime" label="审核时间" min-width="180" />
            <el-table-column prop="txHash" label="上链交易哈希" min-width="220" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
      </el-tabs>
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

    <el-dialog
      v-model="previewDialogVisible"
      title="资质文件预览"
      width="80%"
      top="5vh"
      @close="closePreview"
    >
      <div v-if="previewLoading" class="preview-loading">文件加载中...</div>
      <div v-else class="preview-wrap">
        <iframe
          v-if="previewUrl"
          :src="previewUrl"
          class="preview-frame"
          frameborder="0"
        />
        <div v-else class="preview-empty">暂无可预览内容</div>
      </div>
      <template #footer>
        <el-button @click="closePreview">关闭</el-button>
        <el-button type="primary" :disabled="!previewBlob" @click="downloadPreview">下载文件</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAuditList, approveSupplier, rejectSupplier, getAuditFileBlob } from '@/api/regulator'

const loading = ref(false)
const activeTab = ref('pending')
const pendingData = ref([])
const approvedData = ref([])

const rejectDialogVisible = ref(false)
const rejectOpinion = ref('')
const rejectSubmitting = ref(false)
const currentRejectId = ref(null)
const previewDialogVisible = ref(false)
const previewLoading = ref(false)
const previewUrl = ref('')
const previewBlob = ref(null)
const previewFileName = ref('qualification-file')

async function fetchList() {
  loading.value = true
  try {
    const [pendingRes, approvedRes] = await Promise.all([
      getAuditList({ status: 'PENDING' }),
      getAuditList({ status: 'APPROVED' })
    ])
    pendingData.value = pendingRes.data || []
    approvedData.value = approvedRes.data || []
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

async function openFileByCid(cid) {
  previewDialogVisible.value = true
  previewLoading.value = true
  previewFileName.value = `qualification-${cid.slice(0, 8)}`
  try {
    const data = await getAuditFileBlob(cid)
    const blob = data instanceof Blob ? data : new Blob([data], { type: 'application/octet-stream' })
    previewBlob.value = blob
    const objectUrl = window.URL.createObjectURL(blob)
    if (previewUrl.value) {
      window.URL.revokeObjectURL(previewUrl.value)
    }
    previewUrl.value = objectUrl
  } catch {
    previewDialogVisible.value = false
    ElMessage.error('文件读取失败，请确认 IPFS 节点在线')
  } finally {
    previewLoading.value = false
  }
}

function shortCid(cid) {
  if (!cid || cid.length <= 16) return cid
  return `${cid.slice(0, 8)}...${cid.slice(-8)}`
}

function closePreview() {
  previewDialogVisible.value = false
  if (previewUrl.value) {
    window.URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
  previewBlob.value = null
}

function downloadPreview() {
  if (!previewBlob.value) return
  const url = window.URL.createObjectURL(previewBlob.value)
  const a = document.createElement('a')
  a.href = url
  a.download = previewFileName.value
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => window.URL.revokeObjectURL(url), 1000)
}
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

.preview-loading {
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666;
}

.preview-wrap {
  height: 70vh;
}

.preview-frame {
  width: 100%;
  height: 100%;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fff;
}

.preview-empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
}
</style>

