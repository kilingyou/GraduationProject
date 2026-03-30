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
      </div>

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
  createRecallNotice,
  getRecallNoticeList
} from '@/api/regulator'

const loading = ref(false)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const tableData = ref([])

const analyzing = ref(false)
const analysis = ref(null)
const analysisSn = ref('')

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
    if (analysis.value?.batchNo) {
      createForm.faultBatchId = analysis.value.batchNo
    }
    createForm.faultSourceSn = analysis.value?.sn || analysisSn.value
  } catch {
    ElMessage.error('分析失败')
  } finally {
    analyzing.value = false
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
    createForm.faultBatchId = analysis.value?.batchNo || createForm.faultBatchId
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

