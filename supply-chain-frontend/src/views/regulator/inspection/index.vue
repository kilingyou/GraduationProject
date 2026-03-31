<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="card-header">
        <div class="title">抽检任务管理</div>
        <el-button type="primary" @click="openCreate">发起抽检</el-button>
      </div>

      <el-table :data="tableData" border stripe v-loading="loading" style="margin-top: 16px">
        <el-table-column prop="taskNo" label="任务号" min-width="180" />
        <el-table-column prop="targetType" label="目标类型" width="120" />
        <el-table-column prop="targetId" label="目标标识" min-width="180" />
        <el-table-column prop="inspectionResult" label="检测结果" min-width="140">
          <template #default="{ row }">
            <el-tag :type="row.inspectionResult === 'PASS' ? 'success' : row.inspectionResult === 'FAIL' ? 'danger' : 'info'" size="small">
              {{ row.inspectionResult || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="140" />
        <el-table-column prop="createTime" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'CREATED'"
              size="small"
              type="primary"
              @click="openResult(row)"
            >
              提交结果
            </el-button>
          </template>
        </el-table-column>
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

    <el-dialog v-model="createVisible" title="发起抽检任务" width="520px" @close="resetCreate">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="110px">
        <el-form-item label="目标类型" prop="targetType">
          <el-select v-model="createForm.targetType" placeholder="请选择">
            <el-option label="ECID" value="ECID" />
            <el-option label="BATCH" value="BATCH" />
            <el-option label="SN" value="SN" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标标识" prop="targetId">
          <el-input v-model="createForm.targetId" placeholder="输入 ECID/BATCH/SN" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultVisible" title="提交检测结果" width="640px" @close="resetResult">
      <el-form :model="resultForm" :rules="resultRules" ref="resultFormRef" label-width="120px">
        <el-form-item label="检测结果" prop="inspectionResult">
          <el-radio-group v-model="resultForm.inspectionResult">
            <el-radio label="PASS">合格</el-radio>
            <el-radio label="FAIL">不合格</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="报告哈希" prop="reportHash">
          <el-input v-model="resultForm.reportHash" placeholder="由系统上传后自动计算" disabled />
        </el-form-item>
        <el-form-item label="报告 CID" prop="reportCid">
          <el-upload
            drag
            :auto-upload="false"
            :file-list="reportFiles"
            :on-change="handleReportFileChange"
          >
            <div class="el-upload__text">选择官方检测报告文件（将上传至 IPFS 并计算 SHA-256）</div>
          </el-upload>
          <div class="cid-hint" v-if="resultForm.reportCid">当前：{{ resultForm.reportCid }}</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="resultVisible = false">取消</el-button>
        <el-button type="primary" :loading="resultSubmitting" @click="submitResult">确认提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createInspection,
  getInspectionList,
  submitInspectionResultMultipart
} from '@/api/regulator'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const createVisible = ref(false)
const createSubmitting = ref(false)
const createFormRef = ref(null)

const createForm = reactive({
  targetType: 'ECID',
  targetId: ''
})

const createRules = {
  targetType: [{ required: true, message: '请选择目标类型', trigger: 'change' }],
  targetId: [{ required: true, message: '请输入目标标识', trigger: 'blur' }]
}

const resultVisible = ref(false)
const resultSubmitting = ref(false)
const resultFormRef = ref(null)
const currentResultId = ref(null)

const resultForm = reactive({
  inspectionResult: 'PASS',
  reportHash: '',
  reportCid: '',
  inspectorSign: ''
})

const resultRules = {
  inspectionResult: [{ required: true, message: '请选择检测结果', trigger: 'change' }],
  reportCid: [{ required: true, message: '请上传官方检测报告文件', trigger: 'change' }]
}

const reportFiles = ref([])

function openCreate() {
  createVisible.value = true
}

function resetCreate() {
  createForm.targetType = 'ECID'
  createForm.targetId = ''
}

async function submitCreate() {
  if (!createFormRef.value) return
  try { await createFormRef.value.validate() } catch { return }

  createSubmitting.value = true
  try {
    await createInspection(createForm)
    ElMessage.success('抽检任务已创建')
    createVisible.value = false
    await fetchList()
  } catch {
    ElMessage.error('创建失败')
  } finally {
    createSubmitting.value = false
  }
}

function openResult(row) {
  currentResultId.value = row.id
  resetResult()
  resultVisible.value = true
}

function resetResult() {
  resultForm.inspectionResult = 'PASS'
  resultForm.reportHash = ''
  resultForm.reportCid = ''
  resultForm.inspectorSign = ''
  reportFiles.value = []
}

function handleReportFileChange(_file, fileList) {
  reportFiles.value = fileList
  const first = fileList[0]
  if (first?.name) resultForm.reportCid = first.name
}

async function submitResult() {
  if (!resultFormRef.value) return
  try { await resultFormRef.value.validate() } catch { return }
  if (!currentResultId.value) return
  const first = reportFiles.value?.[0]
  if (!first?.raw) {
    ElMessage.warning('请上传官方检测报告文件')
    return
  }

  resultSubmitting.value = true
  try {
    const fd = new FormData()
    fd.append('inspectionResult', resultForm.inspectionResult)
    fd.append('reportFile', first.raw)
    if (resultForm.inspectorSign) fd.append('inspectorSign', resultForm.inspectorSign)
    const res = await submitInspectionResultMultipart(currentResultId.value, fd)
    // 回填后端计算结果
    if (res?.data) {
      resultForm.reportHash = res.data.reportHash || ''
      resultForm.reportCid = res.data.reportCid || resultForm.reportCid
    }
    ElMessage.success('检测结果已提交')
    resultVisible.value = false
    await fetchList()
  } catch {
    ElMessage.error('提交失败')
  } finally {
    resultSubmitting.value = false
  }
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getInspectionList({ pageNum: pageNum.value, pageSize: pageSize.value })
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

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.cid-hint {
  margin-top: 8px;
  font-size: 13px;
  color: #409eff;
}
</style>

