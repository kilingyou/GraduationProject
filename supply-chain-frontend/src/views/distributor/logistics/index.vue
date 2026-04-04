<template>
  <div class="app-container">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-16"
      title="流通渠道职责：在货权发生转移时登记物流单号、时间与接收方，系统会锚定 TRANSFER_EVENT / TRANSFER_RECEIVE；组装商将整机交给下游分销商时，同样在此发货并由对方「确认收货」完成货权交接。"
    />

    <!-- Action Cards -->
    <el-row :gutter="20" class="mb-16">
      <el-col :span="12">
        <el-card shadow="hover" class="action-card" @click="shipDialogVisible = true">
          <el-icon :size="36" color="#409eff"><Van /></el-icon>
          <div class="action-card__text">
            <h3>扫码发货</h3>
            <p>创建物流发货单</p>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="action-card" @click="receiveDialogVisible = true">
          <el-icon :size="36" color="#67c23a"><Box /></el-icon>
          <div class="action-card__text">
            <h3>确认收货</h3>
            <p>确认物流接收</p>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="action-card" @click="batchDialogVisible = true">
          <el-icon :size="36" color="#e6a23c"><List /></el-icon>
          <div class="action-card__text">
            <h3>批量发货</h3>
            <p>Excel 装箱单（表头 SN）</p>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Track Section -->
    <el-card shadow="hover" class="mb-16">
      <template #header>
        <div class="card-header">
          <el-icon><Location /></el-icon>
          <span>物流追踪</span>
        </div>
      </template>
      <el-form inline>
        <el-form-item>
          <el-input v-model="trackSn" placeholder="输入 SN 追踪物流" clearable style="width: 300px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="trackLoading" @click="handleTrack">查询</el-button>
        </el-form-item>
      </el-form>
      <el-timeline v-if="trackEvents.length" class="mt-16">
        <el-timeline-item
          v-for="(evt, idx) in trackEvents"
          :key="idx"
          :timestamp="evt.time"
          :type="idx === 0 ? 'primary' : 'info'"
          placement="top"
        >
          <el-card shadow="never">
            <p><strong>{{ evt.type }}</strong> — {{ evt.description }}</p>
            <p v-if="evt.logisticsNo">物流单号：{{ evt.logisticsNo }}</p>
            <p v-if="evt.logisticsCompany">物流公司：{{ evt.logisticsCompany }}</p>
            <p v-if="evt.assemblyBatchNo">组装批次：{{ evt.assemblyBatchNo }}</p>
            <p v-if="evt.txHash" class="hash-line">链上摘要 Tx：{{ evt.txHash }}</p>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <!-- Transfer List -->
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <el-icon><List /></el-icon>
          <span>流转记录</span>
        </div>
      </template>
      <el-table :data="transferList" v-loading="listLoading" border stripe>
        <el-table-column prop="trackingNumber" label="物流单号" width="180" />
        <el-table-column prop="sn" label="产品SN" width="180" />
        <el-table-column prop="batchNo" label="组装批次" width="160" show-overflow-tooltip />
        <el-table-column prop="senderId" label="发货方ID" width="100" />
        <el-table-column prop="receiverId" label="收货方ID" width="100" />
        <el-table-column prop="transferType" label="流转类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ transferTypeLabel(row.transferType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="transferStatusType(row.status)" size="small">{{ transferStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="shipTime" label="发货时间" width="180" />
        <el-table-column prop="actualArrival" label="到达时间" width="180" />
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canOneClickReceive(row)"
              type="success"
              size="small"
              link
              :loading="row._receiving"
              @click="handleOneClickReceive(row)"
            >
              一键收货
            </el-button>
            <span v-else class="op-placeholder">—</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="mt-16"
        v-model:current-page="page.page"
        v-model:page-size="page.size"
        :total="page.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadTransfers"
        @current-change="loadTransfers"
      />
    </el-card>

    <!-- Ship Dialog -->
    <el-dialog v-model="shipDialogVisible" title="扫码发货" width="520px" destroy-on-close>
      <el-form :model="shipForm" :rules="shipRules" ref="shipFormRef" label-width="100px">
        <el-form-item label="产品SN" prop="sn">
          <el-input v-model="shipForm.sn" placeholder="请输入产品 SN" />
        </el-form-item>
        <el-form-item label="物流公司" prop="logisticsCompany">
          <el-input v-model="shipForm.logisticsCompany" placeholder="例: 顺丰速运" />
        </el-form-item>
        <el-form-item label="物流单号" prop="trackingNo">
          <el-input v-model="shipForm.trackingNo" placeholder="请输入物流单号" />
        </el-form-item>
        <el-form-item label="流转场景" prop="transferType">
          <el-select v-model="shipForm.transferType" placeholder="选择类型" style="width: 100%">
            <el-option label="标准发货 (SHIP)" value="SHIP" />
            <el-option label="渠道 → 制造商" value="CHANNEL_TO_MFG" />
            <el-option label="→ 组装商" value="TO_ASSEMBLER" />
            <el-option label="→ 分销商" value="TO_DISTRIBUTOR" />
            <el-option label="→ 零售/二级渠道" value="TO_RETAIL" />
          </el-select>
        </el-form-item>
        <el-form-item label="接收方" prop="receiverId">
          <el-select
            placeholder="快速选择对接企业（可选）"
            filterable
            clearable
            style="width: 100%"
            @change="onShipReceiverPick"
          >
            <el-option
              v-for="u in partnerUserOptions"
              :key="u.id"
              :label="partnerLabel(u)"
              :value="u.id"
            />
          </el-select>
          <el-input-number
            v-model="shipForm.receiverId"
            :min="1"
            :controls="false"
            placeholder="接收方用户 ID（必填，可与上栏联动）"
            style="width: 100%; margin-top: 8px"
          />
          <div class="field-hint">用户 ID 可在「系统管理-用户管理」中查看；列表聚合分销商/组装商/制造商账号。</div>
        </el-form-item>
        <el-form-item label="发货时间" prop="shipTime">
          <el-date-picker
            v-model="shipForm.shipTime"
            type="datetime"
            placeholder="选择发货时间"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="shipSubmitting" @click="handleShip">确认发货</el-button>
      </template>
    </el-dialog>

    <!-- Batch ship -->
    <el-dialog v-model="batchDialogVisible" title="Excel 批量发货" width="560px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="物流公司" required>
          <el-input v-model="batchForm.logisticsCompany" placeholder="与单件发货一致" />
        </el-form-item>
        <el-form-item label="物流单号" required>
          <el-input v-model="batchForm.trackingNo" placeholder="整批共用单号" />
        </el-form-item>
        <el-form-item label="流转场景">
          <el-select v-model="batchForm.transferType" style="width: 100%">
            <el-option label="标准发货 (SHIP)" value="SHIP" />
            <el-option label="→ 分销商" value="TO_DISTRIBUTOR" />
            <el-option label="→ 零售/二级渠道" value="TO_RETAIL" />
          </el-select>
        </el-form-item>
        <el-form-item label="接收方" required>
          <el-select
            placeholder="快速选择"
            filterable
            clearable
            style="width: 100%"
            @change="onBatchReceiverPick"
          >
            <el-option
              v-for="u in partnerUserOptions"
              :key="u.id"
              :label="partnerLabel(u)"
              :value="u.id"
            />
          </el-select>
          <el-input-number
            v-model="batchForm.receiverId"
            :min="1"
            :controls="false"
            style="width: 100%; margin-top: 8px"
            placeholder="接收方用户 ID"
          />
        </el-form-item>
        <el-form-item label="装箱单">
          <el-upload
            :auto-upload="false"
            :limit="1"
            accept=".xlsx,.xls"
            :on-change="f => (batchFile = f.raw)"
            :on-exceed="() => ElMessage.warning('只能上传一个文件')"
          >
            <el-button>选择 Excel</el-button>
          </el-upload>
          <el-button type="primary" link style="margin-left: 8px" @click="downloadBatchTpl">下载模板</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="handleBatchShip">提交批量发货</el-button>
      </template>
    </el-dialog>

    <!-- Receive Dialog -->
    <el-dialog v-model="receiveDialogVisible" title="确认收货" width="420px" destroy-on-close>
      <el-form :model="receiveForm" :rules="receiveRules" ref="receiveFormRef" label-width="100px">
        <el-form-item label="物流单号" prop="trackingNo">
          <el-input v-model="receiveForm.trackingNo" placeholder="物流单号或产品 SN" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="receiveDialogVisible = false">取消</el-button>
        <el-button type="success" :loading="receiveSubmitting" @click="handleReceive">确认收货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Van, Box, Location, List } from '@element-plus/icons-vue'
