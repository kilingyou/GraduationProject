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

    <el-card v-if="routeOrderId" shadow="never" class="context-order-card">
      <div class="context-order-bar">
        <div class="context-order-main">
          <span class="context-label">当前订单</span>
          <el-tag type="primary" effect="dark" size="large">{{ routeOrderId }}</el-tag>
          <template v-if="contextSummary">
            <span class="context-meta">
              批次 {{ contextSummary.batchCount }} · ECID {{ contextSummary.ecidTotal }} · 已上链
              {{ contextSummary.ecidOnChainCount }} · 已放行组装 {{ contextSummary.ecidReleasedToAssemblerCount }}
            </span>
          </template>
        </div>
        <div class="context-order-actions">
          <el-button type="primary" link :loading="contextSummaryLoading" @click="reloadContextSummary">刷新摘要</el-button>
          <el-button type="success" plain size="small" @click="goQualityWithOrder">去质检</el-button>
          <el-button size="small" @click="clearOrderContext">清除定位</el-button>
        </div>
      </div>
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
                @change="onBatchOrderChange"
              >
                <el-option
                  v-for="o in orderOptions"
                  :key="o.orderId"
                  :label="`${o.orderId} (${o.bomName})`"
                  :value="o.orderId"
                />
              </el-select>
            </el-form-item>
            <el-form-item v-if="bomItemOptions.length" label="BOM子件" prop="bomItemId">
              <el-select
                v-model="batchForm.bomItemId"
                placeholder="选择要投产的子件行"
                filterable
                style="width: 280px"
              >
                <el-option
                  v-for="it in bomItemOptions"
                  :key="it.id"
                  :label="`${it.partNumber || '-'} ${it.partName || ''}（×${it.quantity || 1}/套）`"
                  :value="it.id"
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
            title="每个批次绑定一条 BOM 子件行：计划数量不得超过「订单套数×该行每套用量」减去该子件已建批计划之和。生成 ECID 时设备类型与链上 devType 由子件料号/名称推导。组装商再将多个子件 ECID 绑定到整机 SN。"
          />

          <el-table v-loading="batchLoading" :data="batchList" stripe border style="width: 100%">
            <el-table-column prop="batchId" label="批次号" min-width="160" show-overflow-tooltip />
            <el-table-column prop="orderId" label="订单" min-width="140" show-overflow-tooltip />
            <el-table-column label="子件(BOM行)" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.bomPartSummary || (row.bomItemId ? `#${row.bomItemId}` : '-') }}
              </template>
            </el-table-column>
            <el-table-column prop="plannedQty" label="计划数量" width="100" align="center" />
            <el-table-column prop="completedQty" label="完成数量" width="100" align="center" />
            <el-table-column prop="status" label="状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="batchStatusType(row.status)" effect="plain">
                  {{ batchStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="300" align="center">
              <template #default="{ row }">
                <el-button type="primary" link @click="goToEcidTab(row)">管理ECID</el-button>
                <el-button
                  type="warning"
                  link
                  :loading="releasingBatchId === row.batchId"
                  @click="handleReleaseBatchToAssembler(row)"
                >
                  放行本批次
                </el-button>
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
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            class="batch-hint"
            title="上链注册前须在「质检」页：上传报告（绑定报告哈希）并标记合格；本页生成 ECID 数量累计不得超过批次计划数。组装商领料前须在本页或批次列表对「已质检合格且已上链」的部件执行「放行给组装商」。"
          />
          <!-- Generate form -->
          <el-form :inline="true" :model="ecidGenForm" :rules="ecidGenRules" ref="ecidGenFormRef" class="inline-form">
            <el-form-item label="批次" prop="batchId">
              <el-select
                v-model="ecidGenForm.batchId"
                placeholder="按批次缩小列表（可与下方筛选组合）"
                filterable
                clearable
                style="width: 280px"
                @change="onEcidBatchFilterChange"
              >
                <el-option
                  v-for="b in batchDropdownList"
                  :key="b.batchId"
                  :label="b.bomPartSummary ? `${b.batchId} (${b.bomPartSummary})` : b.batchId"
                  :value="b.batchId"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="数量" prop="quantity">
              <el-input-number v-model="ecidGenForm.quantity" :min="1" :max="1000" :step="10" />
            </el-form-item>
            <el-form-item label="设备类型" prop="deviceType">
              <el-input
                v-model="ecidGenForm.deviceType"
                :placeholder="selectedBatchHasBomLine ? '子件批次将自动用料号+名称' : '如: Sensor-A'"
                :disabled="selectedBatchHasBomLine"
                style="width: 200px"
              />
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
            <el-button
              type="warning"
              :disabled="!selectedEcids.length"
              :loading="releasingSelected"
              @click="handleReleaseSelectedToAssembler"
            >
              放行给组装商 ({{ selectedEcids.length }})
            </el-button>
          </div>

          <el-form :inline="true" class="ecid-filter-bar" @submit.prevent="handleEcidFilterSearch">
            <el-form-item label="关键字">
              <el-input
                v-model="ecidQuery.keyword"
                clearable
                placeholder="ECID / 订单号 / 批次号 / 类型"
                style="width: 220px"
                @keyup.enter="handleEcidFilterSearch"
              />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="ecidQuery.status" placeholder="全部" clearable style="width: 140px">
                <el-option label="已生成" value="PRODUCED" />
                <el-option label="质检通过" value="QC_PASS" />
                <el-option label="质检不合格" value="QC_FAILED" />
                <el-option label="不合格作废" value="REJECTED" />
                <el-option label="已组装" value="ASSEMBLED" />
              </el-select>
            </el-form-item>
            <el-form-item label="上链">
              <el-select v-model="ecidQuery.chainFlag" placeholder="全部" clearable style="width: 120px">
                <el-option label="已上链" value="1" />
                <el-option label="未上链" value="0" />
              </el-select>
            </el-form-item>
            <el-form-item label="组装放行">
              <el-select v-model="ecidQuery.releasedFlag" placeholder="全部" clearable style="width: 120px">
                <el-option label="已放行" value="1" />
                <el-option label="待放行" value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleEcidFilterSearch">查询</el-button>
              <el-button @click="handleEcidFilterReset">重置</el-button>
            </el-form-item>
          </el-form>

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
            <el-table-column label="子件" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.bomPartSummary || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="deviceType" label="链上类型" width="140" show-overflow-tooltip />
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
            <el-table-column label="组装放行" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.releasedToAssembler === 1 ? 'success' : 'warning'" effect="plain" size="small">
                  {{ row.releasedToAssembler === 1 ? '已放行' : '待放行' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="260" align="center" fixed="right">
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
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getOrderList,
  createBatch,
  completeProductionBatch,
  getBatchList,
  generateEcids,
  getEcidList,
  getOrderBomItemsForProduction,
  registerEcids,
  releasePartsToAssembler,
  getOrderProductionSummary
} from '@/api/manufacturer'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()

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

const batchQuery = reactive({ page: 1, pageSize: 10, orderId: undefined })
const bomItemOptions = ref([])

const batchForm = reactive({ orderId: '', bomItemId: null, plannedQty: 100 })
const batchRules = {
  orderId: [{ required: true, message: '请选择关联订单', trigger: 'change' }],
  bomItemId: [
    {
      validator: (_r, v, cb) => {
        if (bomItemOptions.value.length && (v == null || v === '')) {
          cb(new Error('请选择 BOM 子件行'))
        } else {
          cb()
        }
      },
      trigger: 'change'
    }
  ],
  plannedQty: [{ required: true, message: '请输入计划数量', trigger: 'blur' }]
}

async function onBatchOrderChange(orderId) {
  batchForm.bomItemId = null
  bomItemOptions.value = []
  if (!orderId) return
  try {
    const { data } = await getOrderBomItemsForProduction(orderId)
    bomItemOptions.value = Array.isArray(data) ? data : []
  } catch {
    bomItemOptions.value = []
  }
}

async function fetchBatches() {
  batchLoading.value = true
  try {
    const params = { page: batchQuery.page, pageSize: batchQuery.pageSize }
    if (batchQuery.orderId) params.orderId = batchQuery.orderId
    const { data } = await getBatchList(params)
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
    const payload = { orderId: batchForm.orderId, plannedQty: batchForm.plannedQty }
    if (batchForm.bomItemId != null) {
      payload.bomItemId = batchForm.bomItemId
    }
    await createBatch(payload)
    ElMessage.success('批次创建成功')
    batchForm.bomItemId = null
    bomItemOptions.value = []
    batchForm.plannedQty = 100
    if (routeOrderId.value) {
      batchForm.orderId = routeOrderId.value
      onBatchOrderChange(routeOrderId.value)
    } else {
      batchForm.orderId = ''
    }
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
      '确认批次完工？要求：每台设备要么「质检合格且已上链」，要么「质检不合格但已完成退货/销毁闭环且处置已上链」。若本订单下所有批次均已完工，订单将自动变为「已完成」。',
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

async function handleReleaseBatchToAssembler(row) {
  if (!row?.batchId) return
  try {
    await ElMessageBox.confirm(
      '将本批次中「质检合格且已上链、尚未放行」的部件全部标记为已放行给组装商，组装商即可在系统中领料。',
      '放行给组装商',
      { type: 'warning' }
    )
    releasingBatchId.value = row.batchId
    const { data } = await releasePartsToAssembler({ batchId: row.batchId })
    const n = data?.released ?? 0
    if (n === 0) {
      ElMessage.warning('无可放行部件（需质检合格、已上链且尚未放行）')
    } else {
      ElMessage.success(`已放行 ${n} 条`)
    }
    fetchEcids()
    fetchBatches()
  } catch (e) {
    if (e !== 'cancel') {
      /* 拦截器 */
    }
  } finally {
    releasingBatchId.value = ''
  }
}

async function handleReleaseSelectedToAssembler() {
  if (!selectedEcids.value.length) return
  try {
    await ElMessageBox.confirm(
      `将选中的 ${selectedEcids.value.length} 条中符合条件的部件标记为已放行给组装商（须质检合格、已上链）。`,
      '放行给组装商',
      { type: 'warning' }
    )
    releasingSelected.value = true
    const ecids = selectedEcids.value.map((r) => r.ecid).filter(Boolean)
    const { data } = await releasePartsToAssembler({ ecids })
    const n = data?.released ?? 0
    if (n === 0) {
      ElMessage.warning('无符合条件可放行的部件')
    } else {
      ElMessage.success(`已放行 ${n} 条`)
    }
    fetchEcids()
  } catch (e) {
    if (e !== 'cancel') {
      /* 拦截器 */
    }
  } finally {
    releasingSelected.value = false
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
const releasingSelected = ref(false)
const releasingBatchId = ref('')
const ecidList = ref([])
const ecidTotal = ref(0)
const selectedEcids = ref([])
const ecidGenFormRef = ref(null)

const ecidQuery = reactive({
  page: 1,
  pageSize: 20,
  orderId: undefined,
  keyword: '',
  status: '',
  chainFlag: '',
  releasedFlag: ''
})
const ecidGenForm = reactive({ batchId: '', quantity: 10, deviceType: '' })

/** 下拉用：一次拉足批次，避免表格分页导致选项不全 */
const batchDropdownList = ref([])

const ecidGenRules = {
  batchId: [{ required: true, message: '请选择批次', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }],
  deviceType: [
    {
      validator: (_r, v, cb) => {
        const b = batchDropdownList.value.find((x) => x.batchId === ecidGenForm.batchId)
        if (b && b.bomItemId) {
          cb()
          return
        }
        if (!v || !String(v).trim()) {
          cb(new Error('非子件批次须填写设备类型'))
        } else {
          cb()
        }
      },
      trigger: 'blur'
    }
  ]
}

const selectedBatchHasBomLine = computed(() => {
  const b = batchDropdownList.value.find((x) => x.batchId === ecidGenForm.batchId)
  return !!(b && b.bomItemId)
})

watch(
  () => ecidGenForm.batchId,
  () => {
    if (selectedBatchHasBomLine.value) {
      ecidGenForm.deviceType = ''
    }
  }
)

async function fetchBatchDropdownOptions() {
  try {
    const params = { page: 1, pageSize: 500 }
    if (batchQuery.orderId) params.orderId = batchQuery.orderId
    const { data } = await getBatchList(params)
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

function handleEcidFilterSearch() {
  ecidQuery.page = 1
  fetchEcids()
}

function handleEcidFilterReset() {
  ecidQuery.keyword = ''
  ecidQuery.status = ''
  ecidQuery.chainFlag = ''
  ecidQuery.releasedFlag = ''
  ecidQuery.page = 1
  fetchEcids()
}

async function fetchEcids() {
  ecidLoading.value = true
  try {
    const params = { page: ecidQuery.page, pageSize: ecidQuery.pageSize }
    if (ecidGenForm.batchId) params.batchId = ecidGenForm.batchId
    if (ecidQuery.orderId) params.orderId = ecidQuery.orderId
    const kw = (ecidQuery.keyword || '').trim()
    if (kw) params.keyword = kw
    if (ecidQuery.status) params.status = ecidQuery.status
    if (ecidQuery.chainFlag === '0' || ecidQuery.chainFlag === '1') {
      params.chainRegistered = Number(ecidQuery.chainFlag)
    }
    if (ecidQuery.releasedFlag === '0' || ecidQuery.releasedFlag === '1') {
      params.releasedToAssembler = Number(ecidQuery.releasedFlag)
    }
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
    const b = batchDropdownList.value.find((x) => x.batchId === ecidGenForm.batchId)
    const payload = {
      batchId: ecidGenForm.batchId,
      quantity: ecidGenForm.quantity
    }
    if (!b?.bomItemId) {
      payload.deviceType = ecidGenForm.deviceType
    }
    await generateEcids(payload)
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

const routeOrderId = computed(() => {
  const q = route.query.orderId
  if (Array.isArray(q)) return (q[0] || '').trim()
  return typeof q === 'string' ? q.trim() : ''
})

const routeBatchId = computed(() => {
  const q = route.query.batchId
  if (Array.isArray(q)) return (q[0] || '').trim()
  return typeof q === 'string' ? q.trim() : ''
})

const contextSummary = ref(null)
const contextSummaryLoading = ref(false)

async function loadContextSummary(orderId) {
  if (!orderId) {
    contextSummary.value = null
    return
  }
  contextSummaryLoading.value = true
  try {
    const { data } = await getOrderProductionSummary(orderId)
    contextSummary.value = data
  } catch {
    contextSummary.value = null
  } finally {
    contextSummaryLoading.value = false
  }
}

function reloadContextSummary() {
  if (routeOrderId.value) loadContextSummary(routeOrderId.value)
}

function clearOrderContext() {
  router.replace({ name: 'Production', query: {} })
}

function goQualityWithOrder() {
  if (!routeOrderId.value) return
  router.push({ name: 'MfgQuality', query: { orderId: routeOrderId.value } })
}

function syncRouteToProductionForm() {
  const oid = routeOrderId.value
  const bid = routeBatchId.value
  if (oid) {
    batchForm.orderId = oid
    onBatchOrderChange(oid)
    batchQuery.orderId = oid
    ecidQuery.orderId = oid
  } else {
    batchForm.orderId = ''
    batchForm.bomItemId = null
    bomItemOptions.value = []
    batchQuery.orderId = undefined
    ecidQuery.orderId = undefined
  }
  if (bid) {
    ecidGenForm.batchId = bid
    activeTab.value = 'ecid'
  }
  fetchBatches()
  fetchEcids()
  fetchBatchDropdownOptions()
  if (oid) {
    loadContextSummary(oid)
  } else {
    contextSummary.value = null
  }
}

watch(
  () => `${route.query.orderId || ''}|${route.query.batchId || ''}`,
  () => syncRouteToProductionForm()
)

watch(activeTab, (name) => {
  if (name === 'ecid') {
    fetchBatchDropdownOptions()
  }
})

onMounted(() => {
  fetchOrderOptions()
  fetchBatchDropdownOptions()
  syncRouteToProductionForm()
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

  .context-order-card {
    :deep(.el-card__body) {
      padding: 12px 16px;
    }
  }

  .context-order-bar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .context-order-main {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px;
  }

  .context-label {
    font-weight: 600;
    color: #606266;
  }

  .context-meta {
    font-size: 13px;
    color: #909399;
  }

  .context-order-actions {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }

  .inline-form {
    margin-bottom: 16px;
    padding-bottom: 16px;
    border-bottom: 1px solid #f0f0f0;
  }

  .batch-hint {
    margin-bottom: 12px;
  }

  .ecid-filter-bar {
    margin-bottom: 12px;
    padding: 12px 12px 4px;
    background: #fafafa;
    border-radius: 8px;
    border: 1px solid #ebeef5;
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
