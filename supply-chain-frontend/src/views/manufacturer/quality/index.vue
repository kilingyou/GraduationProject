<template>
  <div class="quality-container">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <!-- 质检操作 -->
        <el-tab-pane label="质检操作" name="operation">
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            class="qc-flow-alert"
            title="流程：先上传检测报告（写入设备报告哈希）→ 再点「标记合格」→ 最后在「生产」页对 ECID 执行上链注册。"
          />
          <el-form
            ref="qcFormRef"
            :model="qcForm"
            :rules="qcRules"
            label-width="110px"
            style="max-width: 600px"
          >
            <el-form-item label="目标类型" prop="targetType">
              <el-radio-group v-model="qcForm.targetType">
                <el-radio value="ECID">按ECID</el-radio>
                <el-radio value="BATCH">按批次</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item
              :label="qcForm.targetType === 'ECID' ? 'ECID' : '批次号'"
              prop="targetId"
            >
              <el-input
                v-model="qcForm.targetId"
                :placeholder="`请输入${qcForm.targetType === 'ECID' ? 'ECID' : '批次号'}`"
              />
            </el-form-item>

            <el-form-item label="检测报告" prop="reportFile">
              <el-upload
                ref="uploadRef"
                v-model:file-list="qcForm.fileList"
                :auto-upload="false"
                :limit="1"
                accept=".pdf,.doc,.docx,.xlsx,.xls"
                :on-exceed="() => ElMessage.warning('只能上传一个文件')"
              >
                <el-button type="primary" plain>选择报告文件</el-button>
                <template #tip>
                  <div class="el-upload__tip">支持 PDF / Word / Excel</div>
                </template>
              </el-upload>
            </el-form-item>

            <el-form-item label="检测结果" prop="result">
              <el-radio-group v-model="qcForm.result">
                <el-radio value="PASS">合格</el-radio>
                <el-radio value="FAIL">不合格</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item v-if="qcForm.result === 'FAIL'" label="不合格原因" prop="reason">
              <el-input
                v-model="qcForm.reason"
                type="textarea"
                :rows="3"
                placeholder="请详细描述不合格原因"
              />
            </el-form-item>

            <el-form-item v-if="qcForm.result === 'FAIL'" label="后续处置" prop="disposalType">
              <el-radio-group v-model="qcForm.disposalType">
                <el-radio value="RETURN">退货至供应商（供应商在「不合格处置」确认收讫）</el-radio>
                <el-radio value="DESTROY">就地销毁（本企业在下方列表确认销毁完成）</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item>
              <el-button
                v-if="qcForm.result === 'PASS'"
                type="success"
                :loading="submitting"
                @click="handlePass"
              >
                标记合格并上链
              </el-button>
              <el-button
                v-if="qcForm.result === 'FAIL'"
                type="danger"
                :loading="submitting"
                @click="handleFail"
              >
                标记不合格
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 不合格处置跟踪 -->
        <el-tab-pane label="不合格处置" name="rejectDisposition">
          <el-table v-loading="rejectLoading" :data="rejectList" border stripe>
            <el-table-column prop="orderId" label="订单号" min-width="130" show-overflow-tooltip />
            <el-table-column prop="ecid" label="ECID" min-width="200" show-overflow-tooltip />
            <el-table-column prop="disposalType" label="处置方式" width="100" align="center">
              <template #default="{ row }">
                {{ row.disposalType === 'RETURN' ? '退货' : row.disposalType === 'DESTROY' ? '销毁' : row.disposalType }}
              </template>
            </el-table-column>
            <el-table-column prop="disposalStatus" label="状态" width="150" align="center">
              <template #default="{ row }">
                <el-tag :type="rejTag(row.disposalStatus)" effect="plain">
                  {{ rejLabel(row.disposalStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="原因" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="130" align="center" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.disposalType === 'DESTROY' && row.disposalStatus === 'AWAITING_MFG_DESTROY'"
                  type="danger"
                  link
                  :loading="destroyingId === row.id"
                  @click="onConfirmDestroy(row)"
                >
                  确认已销毁
                </el-button>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="rejectQuery.pageNum"
              v-model:page-size="rejectQuery.pageSize"
              :total="rejectTotal"
              :page-sizes="[10, 20, 50]"
              background
              layout="total, sizes, prev, pager, next"
              @size-change="fetchRejectList"
              @current-change="fetchRejectList"
            />
          </div>
        </el-tab-pane>

        <!-- 质检报告列表 -->
        <el-tab-pane label="质检报告列表" name="reports">
          <el-table
            v-loading="reportLoading"
            :data="reportList"
            stripe
            border
            style="width: 100%"
          >
            <el-table-column prop="reportName" label="报告名称" min-width="180" show-overflow-tooltip />
            <el-table-column prop="targetType" label="目标类型" width="100" align="center">
              <template #default="{ row }">
                {{ row.targetType === 'ECID' ? 'ECID' : '批次' }}
              </template>
            </el-table-column>
            <el-table-column prop="targetId" label="目标标识" min-width="180" show-overflow-tooltip />
            <el-table-column prop="result" label="结果" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.result === 'PASS' ? 'success' : 'danger'" effect="plain">
                  {{ row.result === 'PASS' ? '合格' : '不合格' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="fileHash" label="文件哈希" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <el-text class="hash-text" truncated>{{ row.fileHash || '-' }}</el-text>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="170" align="center" />
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="reportQuery.page"
              v-model:page-size="reportQuery.pageSize"
              :total="reportTotal"
              :page-sizes="[10, 20, 50]"
              background
              layout="total, sizes, prev, pager, next"
              @size-change="fetchReports"
              @current-change="fetchReports"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  uploadQualityReport,
  completeProduction,
  rejectDevice,
  getQualityReportList,
  getRejectRecordList,
  confirmRejectDestroy
} from '@/api/manufacturer'

const activeTab = ref('operation')
const submitting = ref(false)

// ================== QC Operation ==================
const qcFormRef = ref(null)
const uploadRef = ref(null)

const qcForm = reactive({
  targetType: 'ECID',
  targetId: '',
  fileList: [],
  result: 'PASS',
  reason: '',
  disposalType: 'RETURN'
})

const qcRules = {
  targetType: [{ required: true, message: '请选择目标类型', trigger: 'change' }],
  targetId: [{ required: true, message: '请输入目标标识', trigger: 'blur' }],
  result: [{ required: true, message: '请选择检测结果', trigger: 'change' }],
  reason: [{ required: true, message: '请输入不合格原因', trigger: 'blur' }],
  disposalType: [{ required: true, message: '请选择处置方式', trigger: 'change' }]
}

function resetForm() {
  qcForm.targetId = ''
  qcForm.fileList = []
  qcForm.result = 'PASS'
  qcForm.reason = ''
  qcForm.disposalType = 'RETURN'
  qcFormRef.value?.clearValidate()
}

async function uploadReport() {
  if (!qcForm.fileList.length) return null
  const formData = new FormData()
  formData.append('file', qcForm.fileList[0].raw)
  formData.append('targetType', qcForm.targetType)
  formData.append('targetId', qcForm.targetId)
  formData.append('result', qcForm.result)
  const { data } = await uploadQualityReport(formData)
  return data
}

async function handlePass() {
  const valid = await qcFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (!qcForm.fileList.length) {
    ElMessage.warning('请先上传质检报告文件')
    return
  }

  await ElMessageBox.confirm('确认标记为合格并上链？此操作不可撤销。', '质检确认', { type: 'warning' })

  submitting.value = true
  try {
    await uploadReport()
    await completeProduction({
      targetType: qcForm.targetType,
      targetId: qcForm.targetId
    })
    ElMessage.success('已标记合格并上链')
    resetForm()
    if (activeTab.value === 'reports') fetchReports()
  } catch { /* handled by interceptor */ } finally {
    submitting.value = false
  }
}

async function handleFail() {
  const valid = await qcFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (!qcForm.fileList.length) {
    ElMessage.warning('不合格处置须上传检测报告或佐证材料')
    return
  }

  submitting.value = true
  try {
    await uploadReport()
    await rejectDevice({
      targetType: qcForm.targetType,
      targetId: qcForm.targetId,
      reason: qcForm.reason,
      disposalType: qcForm.disposalType
    })
    ElMessage.success('已标记不合格')
    resetForm()
    if (activeTab.value === 'reports') fetchReports()
  } catch { /* handled by interceptor */ } finally {
    submitting.value = false
  }
}

// ================== Report List ==================
const reportLoading = ref(false)
const reportList = ref([])
const reportTotal = ref(0)
const reportQuery = reactive({ page: 1, pageSize: 10 })

async function fetchReports() {
  reportLoading.value = true
  try {
    const { data } = await getQualityReportList(reportQuery)
    reportList.value = data.records ?? data.list ?? []
    reportTotal.value = data.total ?? 0
  } catch { /* handled by interceptor */ } finally {
    reportLoading.value = false
  }
}

const rejectLoading = ref(false)
const rejectList = ref([])
const rejectTotal = ref(0)
const rejectQuery = reactive({ pageNum: 1, pageSize: 10 })
const destroyingId = ref(null)

function rejLabel(s) {
  const m = {
    AWAITING_SUPPLIER: '待供应商确认退货',
    AWAITING_MFG_DESTROY: '待确认销毁',
    COMPLETED: '已完结'
  }
  return m[s] || s || '-'
}

function rejTag(s) {
  if (s === 'COMPLETED') return 'success'
  if (s === 'AWAITING_MFG_DESTROY') return 'danger'
  if (s === 'AWAITING_SUPPLIER') return 'warning'
  return 'info'
}

async function fetchRejectList() {
  rejectLoading.value = true
  try {
    const { data } = await getRejectRecordList(rejectQuery)
    rejectList.value = data.records ?? data.list ?? []
    rejectTotal.value = data.total ?? 0
  } catch { /* interceptor */ } finally {
    rejectLoading.value = false
  }
}

async function onConfirmDestroy(row) {
  try {
    await ElMessageBox.confirm(
      `确认 ECID ${row.ecid} 已按规范完成销毁？确认后将上链登记。`,
      '销毁确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  destroyingId.value = row.id
  try {
    await confirmRejectDestroy({ id: row.id })
    ElMessage.success('已确认销毁并上链')
    fetchRejectList()
  } catch { /* interceptor */ } finally {
    destroyingId.value = null
  }
}

watch(activeTab, (val) => {
  if (val === 'reports') fetchReports()
  if (val === 'rejectDisposition') fetchRejectList()
})

onMounted(fetchReports)
</script>

<style scoped lang="scss">
.quality-container {
  .qc-flow-alert {
    margin-bottom: 16px;
  }

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
}
</style>