import {
  shipProducts, receiveProducts, getTransferList, trackProduct,
  shipBatchProducts, downloadSnShipTemplate
} from '@/api/distributor'
import { getUserList } from '@/api/system'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const partnerUserOptions = ref([])

function partnerLabel(u) {
  const ent = u.enterpriseName || u.contactPerson || '—'
  return `${u.username} · ${ent} (ID ${u.id})`
}

function transferTypeLabel(tt) {
  const m = {
    SHIP: '发货',
    CHANNEL_TO_MFG: '渠道→制造商',
    TO_ASSEMBLER: '→组装商',
    TO_DISTRIBUTOR: '→分销商',
    TO_RETAIL: '→零售'
  }
  return m[tt] || tt || '—'
}

async function loadPartnerUsers() {
  const roles = ['distributor', 'assembler', 'manufacturer']
  const byId = new Map()
  const selfId = userStore.userId
  try {
    for (const roleKey of roles) {
      const res = await getUserList({ page: 1, size: 500, roleKey })
      const rows = res.data?.records || res.data?.list || []
      for (const u of rows) {
        if (u.id != null && u.id !== selfId && !byId.has(u.id)) {
          byId.set(u.id, u)
        }
      }
    }
    partnerUserOptions.value = Array.from(byId.values())
  } catch {
    partnerUserOptions.value = []
  }
}

