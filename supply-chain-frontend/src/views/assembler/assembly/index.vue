<template>
  <div class="app-container">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- Tab: Assembly Batches -->
      <el-tab-pane label="组装批次" name="batch">
        <el-card shadow="never" class="mb-16">
          <el-form :model="batchForm" :rules="batchRules" ref="batchFormRef" inline>
            <el-form-item label="生产订单" prop="orderId">
              <el-select
                v-model="batchForm.orderId"
                placeholder="选择要组装的订单"
                filterable
                clearable
                style="width: 260px"
                :loading="eligibleOrdersLoading"
              >
                <el-option
                  v-for="o in eligibleOrders"
                  :key="o.orderId"
                  :label="`${o.orderId} (${orderStatusLabel(o.status)})`"
                  :value="o.orderId"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="产品型号" prop="productModel">
              <el-input v-model="batchForm.productModel" placeholder="请输入产品型号" />
            </el-form-item>
            <el-form-item label="计划数量" prop="plannedQty">
              <el-input-number v-model="batchForm.plannedQty" :min="1" :max="99999" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="batchSubmitting" @click="handleCreateBatch">
                创建批次
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="mb-16"
          title="组装批次须绑定生产订单；创建整机时请选用该批次对应订单下、且制造商已「放行给组装商」的 ECID。"
        />
        <el-table :data="batchList" v-loading="batchLoading" border stripe>
          <el-table-column prop="batchNo" label="批次号" width="200" />
          <el-table-column prop="orderId" label="生产订单" min-width="160" show-overflow-tooltip />
          <el-table-column prop="productModel" label="产品型号" width="160" />
          <el-table-column prop="plannedQty" label="计划数量" width="100" align="center" />
          <el-table-column prop="completedQty" label="已完成" width="100" align="center" />
          <el-table-column prop="status" label="状态" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="batchStatusType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" min-width="180" />
        </el-table>
        <el-pagination
          class="mt-16"
          v-model:current-page="batchPage.pageNum"
          v-model:page-size="batchPage.pageSize"
          :total="batchPage.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadBatches"
          @current-change="loadBatches"
        />
      </el-tab-pane>

      <!-- Tab: Assembly Records -->
      <el-tab-pane label="组装记录" name="record">
        <el-form :inline="true" class="mb-16 filter-bar" @submit.prevent>
          <el-form-item label="批次筛选">
            <el-select
              v-model="recordFilterBatchNo"
              placeholder="全部批次"
              clearable
              filterable
              style="width: 280px"
              @change="onRecordFilterChange"
            >
              <el-option
                v-for="b in batchList"
                :key="b.batchNo"
                :label="`${b.batchNo} (${b.productModel})`"
                :value="b.batchNo"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button :loading="exportLoading" @click="handleExportRecords">导出 CSV</el-button>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" plain :loading="exportSnXlsxLoading" @click="handleExportSnXlsx">
              一键导出 SN
            </el-button>
          </el-form-item>
        </el-form>
        <el-card shadow="never" class="mb-16">
          <el-form :model="recordForm" :rules="recordRules" ref="recordFormRef" label-width="100px">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="组装批次" prop="batchNo">
                  <el-select v-model="recordForm.batchNo" placeholder="请选择批次" filterable style="width: 100%">
                    <el-option
                      v-for="b in batchList"
                      :key="b.batchNo"
                      :disabled="!isBatchOpenForAssembly(b)"
                      :label="batchOptionLabel(b)"
                      :value="b.batchNo"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="固件版本" prop="firmwareVersion">
                  <el-input v-model="recordForm.firmwareVersion" placeholder="例: v1.2.3" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="整机 SN" prop="sn">
                  <el-input v-model="recordForm.sn" clearable placeholder="选填，不填则系统自动生成" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="24">
                <el-form-item label="ECID列表" prop="ecidList">
                  <el-select
                    v-model="recordForm.ecidList"
                    multiple
                    filterable
                    remote
                    reserve-keyword
                    allow-create
                    default-first-option
                    :remote-method="remoteSearchEcids"
                    :loading="ecidOptionsLoading"
                    placeholder="点击展开或输入关键字筛选；与部件入库验证通过条件一致，也可手动输入后回车"
                    style="width: 100%"
                    @focus="onEcidSelectFocus"
                  >
                    <el-option
                      v-for="item in ecidOptions"
                      :key="item.ecid"
                      :label="ecidOptionLabel(item)"
                      :value="item.ecid"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item>
              <el-button type="primary" :loading="recordSubmitting" @click="handleCreateRecord">
                创建记录
              </el-button>
            </el-form-item>
            <el-form-item v-if="generatedSn" label="生成SN">
              <el-tag type="success" size="large" effect="dark">{{ generatedSn }}</el-tag>
            </el-form-item>
          </el-form>
        </el-card>

        <el-table :data="recordList" v-loading="recordLoading" border stripe>
          <el-table-column prop="sn" label="SN" width="200">
            <template #default="{ row }">
              <span
                v-if="row.sn"
                class="copy-cell"
                title="点击复制"
                @click.stop="copyText(row.sn, 'SN')"
              >{{ row.sn }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column prop="assemblyBatchNo" label="批次号" width="200">
            <template #default="{ row }">
              <span
                v-if="row.assemblyBatchNo"
                class="copy-cell"
                title="点击复制"
                @click.stop="copyText(row.assemblyBatchNo, '批次号')"
              >{{ row.assemblyBatchNo }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column prop="ecidList" label="ECID列表" min-width="200">
            <template #default="{ row }">
              <el-tag v-for="e in (row.ecidList || [])" :key="e" size="small" class="ecid-tag">{{ e }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="firmwareVersion" label="固件版本" width="120" />
          <el-table-column prop="testResult" label="测试结果" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.testResult === 'PASS' ? 'success' : row.testResult === 'FAIL' ? 'danger' : 'info'" size="small">
                {{ row.testResult || '待测' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="链上状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="isOnChain(row) ? 'success' : 'warning'" size="small">
                {{ isOnChain(row) ? '已上链' : '未上链' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="!isOnChain(row)"
                type="primary"
                size="small"
                link
                :loading="row._registering"
                @click="handleRegister(row)"
              >
                注册上链
              </el-button>
              <el-tag v-else type="success" size="small">已上链</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="mt-16"
          v-model:current-page="recordPage.pageNum"
          v-model:page-size="recordPage.pageSize"
          :total="recordPage.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadRecords"
          @current-change="loadRecords"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createAssemblyBatch, getAssemblyBatchList,
  createAssemblyRecord, getAssemblyRecordList,
  registerAssemblyOnChain,
  getAvailableIntakeEcids,
  getEligibleAssemblyOrders,
  exportAssemblySnShipFormat
} from '@/api/assembler'
import { useUserStore } from '@/store/user'

const activeTab = ref('batch')

// ---- Batch ----
const batchFormRef = ref()
const batchForm = reactive({ orderId: '', productModel: '', plannedQty: 1 })
const batchRules = {
  orderId: [{ required: true, message: '请选择生产订单', trigger: 'change' }],
  productModel: [{ required: true, message: '请输入产品型号', trigger: 'blur' }],
  plannedQty: [{ required: true, message: '请输入计划数量', trigger: 'change' }]
}
const eligibleOrders = ref([])
const eligibleOrdersLoading = ref(false)
const batchSubmitting = ref(false)
const batchLoading = ref(false)
const batchList = ref([])
const batchPage = reactive({ pageNum: 1, pageSize: 10, total: 0 })

async function loadEligibleOrders() {
  eligibleOrdersLoading.value = true
  try {
    const res = await getEligibleAssemblyOrders()
    eligibleOrders.value = res.data || []
  } catch {
    eligibleOrders.value = []
  } finally {
    eligibleOrdersLoading.value = false
  }
}

function orderStatusLabel(s) {
  const m = {
    PENDING_ACCEPTANCE: '待接单',
    ACCEPTED: '已接单',
    IN_PRODUCTION: '生产中',
    COMPLETED: '已完成',
    CANCELLED: '已撤销'
  }
  return m[s] || s || '—'
}

async function loadBatches() {
  batchLoading.value = true
  try {
    const res = await getAssemblyBatchList({ pageNum: batchPage.pageNum, pageSize: batchPage.pageSize })
    batchList.value = res.data?.records || res.data?.list || []
    batchPage.total = res.data?.total || 0
  } catch (e) {
    ElMessage.error('加载批次列表失败')
  } finally {
    batchLoading.value = false
  }
}

async function handleCreateBatch() {
  const valid = await batchFormRef.value.validate().catch(() => false)
  if (!valid) return
  batchSubmitting.value = true
  try {
    await createAssemblyBatch({ ...batchForm })
    ElMessage.success('创建批次成功')
    batchFormRef.value.resetFields()
    batchForm.orderId = ''
    batchForm.plannedQty = 1
    loadBatches()
  } catch (e) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    batchSubmitting.value = false
  }
}

function batchStatusType(status) {
  const m = { IN_PROGRESS: '', COMPLETED: 'success', CANCELLED: 'danger' }
  return m[status] ?? 'info'
}

function isBatchOpenForAssembly(b) {
  if (!b || b.status === 'COMPLETED' || b.status === 'CANCELLED') return false
  const done = b.completedQty ?? 0
  const plan = b.plannedQty
  if (plan != null && done >= plan) return false
  return true
}

function batchOptionLabel(b) {
  if (!b) return ''
  const ord = b.orderId ? ` · ${b.orderId}` : ''
  const suffix = !isBatchOpenForAssembly(b) ? ' — 已满或未开放' : ''
  return `${b.batchNo} (${b.productModel || '—'})${ord}${suffix}`
}

const assemblyBatchOrderId = computed(() => {
  const b = batchList.value.find(x => x.batchNo === recordForm.batchNo)
  return b?.orderId || ''
})

// ---- Records ----
const recordFormRef = ref()
const recordForm = reactive({ batchNo: '', sn: '', ecidList: [], firmwareVersion: '' })
const recordRules = {
  batchNo: [{ required: true, message: '请选择批次', trigger: 'change' }],
  ecidList: [{ required: true, type: 'array', min: 1, message: '请添加至少一个 ECID', trigger: 'change' }],
  firmwareVersion: [{ required: true, message: '请输入固件版本', trigger: 'blur' }]
}
const recordSubmitting = ref(false)
const recordLoading = ref(false)
const recordList = ref([])
const recordPage = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const recordFilterBatchNo = ref('')
const generatedSn = ref('')
const exportLoading = ref(false)
const exportSnXlsxLoading = ref(false)

const ecidOptions = ref([])
const ecidOptionsLoading = ref(false)
let ecidOptionsPrimed = false

function ecidOptionLabel(item) {
  if (!item?.ecid) return ''
  const t = item.deviceType || '—'
  const b = item.batchId ? ` · ${item.batchId}` : ''
  return `${item.ecid} (${t}${b})`
}

async function remoteSearchEcids(query) {
  ecidOptionsLoading.value = true
  try {
    const params = {
      keyword: (query || '').trim(),
      pageNum: 1,
      pageSize: 100
    }
    if (assemblyBatchOrderId.value) {
      params.orderId = assemblyBatchOrderId.value
    }
    const res = await getAvailableIntakeEcids(params)
    ecidOptions.value = res.data?.records || []
  } catch {
    ecidOptions.value = []
  } finally {
    ecidOptionsLoading.value = false
  }
}

async function onEcidSelectFocus() {
  if (!ecidOptionsPrimed) {
    ecidOptionsPrimed = true
    await remoteSearchEcids('')
  }
}

function refreshEcidOptions() {
  ecidOptionsPrimed = false
  remoteSearchEcids('')
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

function normalizeEcidList(r) {
  let ecids = []
  if (Array.isArray(r.ecidList)) {
    ecids = r.ecidList
  } else if (typeof r.ecidList === 'string' && r.ecidList.trim()) {
    try {
      ecids = JSON.parse(r.ecidList)
    } catch {
      ecids = []
    }
  }
  return ecids
}

function isOnChain(row) {
  return row.status === 'ON_CHAIN' || row.chainRegistered === 1
}

function onRecordFilterChange() {
  recordPage.pageNum = 1
  loadRecords()
}

async function loadRecords() {
  recordLoading.value = true
  try {
    const params = { pageNum: recordPage.pageNum, pageSize: recordPage.pageSize }
    if (recordFilterBatchNo.value) {
      params.assemblyBatchNo = recordFilterBatchNo.value
    }
    const res = await getAssemblyRecordList(params)
    recordList.value = (res.data?.records || res.data?.list || []).map(r => ({
      ...r,
      ecidList: normalizeEcidList(r),
      _registering: false
    }))
    recordPage.total = res.data?.total || 0
  } catch (e) {
    ElMessage.error('加载组装记录失败')
  } finally {
    recordLoading.value = false
  }
}

async function handleCreateRecord() {
  const valid = await recordFormRef.value.validate().catch(() => false)
  if (!valid) return
  recordSubmitting.value = true
  try {
    const res = await createAssemblyRecord({ ...recordForm })
    generatedSn.value = res.data?.sn || ''
    ElMessage.success('创建组装记录成功')
    recordFormRef.value.resetFields()
    recordForm.ecidList = []
    recordForm.sn = ''
    refreshEcidOptions()
    loadRecords()
    loadBatches()
  } catch (e) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    recordSubmitting.value = false
  }
}

async function handleExportRecords() {
  exportLoading.value = true
  try {
    const token = useUserStore().token
    const params = new URLSearchParams()
    if (recordFilterBatchNo.value) params.set('assemblyBatchNo', recordFilterBatchNo.value)
    const r = await fetch(`/api/assembler/assembly/record/export?${params}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    })
    if (!r.ok) {
      ElMessage.error('导出失败，请检查登录状态')
      return
    }
    const blob = await r.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'assembly-records.csv'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  } finally {
    exportLoading.value = false
  }
}

async function handleExportSnXlsx() {
  exportSnXlsxLoading.value = true
  try {
    const params = {}
    if (recordFilterBatchNo.value) params.assemblyBatchNo = recordFilterBatchNo.value
    const blob = await exportAssemblySnShipFormat(params)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'assembly-sn-ship-template.xlsx'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已导出 Excel（表头 SN，与批量发货模板一致）')
  } catch {
    ElMessage.error('导出失败')
  } finally {
    exportSnXlsxLoading.value = false
  }
}

async function handleRegister(row) {
  row._registering = true
  try {
    await registerAssemblyOnChain(row.id)
    ElMessage.success('注册上链成功')
    row.status = 'ON_CHAIN'
    row.chainRegistered = 1
  } catch (e) {
    ElMessage.error(e.message || '上链失败')
  } finally {
    row._registering = false
  }
}

watch(activeTab, (name) => {
  if (name === 'record') {
    onEcidSelectFocus()
  }
})

watch(
  () => recordForm.batchNo,
  () => {
    ecidOptionsPrimed = false
    if (activeTab.value === 'record') {
      remoteSearchEcids('')
    }
  }
)

onMounted(() => {
  loadEligibleOrders()
  loadBatches()
  loadRecords()
})
</script>

<style scoped lang="scss">
.app-container {
  padding: 20px;
}
.mb-16 {
  margin-bottom: 16px;
}
.filter-bar {
  margin-bottom: 0;
}
.mt-16 {
  margin-top: 16px;
}
.ecid-tag {
  margin: 2px 4px 2px 0;
}
.copy-cell {
  cursor: pointer;
  color: var(--el-color-primary);
  word-break: break-all;
  &:hover {
    text-decoration: underline;
  }
}
</style>
