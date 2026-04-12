<template>
  <div class="app-container">
    <!-- ECID Scan Section -->
    <el-card shadow="hover" class="mb-16">
      <template #header>
        <div class="card-header">
          <el-icon><Monitor /></el-icon>
          <span>元器件来料扫码</span>
        </div>
      </template>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form :model="scanForm" inline>
            <el-form-item label="ECID">
              <el-input
                v-model="scanForm.ecid"
                placeholder="请输入或扫码输入 ECID"
                clearable
                style="width: 300px"
                @keyup.enter="handleScan"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="scanLoading" @click="handleScan">
                扫码验证
              </el-button>
            </el-form-item>
          </el-form>
        </el-col>
      </el-row>

      <!-- Scan Result -->
      <div v-if="scanResult" class="scan-result">
        <el-result
          v-if="scanResult.status === 'PASS'"
          icon="success"
          title="验证通过"
          :sub-title="`ECID: ${scanResult.ecid} — 该器件已通过质检，可用于组装`"
        >
          <template #extra>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="器件类型">{{ scanResult.deviceType || '-' }}</el-descriptions-item>
              <el-descriptions-item label="生产批次">{{ scanResult.manufacturerBatchId || '-' }}</el-descriptions-item>
              <el-descriptions-item label="生产订单">{{ scanResult.orderId || '-' }}</el-descriptions-item>
              <el-descriptions-item label="BOM 子件">{{ scanResult.bomPartSummary || '-' }}</el-descriptions-item>
              <el-descriptions-item label="说明" :span="2">{{ scanResult.message || '-' }}</el-descriptions-item>
              <el-descriptions-item label="部件链上">
                <el-tag :type="scanResult.chainRegistered === 1 ? 'success' : 'info'" size="small">
                  {{ scanResult.chainRegistered === 1 ? '已注册' : '未注册' }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </template>
        </el-result>
        <el-result
          v-else-if="scanResult.status === 'REJECT'"
          icon="error"
          title="验证不通过"
          :sub-title="`ECID: ${scanResult.ecid} — 该器件质检未通过，不可用于组装`"
        >
          <template #extra>
            <el-alert type="error" :closable="false" show-icon>
              {{ scanResult.message || '不可用于组装' }}
              <span v-if="scanResult.boundToSn">（已绑定整机 SN：{{ scanResult.boundToSn }}）</span>
            </el-alert>
          </template>
        </el-result>
        <el-result
          v-else
          icon="warning"
          title="未找到记录"
          :sub-title="`ECID: ${scanForm.ecid} — 系统中无此器件记录`"
        />
      </div>
    </el-card>

    <!-- Batch Import Section -->
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <el-icon><Upload /></el-icon>
          <span>批量导入</span>
        </div>
      </template>
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls,.csv"
        :on-change="handleFileChange"
        :on-exceed="() => ElMessage.warning('只能上传一个文件')"
      >
        <template #trigger>
          <el-button>选择文件</el-button>
        </template>
        <el-button type="primary" :loading="importLoading" style="margin-left: 12px" @click="handleBatchImport">
          解析并校验
        </el-button>
        <el-button style="margin-left: 8px" @click="handleDownloadTemplate">下载 Excel 模板</el-button>
        <template #tip>
          <div class="el-upload__tip">上传后解析 ECID 列并逐条校验；模板首列为表头「ECID」。部件须制造商已放行给组装商且满足质检/上链条件方可通过。</div>
        </template>
      </el-upload>

      <el-table
        v-if="importResults.length"
        :data="importResults"
        v-loading="importLoading"
        border
        stripe
        style="margin-top: 16px"
      >
        <el-table-column prop="ecid" label="ECID" min-width="200" />
        <el-table-column prop="orderId" label="生产订单" min-width="140" show-overflow-tooltip />
        <el-table-column prop="bomPartSummary" label="BOM 子件" min-width="160" show-overflow-tooltip />
        <el-table-column prop="deviceType" label="器件类型" width="140" />
        <el-table-column prop="status" label="验证状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="备注" min-width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Monitor, Search, Upload } from '@element-plus/icons-vue'
import { scanEcid, importVerifyIntake, downloadIntakeImportTemplate } from '@/api/assembler'

const scanForm = ref({ ecid: '' })
const scanLoading = ref(false)
const scanResult = ref(null)

async function handleScan() {
  if (!scanForm.value.ecid.trim()) {
    ElMessage.warning('请输入 ECID')
    return
  }
  scanLoading.value = true
  scanResult.value = null
  try {
    const res = await scanEcid({ ecid: scanForm.value.ecid.trim() })
    scanResult.value = res.data
  } catch (e) {
    if (e.response?.status === 404) {
      scanResult.value = { status: 'NOT_FOUND', ecid: scanForm.value.ecid }
    } else {
      ElMessage.error(e.message || '扫码验证失败')
    }
  } finally {
    scanLoading.value = false
  }
}

const uploadRef = ref()
const importFile = ref(null)
const importLoading = ref(false)
const importResults = ref([])

function handleFileChange(file) {
  importFile.value = file.raw
}

async function handleBatchImport() {
  if (!importFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  importLoading.value = true
  try {
    const fd = new FormData()
    fd.append('file', importFile.value)
    const verifyRes = await importVerifyIntake(fd)
    importResults.value = verifyRes.data || []
    const pass = importResults.value.filter(r => r.status === 'PASS').length
    ElMessage.success(`校验完成：${importResults.value.length} 条，通过 ${pass} 条`)
    uploadRef.value?.clearFiles()
    importFile.value = null
  } catch (e) {
    ElMessage.error(e.message || '导入校验失败')
  } finally {
    importLoading.value = false
  }
}

async function handleDownloadTemplate() {
  try {
    const blob = await downloadIntakeImportTemplate()
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'ECID导入模板.xlsx'
    a.click()
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('下载模板失败')
  }
}

function statusTagType(status) {
  const map = { PASS: 'success', REJECT: 'danger', NOT_FOUND: 'info' }
  return map[status] || 'info'
}

function statusLabel(status) {
  const map = { PASS: '通过', REJECT: '不通过', NOT_FOUND: '未找到' }
  return map[status] || status
}
</script>

<style scoped lang="scss">
.app-container {
  padding: 20px;
}
.mb-16 {
  margin-bottom: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
.scan-result {
  margin-top: 20px;
  padding: 20px;
  background: #fafafa;
  border-radius: 8px;
}
</style>