function onShipReceiverPick(id) {
  if (id != null && id !== '') shipForm.receiverId = id
}

function onBatchReceiverPick(id) {
  if (id != null && id !== '') batchForm.receiverId = id
}

// ---- Transfer List ----
const listLoading = ref(false)
const transferList = ref([])
const page = reactive({ page: 1, size: 10, total: 0 })

async function loadTransfers() {
  listLoading.value = true
  try {
    const res = await getTransferList({ page: page.page, size: page.size })
    transferList.value = (res.data?.records || res.data?.list || []).map(r => ({
      ...r,
      _receiving: false
    }))
    page.total = res.data?.total || 0
  } catch {
    ElMessage.error('加载流转记录失败')
  } finally {
    listLoading.value = false
  }
}

function transferStatusType(s) {
  const m = { PENDING: 'info', RECEIVED: 'success', IN_TRANSIT: 'warning', ANOMALY: 'danger' }
  return m[s] ?? 'info'
}
function transferStatusLabel(s) {
  const m = { PENDING: '待处理', RECEIVED: '已收货', IN_TRANSIT: '在途', ANOMALY: '异常' }
  return m[s] ?? s
}

function canOneClickReceive(row) {
  const uid = userStore.userId
  if (uid == null || row?.receiverId == null) return false
  if (Number(uid) !== Number(row.receiverId)) return false
  const s = row.status
  return s === 'IN_TRANSIT' || s === 'PENDING'
}

