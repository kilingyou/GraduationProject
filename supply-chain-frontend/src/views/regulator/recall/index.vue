<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="card-header">
        <div class="title">召回管理</div>
        <el-button type="primary" @click="openCreate">发布召回</el-button>
      </div>

      <div class="analysis-area">
        <el-input
          v-model="analysisSn"
          placeholder="输入问题源 SN，点击分析影响范围"
          clearable
          style="max-width: 360px"
        />
        <el-button type="success" :loading="analyzing" @click="handleAnalyze">分析影响范围</el-button>
        <el-button :disabled="!analysisSn" @click="handleExportEvidence">导出证据包(JSON)</el-button>
        <el-button :disabled="!analysisSn" @click="handleExportEvidencePdf">导出证据包(PDF)</el-button>
        <el-button type="warning" :disabled="!analysisSn" @click="handleAnomaly">异常监控分析</el-button>
      </div>

      <el-card shadow="never" class="analysis-card">
        <template #header>
          <div class="section-title">自动任务状态</div>
        </template>
        <el-descriptions :column="2" border size="small" v-if="schedulerStatus">
          <el-descriptions-item label="是否启用">{{ schedulerStatus.enabled ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="扫描间隔(ms)">{{ schedulerStatus.intervalMs }}</el-descriptions-item>
          <el-descriptions-item label="投诉阈值">{{ schedulerStatus.thresholdCount }}</el-descriptions-item>
          <el-descriptions-item label="窗口(分钟)">{{ schedulerStatus.thresholdWindowMinutes }}</el-descriptions-item>
          <el-descriptions-item label="投诉通告默认状态">{{ schedulerStatus.defaultNoticeStatusForComplaints }}</el-descriptions-item>
          <el-descriptions-item label="累计运行次数">{{ schedulerStatus.totalRuns }}</el-descriptions-item>
          <el-descriptions-item label="投诉触发通告">{{ schedulerStatus.createdFromComplaints }}</el-descriptions-item>
          <el-descriptions-item label="抽检触发通告">{{ schedulerStatus.createdFromInspections }}</el-descriptions-item>
          <el-descriptions-item label="最近运行">{{ schedulerStatus.lastRunAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最近成功">{{ schedulerStatus.lastSuccessAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最近错误" :span="2">{{ schedulerStatus.lastError || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="analysis-area">
          <el-button type="warning" :loading="runningNow" @click="handleRunNow">立即执行一次</el-button>
          <el-button @click="loadSchedulerStatus">刷新状态</el-button>
        </div>
      </el-card>

      <el-card v-if="analysis" class="analysis-card" shadow="never">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="问题源 SN">{{ analysis.sn }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ analysis.status || '-' }}</el-descriptions-item>
          <el-descriptions-item label="问题批次">{{ analysis.batchNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="组装上链 TxHash">{{ analysis.assemblyRecord?.txHash || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="analysis.ecidList" class="ecid-list">
          <div class="ecid-title">涉及部件 ECID（占位展示）</div>
          <div class="ecid-content">{{ safeEcids }}</div>
        </div>
      </el-card>

      <el-card v-if="anomaly" class="analysis-card" shadow="never">
        <template #header>
          <div class="section-title">串货/异常监控</div>
        </template>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="SN">{{ anomaly.sn }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <el-tag :type="anomaly.riskLevel === 'HIGH' ? 'danger' : anomaly.riskLevel === 'MEDIUM' ? 'warning' : 'success'">
              {{ anomaly.riskLevel }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="物流事件数">{{ anomaly.transferCount }}</el-descriptions-item>
          <el-descriptions-item label="绑定人数">{{ anomaly.bindCount }}</el-descriptions-item>
          <el-descriptions-item label="风险标记" :span="2">
            <el-tag v-for="flag in (anomaly.riskFlags || [])" :key="flag" class="mr-8" type="danger">{{ flag }}</el-tag>
            <span v-if="!anomaly.riskFlags || !anomaly.riskFlags.length">无</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <div class="section-title">召回通告列表</div>
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="noticeNo" label="通告号" min-width="160" />
        <el-table-column prop="faultSourceSn" label="问题源SN" min-width="180" />
        <el-table-column prop="faultBatchId" label="问题批次" min-width="160" />
        <el-table-column prop="disposalPlan" label="处理方案" min-width="220" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" min-width="120" />
        <el-table-column prop="txHash" label="上链交易哈希" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" min-width="180" />
      </el-table>

      <div class="pagination-wrapper" v-if="total > 0">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <el-dialog v-model="createVisible" title="发布召回通告" width="640px" @close="resetCreate">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="120px">
        <el-form-item label="问题源 SN" prop="faultSourceSn">
          <el-input v-model="createForm.faultSourceSn" placeholder="输入问题源 SN" />
        </el-form-item>
        <el-form-item label="问题批次" prop="faultBatchId">
          <el-input v-model="createForm.faultBatchId" placeholder="可由分析结果自动带入" />
        </el-form-item>
        <el-form-item label="处理方案" prop="disposalPlan">
          <el-input
            v-model="createForm.disposalPlan"
            placeholder="退回维修/销毁/其他"
            type="textarea"
            :rows="4"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  analyzeRecall,
  analyzeAnomaly,
  createRecallNotice,
  getRecallNoticeList,
  exportRecallEvidence,
  exportRecallEvidencePdf,
  getRecallSchedulerStatus,
  runRecallSchedulerNow
} from '@/api/regulator'

const loading = ref(false)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const tableData = ref([])

const analyzing = ref(false)
const analysis = ref(null)
const analysisSn = ref('')
const anomaly = ref(null)
const schedulerStatus = ref(null)
const runningNow = ref(false)

const safeEcids = computed(() => {
  const v = analysis.value?.ecidList
  if (!v) return '-'
  if (typeof v === 'string') return v
  try {
    return JSON.stringify(v)
  } catch {
    return String(v)
  }
})

async function handleAnalyze() {
  if (!analysisSn.value) {
    ElMessage.warning('请输入问题源 SN')
    return
  }
  analyzing.value = true
  try {
    const res = await analyzeRecall(analysisSn.value)
    analysis.value = res.data || null
    // 带入批次字段（如果分析结果包含 batchNo）
    if (analysis.value?.faultBatchId) {
      createForm.faultBatchId = analysis.value.faultBatchId
    } else if (analysis.value?.batchNo) {
      createForm.faultBatchId = analysis.value.batchNo
    }
    createForm.faultEcid = analysis.value?.faultEcid || ''
    createForm.faultSourceSn = analysis.value?.sn || analysisSn.value
  } catch {
    ElMessage.error('分析失败')
  } finally {
    analyzing.value = false
  }
}

async function handleExportEvidence() {
  if (!analysisSn.value) {
    ElMessage.warning('请先输入 SN')
    return
  }
  try {
    const res = await exportRecallEvidence(analysisSn.value)
    const data = res.data || {}
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `evidence-${analysisSn.value}.json`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('证据包已导出')
  } catch {
    ElMessage.error('导出失败')
  }
}

async function handleExportEvidencePdf() {
  if (!analysisSn.value) {
    ElMessage.warning('请先输入 SN')
    return
  }
  try {
    const blob = await exportRecallEvidencePdf(analysisSn.value)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `evidence-${analysisSn.value}.pdf`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('PDF 证据包已导出')
  } catch {
    ElMessage.error('PDF 导出失败')
  }
}

async function handleAnomaly() {
  if (!analysisSn.value) {
    ElMessage.warning('请先输入 SN')
    return
  }
  try {
    const res = await analyzeAnomaly(analysisSn.value)
    anomaly.value = res.data || null
    ElMessage.success('异常分析完成')
  } catch {
    ElMessage.error('异常分析失败')
  }
}

async function loadSchedulerStatus() {
  try {
    const res = await getRecallSchedulerStatus()
    schedulerStatus.value = res.data || null
  } catch {
    schedulerStatus.value = null
  }
}

async function handleRunNow() {
  runningNow.value = true
  try {
    await runRecallSchedulerNow()
    ElMessage.success('已触发自动任务')
    await loadSchedulerStatus()
    await fetchList()
  } catch {
    ElMessage.error('触发失败')
  } finally {
    runningNow.value = false
  }
}

const createVisible = ref(false)
const createSubmitting = ref(false)
const createFormRef = ref(null)

const createForm = reactive({
  faultSourceSn: '',
  faultBatchId: '',
  faultEcid: '',
  affectedSns: '[]',
  disposalPlan: ''
})

const createRules = {
  faultSourceSn: [{ required: true, message: '请输入问题源 SN', trigger: 'blur' }],
  faultBatchId: [{ required: true, message: '请输入问题批次', trigger: 'blur' }],
  disposalPlan: [{ required: true, message: '请输入处理方案', trigger: 'blur' }]
}

function openCreate() {
  if (analysis.value) {
    createForm.faultSourceSn = analysis.value?.sn || analysisSn.value
    createForm.faultBatchId = analysis.value?.faultBatchId || analysis.value?.batchNo || createForm.faultBatchId
    createForm.faultEcid = analysis.value?.faultEcid || createForm.faultEcid
  } else {
    createForm.faultSourceSn = analysisSn.value
  }
  createVisible.value = true
}

function resetCreate() {
  createForm.faultEcid = ''
  createForm.affectedSns = '[]'
  createForm.disposalPlan = ''
}

async function submitCreate() {
  if (!createFormRef.value) return
  await createFormRef.value.validate()

  createSubmitting.value = true
  try {
    await createRecallNotice(createForm)
    ElMessage.success('已发布召回通告')
    createVisible.value = false
    await fetchList()
  } catch {
    ElMessage.error('发布失败')
  } finally {
    createSubmitting.value = false
  }
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getRecallNoticeList({ pageNum: pageNum.value, pageSize: pageSize.value })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    ElMessage.error('获取列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadSchedulerStatus()
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

.analysis-area {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.analysis-card {
  margin-top: 14px;
}

.section-title {
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 10px;
}

.analysis-card {
  border-radius: 10px;
}

.mr-8 {
  margin-right: 8px;
  margin-bottom: 6px;
}

.ecid-list {
  margin-top: 12px;
}

.ecid-title {
  font-size: 13px;
  color: #333;
  font-weight: 600;
  margin-bottom: 8px;
}

.ecid-content {
  color: #666;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-word;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

