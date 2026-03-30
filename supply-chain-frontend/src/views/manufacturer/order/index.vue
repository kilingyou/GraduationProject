<template>
  <div class="order-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>生产订单列表</span>
          <el-input
            v-model="queryParams.keyword"
            placeholder="搜索订单ID / BOM名称"
            clearable
            style="width: 260px"
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          >
            <template #append>
              <el-button :icon="Search" @click="handleSearch" />
            </template>
          </el-input>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="orderList"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="orderId" label="订单ID" min-width="140" show-overflow-tooltip />
        <el-table-column prop="bomName" label="BOM" min-width="140" show-overflow-tooltip />
        <el-table-column prop="quantity" label="数量" width="100" align="center" />
        <el-table-column prop="expectedDelivery" label="期望交期" width="130" align="center" />
        <el-table-column prop="supplierName" label="供应商" min-width="120" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING_ACCEPTANCE'"
              type="primary"
              link
              @click="openAcceptDialog(row)"
            >
              接单
            </el-button>
            <el-button
              v-if="row.status === 'ACCEPTED' || row.status === 'IN_PRODUCTION'"
              type="success"
              link
              @click="openAgreementDialog(row)"
            >
              查看协议
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.page"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchOrders"
          @current-change="fetchOrders"
        />
      </div>
    </el-card>

    <!-- 接单对话框 -->
    <el-dialog v-model="acceptVisible" title="确认接单" width="520px" destroy-on-close>
      <el-form
        ref="acceptFormRef"
        :model="acceptForm"
        :rules="acceptRules"
        label-width="100px"
      >
        <el-form-item label="最终报价" prop="finalPrice">
          <el-input-number
            v-model="acceptForm.finalPrice"
            :min="0"
            :precision="2"
            :step="100"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="交付日期" prop="deliveryDate">
          <el-date-picker
            v-model="acceptForm.deliveryDate"
            type="date"
            placeholder="选择交付日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            :disabled-date="(d) => d < new Date()"
          />
        </el-form-item>
        <el-form-item label="协议文件">
          <el-upload
            v-model:file-list="acceptForm.fileList"
            :auto-upload="false"
            :limit="1"
            accept=".pdf,.doc,.docx"
          >
            <el-button type="primary" plain>选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 PDF / Word，可选上传</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="acceptVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAccept">
          确认接单
        </el-button>
      </template>
    </el-dialog>

    <!-- 协议详情对话框 -->
    <el-dialog v-model="agreementVisible" title="协议详情" width="600px" destroy-on-close>
      <el-descriptions v-if="agreement" :column="1" border>
        <el-descriptions-item label="订单ID">{{ agreement.orderId }}</el-descriptions-item>
        <el-descriptions-item label="最终价格">¥{{ agreement.finalPrice }}</el-descriptions-item>
        <el-descriptions-item label="交付日期">{{ agreement.deliveryDate }}</el-descriptions-item>
        <el-descriptions-item label="协议哈希">
          <el-text class="hash-text" truncated>{{ agreement.agreementHash || '-' }}</el-text>
        </el-descriptions-item>
        <el-descriptions-item label="供应商签名">
          <el-text class="hash-text" truncated>{{ agreement.supplierSignature || '-' }}</el-text>
        </el-descriptions-item>
        <el-descriptions-item label="制造商签名">
          <el-text class="hash-text" truncated>{{ agreement.manufacturerSignature || '-' }}</el-text>
        </el-descriptions-item>
        <el-descriptions-item label="签署时间">{{ agreement.signedAt || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="暂无协议信息" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getOrderList, acceptOrder, getAgreement } from '@/api/manufacturer'

const STATUS_MAP = {
  PENDING_ACCEPTANCE: { label: '待接单', type: 'warning' },
  ACCEPTED: { label: '已接单', type: 'success' },
  IN_PRODUCTION: { label: '生产中', type: '' },
  COMPLETED: { label: '已完成', type: 'info' },
  CANCELLED: { label: '已取消', type: 'danger' }
}

const statusLabel = (s) => STATUS_MAP[s]?.label || s
const statusType = (s) => STATUS_MAP[s]?.type ?? 'info'

const loading = ref(false)
const submitting = ref(false)
const orderList = ref([])
const total = ref(0)

const queryParams = reactive({
  keyword: '',
  page: 1,
  pageSize: 10
})

async function fetchOrders() {
  loading.value = true
  try {
    const { data } = await getOrderList(queryParams)
    orderList.value = data.records ?? data.list ?? []
    total.value = data.total ?? 0
  } catch { /* handled by interceptor */ } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.page = 1
  fetchOrders()
}

// --- Accept order ---
const acceptVisible = ref(false)
const acceptFormRef = ref(null)
const currentOrder = ref(null)

const acceptForm = reactive({
  finalPrice: null,
  deliveryDate: '',
  fileList: []
})

const acceptRules = {
  finalPrice: [{ required: true, message: '请输入最终报价', trigger: 'blur' }],
  deliveryDate: [{ required: true, message: '请选择交付日期', trigger: 'change' }]
}

function openAcceptDialog(row) {
  currentOrder.value = row
  acceptForm.finalPrice = null
  acceptForm.deliveryDate = ''
  acceptForm.fileList = []
  acceptVisible.value = true
}

async function handleAccept() {
  const valid = await acceptFormRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const formData = new FormData()
    formData.append('finalPrice', acceptForm.finalPrice)
    formData.append('deliveryDate', acceptForm.deliveryDate)
    if (acceptForm.fileList.length) {
      formData.append('agreementFile', acceptForm.fileList[0].raw)
    }
    await acceptOrder(currentOrder.value.orderId, formData)
    ElMessage.success('接单成功')
    acceptVisible.value = false
    fetchOrders()
  } catch { /* handled by interceptor */ } finally {
    submitting.value = false
  }
}

// --- Agreement ---
const agreementVisible = ref(false)
const agreement = ref(null)

async function openAgreementDialog(row) {
  agreementVisible.value = true
  agreement.value = null
  try {
    const { data } = await getAgreement(row.orderId)
    agreement.value = data
  } catch { /* handled by interceptor */ }
}

onMounted(fetchOrders)
</script>

<style scoped lang="scss">
.order-container {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }

  .hash-text {
    font-family: monospace;
    font-size: 12px;
    max-width: 400px;
  }
}
</style>