async function handleOneClickReceive(row) {
  try {
    await ElMessageBox.confirm(
      `确认收货？运单 ${row.trackingNumber || '—'}，SN ${row.sn || '—'}`,
      '一键收货',
      { type: 'warning', confirmButtonText: '确认收货', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  row._receiving = true
  try {
    await receiveProducts({ transferId: row.id })
    ElMessage.success('收货成功，货权已更新')
    loadTransfers()
  } catch (e) {
    ElMessage.error(e.message || '收货失败')
  } finally {
    row._receiving = false
  }
}

// ---- Ship ----
const shipDialogVisible = ref(false)
const shipFormRef = ref()
const shipForm = reactive({
  sn: '',
  logisticsCompany: '',
  trackingNo: '',
  receiverId: undefined,
  shipTime: null,
  transferType: 'SHIP'
})
const shipRules = {
  sn: [{ required: true, message: '请输入 SN', trigger: 'blur' }],
  logisticsCompany: [{ required: true, message: '请输入物流公司', trigger: 'blur' }],
  trackingNo: [{ required: true, message: '请输入物流单号', trigger: 'blur' }],
  receiverId: [{ required: true, message: '请输入接收方用户ID', trigger: 'change' }]
}
const shipSubmitting = ref(false)

async function handleShip() {
  const valid = await shipFormRef.value.validate().catch(() => false)
  if (!valid) return
  shipSubmitting.value = true
  try {
    const payload = {
      sn: shipForm.sn.trim(),
      logisticsCompany: shipForm.logisticsCompany,
      trackingNumber: shipForm.trackingNo,
      receiverId: shipForm.receiverId,
      transferType: shipForm.transferType || 'SHIP'
    }
    if (shipForm.shipTime) {
      payload.shipTime = shipForm.shipTime instanceof Date ? shipForm.shipTime.toISOString() : shipForm.shipTime
    }
    await shipProducts(payload)
    ElMessage.success('发货成功')
    shipDialogVisible.value = false
    loadTransfers()
  } catch (e) {
    ElMessage.error(e.message || '发货失败')
  } finally {
    shipSubmitting.value = false
  }
}

// ---- Receive ----
const receiveDialogVisible = ref(false)
const receiveFormRef = ref()
const receiveForm = reactive({ trackingNo: '' })
const receiveRules = {
  trackingNo: [{ required: true, message: '请输入物流单号或 SN', trigger: 'blur' }]
}
const receiveSubmitting = ref(false)

async function handleReceive() {
  const valid = await receiveFormRef.value.validate().catch(() => false)
  if (!valid) return
  receiveSubmitting.value = true
  try {
    await receiveProducts({ trackingNumber: receiveForm.trackingNo.trim() })
    ElMessage.success('收货确认成功')
    receiveDialogVisible.value = false
    loadTransfers()
  } catch (e) {
    ElMessage.error(e.message || '收货失败')
  } finally {
    receiveSubmitting.value = false
  }
}

// ---- Track ----
const trackSn = ref('')
const trackLoading = ref(false)
const trackEvents = ref([])

async function handleTrack() {
  if (!trackSn.value.trim()) {
    ElMessage.warning('请输入 SN')
    return
  }
  trackLoading.value = true
  trackEvents.value = []
  try {
    const res = await trackProduct(trackSn.value.trim())
    trackEvents.value = res.data || []
    if (!trackEvents.value.length) ElMessage.info('暂无物流记录')
  } catch (e) {
    ElMessage.error(e.message || '查询失败')
  } finally {
    trackLoading.value = false
  }
}

const batchDialogVisible = ref(false)
const batchForm = reactive({ logisticsCompany: '', trackingNo: '', receiverId: undefined, transferType: 'SHIP' })
const batchFile = ref(null)
const batchSubmitting = ref(false)

async function downloadBatchTpl() {
  try {
    const blob = await downloadSnShipTemplate()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'SN批量发货模板.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('下载模板失败')
  }
}

async function handleBatchShip() {
  if (!batchForm.logisticsCompany?.trim() || !batchForm.trackingNo?.trim() || !batchForm.receiverId) {
    ElMessage.warning('请填写物流公司、单号与接收方ID')
    return
  }
  if (!batchFile.value) {
    ElMessage.warning('请上传 Excel')
    return
  }
  batchSubmitting.value = true
  try {
    const fd = new FormData()
    fd.append('file', batchFile.value)
    fd.append('logisticsCompany', batchForm.logisticsCompany.trim())
    fd.append('trackingNumber', batchForm.trackingNo.trim())
    fd.append('receiverId', String(batchForm.receiverId))
    if (batchForm.transferType) fd.append('transferType', batchForm.transferType)
    await shipBatchProducts(fd)
    ElMessage.success('批量发货已提交')
    batchDialogVisible.value = false
    batchFile.value = null
    loadTransfers()
  } catch (e) {
    ElMessage.error(e.message || '批量发货失败')
  } finally {
    batchSubmitting.value = false
  }
}

onMounted(() => {
  loadPartnerUsers()
  loadTransfers()
})
</script>

<style scoped lang="scss">
.app-container {
  padding: 20px;
}
.mb-16 {
  margin-bottom: 16px;
}
.mt-16 {
  margin-top: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
.action-card {
  cursor: pointer;
  transition: transform 0.2s;
  &:hover {
    transform: translateY(-2px);
  }
  :deep(.el-card__body) {
    display: flex;
    align-items: center;
    gap: 16px;
  }
}
.field-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}
.hash-line {
  font-size: 12px;
  color: #606266;
  word-break: break-all;
}
.op-placeholder {
  color: #c0c4cc;
  font-size: 13px;
}
.action-card__text {
  h3 {
    margin: 0;
    font-size: 16px;
    color: #303133;
  }
  p {
    margin: 4px 0 0;
    font-size: 13px;
    color: #909399;
  }
}
</style>
