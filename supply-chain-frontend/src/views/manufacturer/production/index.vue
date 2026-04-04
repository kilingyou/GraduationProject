<template>
  <div class="production-container">
    <!-- Flow guide -->
    <el-card shadow="never" class="flow-card">
      <el-steps :active="activeStep" align-center finish-status="success">
        <el-step title="订单列表" description="选择已接订单" />
        <el-step title="绑定ECID" description="生成设备唯一标识" />
        <el-step title="批量注册" description="ECID 上链注册" />
        <el-step title="质检上传" description="上传检测报告" />
      </el-steps>
    </el-card>

    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <!-- 批次管理 -->
        <el-tab-pane label="批次管理" name="batch">
          <el-form :inline="true" :model="batchForm" :rules="batchRules" ref="batchFormRef" class="inline-form">
            <el-form-item label="关联订单" prop="orderId">
              <el-select
                v-model="batchForm.orderId"
                placeholder="选择订单"
                filterable
                style="width: 220px"
              >
                <el-option
                  v-for="o in orderOptions"
                  :key="o.orderId"
                  :label="`${o.orderId} (${o.bomName})`"
                  :value="o.orderId"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="计划数量" prop="plannedQty">
              <el-input-number v-model="batchForm.plannedQty" :min="1" :step="10" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="batchCreating" @click="handleCreateBatch">
                创建批次
              </el-button>
            </el-form-item>
          </el-form>

          <el-alert
            type="info"
            :closable="false"
            show-icon
            class="batch-hint"
            title="「完成数量」= 本批已生成 ECID 数（≤计划），不等于已完工。点「批次完工」时后端校验：设备数≥计划、每台均已上链且质检合格 → 状态变「已完成」；满足条件时也可自动完工。"
          />

          <el-table v-loading="batchLoading" :data="batchList" stripe border style="width: 100%">
            <el-table-column prop="batchId" label="批次号" min-width="160" show-overflow-tooltip />
            <el-table-column prop="orderId" label="订单" min-width="140" show-overflow-tooltip />
            <el-table-column prop="plannedQty" label="计划数量" width="100" align="center" />
            <el-table-column prop="completedQty" label="完成数量" width="100" align="center" />
            <el-table-column prop="status" label="状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="batchStatusType(row.status)" effect="plain">
                  {{ batchStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" align="center">
              <template #default="{ row }">
                <el-button type="primary" link @click="goToEcidTab(row)">管理ECID</el-button>
                <el-button
                  v-if="row.status !== 'COMPLETED'"
                  type="success"
                  link
                  @click="handleBatchComplete(row)"
                >
                  批次完工
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="batchQuery.page"
              v-model:page-size="batchQuery.pageSize"
              :total="batchTotal"
              :page-sizes="[10, 20, 50]"
              background
              layout="total, sizes, prev, pager, next"
              @size-change="fetchBatches"
              @current-change="fetchBatches"
            />
          </div>
        </el-tab-pane>

        <!-- ECID管理 -->
        <el-tab-pane label="ECID管理" name="ecid">
          <!-- Generate form -->
          <el-form :inline="true" :model="ecidGenForm" :rules="ecidGenRules" ref="ecidGenFormRef" class="inline-form">
            <el-form-item label="批次" prop="batchId">
              <el-select
                v-model="ecidGenForm.batchId"
                placeholder="选择批次（表格仅显示该批次设备）"
                filterable
                clearable
                style="width: 280px"
                @change="onEcidBatchFilterChange"
              >
                <el-option
                  v-for="b in batchDropdownList"
                  :key="b.batchId"
                  :label="b.batchId"
                  :value="b.batchId"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="数量" prop="quantity">
              <el-input-number v-model="ecidGenForm.quantity" :min="1" :max="1000" :step="10" />
            </el-form-item>
            <el-form-item label="设备类型" prop="deviceType">
              <el-input v-model="ecidGenForm.deviceType" placeholder="如: Sensor-A" style="width: 160px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="ecidGenerating" @click="handleGenerateEcids">
                生成ECID
              </el-button>
            </el-form-item>
          </el-form>

          <!-- Bulk actions bar -->
          <div class="bulk-bar">
            <el-button
              type="success"
              :disabled="!selectedEcids.length"
              :loading="registering"
              @click="handleBulkRegister"
            >
              批量注册上链 ({{ selectedEcids.length }})
            </el-button>
            <el-button
              type="warning"
              plain
              :disabled="!selectedEcids.length"
              @click="handleExport"
            >
              导出选中
            </el-button>
          </div>

          <el-table
            v-loading="ecidLoading"
            :data="ecidList"
            stripe
            border
            style="width: 100%"
            @selection-change="handleSelectionChange"
          >
            <el-table-column type="selection" width="50" />
            <el-table-column label="ECID" min-width="200">
              <template #default="{ row }">
                <el-tooltip placement="top" :show-after="300">
                  <template #content>
                    <div style="max-width: 360px; word-break: break-all">{{ row.ecid }}</div>
                    <div style="margin-top: 6px; font-size: 12px; opacity: 0.85">点击文字可复制</div>
                  </template>
                  <span
                    class="copy-cell"
                    title="点击复制"
                    @click.stop="copyText(row.ecid, 'ECID')"
                  >{{ row.ecid }}</span>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="订单" min-width="140">
              <template #default="{ row }">
                <el-tooltip placement="top" :show-after="300">
                  <template #content>
                    <div style="max-width: 360px; word-break: break-all">{{ row.orderId }}</div>
                    <div style="margin-top: 6px; font-size: 12px; opacity: 0.85">点击文字可复制</div>
                  </template>
                  <span
                    class="copy-cell"
                    title="点击复制"
                    @click.stop="copyText(row.orderId, '订单号')"
                  >{{ row.orderId }}</span>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="批次" min-width="140">
              <template #default="{ row }">
                <el-tooltip placement="top" :show-after="300">
                  <template #content>
                    <div style="max-width: 360px; word-break: break-all">{{ row.batchId }}</div>
                    <div style="margin-top: 6px; font-size: 12px; opacity: 0.85">点击文字可复制</div>
                  </template>
                  <span
                    class="copy-cell"
                    title="点击复制"
                    @click.stop="copyText(row.batchId, '批次号')"
                  >{{ row.batchId }}</span>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column prop="deviceType" label="设备类型" width="120" align="center" />
            <el-table-column prop="status" label="状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="ecidStatusType(row.status)" effect="plain" size="small">
                  {{ ecidStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="chainRegistered" label="是否上链" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.chainRegistered === 1 ? 'success' : 'info'" effect="plain" size="small">
                  {{ row.chainRegistered === 1 ? '已上链' : '未上链' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.chainRegistered !== 1"
                  type="primary"
                  link
                  size="small"
                  @click="handleSingleRegister(row)"
                >
                  注册上链
                </el-button>
                <el-button
                  type="warning"
                  link
                  size="small"
                  @click="handleSingleExport(row)"
                >
                  导出
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="ecidQuery.page"
              v-model:page-size="ecidQuery.pageSize"
              :total="ecidTotal"
              :page-sizes="[10, 20, 50, 100]"
              background
              layout="total, sizes, prev, pager, next"
              @size-change="fetchEcids"
              @current-change="fetchEcids"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getOrderList,
  createBatch,
  completeProductionBatch,
  getBatchList,
  generateEcids, getEcidList, registerEcids
} from '@/api/manufacturer'
import { useUserStore } from '@/store/user'

const activeTab = ref('batch')
const activeStep = computed(() => {
  if (activeTab.value === 'batch') return 1
  return 2
})

// ================== Shared data ==================
const orderOptions = ref([])

async function fetchOrderOptions() {
  try {
    const { data } = await getOrderList({
      scope: 'mine',
      page: 1,
      pageSize: 200
    })
    orderOptions.value = data.records ?? data.list ?? []
  } catch { /* handled by interceptor */ }
}

// ================== Batch management ==================
const BATCH_STATUS = {
  CREATED: { label: '已创建', type: '' },
  IN_PROGRESS: { label: '进行中', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' }
}
const batchStatusLabel = (s) => BATCH_STATUS[s]?.label || s
const batchStatusType = (s) => BATCH_STATUS[s]?.type ?? 'info'

const batchLoading = ref(false)
const batchCreating = ref(false)
const batchList = ref([])
const batchTotal = ref(0)
const batchFormRef = ref(null)

const batchQuery = reactive({ page: 1, pageSize: 10 })
const batchForm = reactive({ orderId: '', plannedQty: 100 })
const batchRules = {
  orderId: [{ required: true, message: '请选择关联订单', trigger: 'change' }],
  plannedQty: [{ required: true, message: '请输入计划数量', trigger: 'blur' }]
}

async function fetchBatches() {
  batchLoading.value = true
  try {
    const { data } = await getBatchList(batchQuery)
    batchList.value = data.records ?? data.list ?? []
    batchTotal.value = data.total ?? 0
  } catch { /* handled by interceptor */ } finally {
    batchLoading.value = false
  }
}

async function handleCreateBatch() {
  const valid = await batchFormRef.value.validate().catch(() => false)
  if (!valid) return
  batchCreating.value = true
  try {
    await createBatch({ orderId: batchForm.orderId, plannedQty: batchForm.plannedQty })
    ElMessage.success('批次创建成功')
    batchForm.orderId = ''
    batchForm.plannedQty = 100
    fetchBatches()
    fetchBatchDropdownOptions()
  } catch { /* handled by interceptor */ } finally {
    batchCreating.value = false
  }
}

function goToEcidTab(row) {
  ecidGenForm.batchId = row.batchId
  activeTab.value = 'ecid'
  ecidQuery.page = 1
  fetchEcids()
}

async function handleBatchComplete(row) {
  try {
    await ElMessageBox.confirm(
      '确认批次完工？要求：本批次全部 ECID 已链上注册且质检标记为合格。若本订单下所有批次均已完工，订单将自动变为「已完成」。',
      '批次完工',
      { type: 'warning' }
    )
    await completeProductionBatch(row.batchId)
    ElMessage.success('批次已完工')
    fetchBatches()
    fetchBatchDropdownOptions()
    fetchOrderOptions()
  } catch (e) {
    if (e !== 'cancel') {
      /* 业务异常由拦截器提示 */
    }
  }
}

// ================== ECID management ==================
const ECID_STATUS = {
  PRODUCED: { label: '已生成', type: 'info' },
  GENERATED: { label: '已生成', type: 'info' },
  REGISTERED: { label: '已注册', type: 'success' },
  QC_PASS: { label: '质检通过', type: 'success' },
  QC_PASSED: { label: '质检通过', type: 'success' },
  QC_FAILED: { label: '质检不合格', type: 'danger' },
  REJECTED: { label: '不合格作废', type: 'danger' },
  SHIPPED: { label: '已发货', type: '' }
}
const ecidStatusLabel = (s) => ECID_STATUS[s]?.label || s
const ecidStatusType = (s) => ECID_STATUS[s]?.type ?? 'info'

const ecidLoading = ref(false)
const ecidGenerating = ref(false)
const registering = ref(false)
const ecidList = ref([])
const ecidTotal = ref(0)
const selectedEcids = ref([])
const ecidGenFormRef = ref(null)

const ecidQuery = reactive({ page: 1, pageSize: 20 })
const ecidGenForm = reactive({ batchId: '', quantity: 10, deviceType: '' })
const ecidGenRules = {
  batchId: [{ required: true, message: '请选择批次', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }],
  deviceType: [{ required: true, message: '请输入设备类型', trigger: 'blur' }]
}

/** 下拉用：一次拉足批次，避免表格分页导致选项不全 */
const batchDropdownList = ref([])

async function fetchBatchDropdownOptions() {
  try {
    const { data } = await getBatchList({ page: 1, pageSize: 500 })
    batchDropdownList.value = data.records ?? data.list ?? []
  } catch { /* interceptor */ }
}

function onEcidBatchFilterChange() {
  ecidQuery.page = 1
  fetchEcids()
}

async function copyText(value, label = '内容') {
  if (value == null || String(value).trim() === '') {
    ElMessage.warning('无可复制内容')
    return
  }
  const text = String(value)
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`已复制${label}`)
  } catch {
    try {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.left = '-9999px'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
      ElMessage.success(`已复制${label}`)
    } catch {
      ElMessage.error('复制失败，请手动选择文本复制')
    }
  }
}

async function fetchEcids() {
  ecidLoading.value = true
  try {
    const params = { page: ecidQuery.page, pageSize: ecidQuery.pageSize }
    if (ecidGenForm.batchId) params.batchId = ecidGenForm.batchId
    const { data } = await getEcidList(params)
    ecidList.value = data.records ?? data.list ?? []
    ecidTotal.value = data.total ?? 0
  } catch { /* handled by interceptor */ } finally {
    ecidLoading.value = false
  }
}

async function handleGenerateEcids() {
  const valid = await ecidGenFormRef.value.validate().catch(() => false)
  if (!valid) return
  ecidGenerating.value = true
  try {
    await generateEcids({
      batchId: ecidGenForm.batchId,
      quantity: ecidGenForm.quantity,
      deviceType: ecidGenForm.deviceType
    })
    ElMessage.success(`成功生成 ${ecidGenForm.quantity} 个 ECID`)
    fetchEcids()
    fetchBatches()
    fetchBatchDropdownOptions()
  } catch { /* handled by interceptor */ } finally {
    ecidGenerating.value = false
  }
}

function handleSelectionChange(rows) {
  selectedEcids.value = rows
}

async function handleBulkRegister() {
  await ElMessageBox.confirm(
    `确认将 ${selectedEcids.value.length} 个 ECID 注册上链？此操作不可撤销。`,
    '批量注册确认',
    { type: 'warning' }
  )
  registering.value = true
  try {
    const ids = selectedEcids.value.map(r => r.id).filter(Boolean)
    await registerEcids({ ids })
    ElMessage.success('批量注册成功')
    fetchEcids()
  } catch { /* handled by interceptor */ } finally {
    registering.value = false
  }
}

async function handleSingleRegister(row) {
  await ElMessageBox.confirm(`确认将 ECID [${row.ecid}] 注册上链？`, '注册确认', { type: 'warning' })
  registering.value = true
  try {
    await registerEcids({ ids: [row.id] })
    ElMessage.success('注册成功')
    fetchEcids()
  } catch { /* handled by interceptor */ } finally {
    registering.value = false
  }
}

async function downloadEcidCsv(batchId, ecidArr) {
  const token = useUserStore().token
  const params = new URLSearchParams()
  if (batchId) params.set('batchId', batchId)
  if (ecidArr && ecidArr.length) params.set('ecids', ecidArr.join(','))
  const r = await fetch(`/api/manufacturer/production/ecid/export-file?${params}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  if (!r.ok) {
    ElMessage.error('导出失败，请检查批次或登录状态')
    return
  }
  const blob = await r.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'ecid-export.csv'
  a.click()
  URL.revokeObjectURL(url)
}

async function handleExport() {
  try {
    if (selectedEcids.value.length) {
      await downloadEcidCsv(null, selectedEcids.value.map(r => r.ecid))
    } else if (ecidGenForm.batchId) {
      await downloadEcidCsv(ecidGenForm.batchId, null)
    } else {
      ElMessage.warning('请选择表格中的 ECID，或先在上方选择批次后导出整批')
      return
    }
    ElMessage.success('已下载 CSV，可用于车间打码')
  } catch {
    ElMessage.error('导出失败')
  }
}

async function handleSingleExport(row) {
  try {
    await downloadEcidCsv(null, [row.ecid])
    ElMessage.success('已下载 CSV')
  } catch {
    ElMessage.error('导出失败')
  }
}

watch(activeTab, (name) => {
  if (name === 'ecid') {
    fetchBatchDropdownOptions()
  }
})

onMounted(() => {
  fetchOrderOptions()
  fetchBatches()
  fetchBatchDropdownOptions()
  fetchEcids()
})
</script>

<style scoped lang="scss">
.production-container {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .flow-card {
    :deep(.el-card__body) {
      padding: 20px 40px;
    }
  }

  .inline-form {
    margin-bottom: 16px;
    padding-bottom: 16px;
    border-bottom: 1px solid #f0f0f0;
  }

  .batch-hint {
    margin-bottom: 12px;
  }

  .bulk-bar {
    margin-bottom: 12px;
    display: flex;
    gap: 8px;
  }

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }

  .copy-cell {
    display: inline-block;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    vertical-align: bottom;
    cursor: pointer;
    color: var(--el-color-primary);
    border-bottom: 1px dashed transparent;
    transition: border-color 0.15s;

    &:hover {
      border-bottom-color: var(--el-color-primary-light-3);
    }
  }

}
</style>
