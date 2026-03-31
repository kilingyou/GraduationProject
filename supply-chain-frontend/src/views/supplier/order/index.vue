<template>
  <div class="page-container">
    <div class="table-toolbar">
      <div class="search-bar">
        <el-button type="primary" :disabled="!isSupplierApproved" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>发布生产订单
        </el-button>
        <el-select
          v-model="queryParams.status"
          placeholder="状态筛选"
          clearable
          style="width: 160px"
          @change="handleSearch"
        >
          <el-option label="待接单" value="PENDING_ACCEPTANCE" />
          <el-option label="已接单" value="ACCEPTED" />
          <el-option label="生产中" value="IN_PRODUCTION" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已撤销" value="CANCELLED" />
        </el-select>
      </div>
    </div>

    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="id" label="订单ID" width="80" align="center" />
      <el-table-column prop="bomName" label="BOM" min-width="140" show-overflow-tooltip />
      <el-table-column prop="designDocName" label="设计文档" min-width="140" show-overflow-tooltip />
      <el-table-column prop="designDocHash" label="设计文档哈希" min-width="180" show-overflow-tooltip />
      <el-table-column prop="quantity" label="数量" width="80" align="center" />
      <el-table-column prop="expectedDelivery" label="期望交期" width="120" align="center" />
      <el-table-column prop="targetManufacturerName" label="目标制造商" width="130" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" class="status-tag">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" align="center" />
      <el-table-column label="操作" width="100" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleView(row)">查看详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="fetchList"
        @current-change="fetchList"
      />
    </div>

    <!-- Create Dialog -->
    <el-dialog v-model="createDialogVisible" title="发布生产订单" width="580px" @close="resetCreateForm">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="110px">
        <el-form-item label="选择BOM" prop="bomId">
          <el-select v-model="createForm.bomId" placeholder="请选择BOM" filterable style="width: 100%">
            <el-option
              v-for="bom in bomOptions"
              :key="bom.id"
              :label="bom.bomName"
              :value="bom.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="生产数量" prop="quantity">
          <el-input-number v-model="createForm.quantity" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="期望交期" prop="expectedDelivery">
          <el-date-picker
            v-model="createForm.expectedDelivery"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="质量要求" prop="qualityRequirement">
          <el-input
            v-model="createForm.qualityRequirement"
            type="textarea"
            :rows="3"
            placeholder="请输入质量要求"
          />
        </el-form-item>
        <el-form-item label="目标制造商">
          <el-select
            v-model="createForm.targetManufacturer"
            placeholder="不选则对所有制造商可见（广播）"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="m in manufacturerOptions"
              :key="m.id"
              :label="m.label"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">确认发布</el-button>
      </template>
    </el-dialog>

    <!-- Detail Dialog -->
    <el-dialog v-model="detailDialogVisible" title="订单详情" width="640px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="订单ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(detail.status)" class="status-tag">
            {{ statusLabel(detail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="BOM名称">{{ detail.bomName }}</el-descriptions-item>
        <el-descriptions-item label="生产数量">{{ detail.quantity }}</el-descriptions-item>
        <el-descriptions-item label="设计文档">{{ detail.designDocName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="设计文档哈希">{{ detail.designDocHash || '-' }}</el-descriptions-item>
        <el-descriptions-item label="期望交期">{{ detail.expectedDelivery }}</el-descriptions-item>
        <el-descriptions-item label="目标制造商">{{ detail.targetManufacturerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="质量要求" :span="2">{{ detail.qualityRequirement }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detail.updateTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import {
  createProductionOrder,
  getProductionOrderList,
  getProductionOrderDetail,
  cancelProductionOrder,
  listManufacturerOptions
} from '@/api/supplier'
import { getBomList } from '@/api/supplier'

const STATUS_MAP = {
  PENDING_ACCEPTANCE: { label: '待接单', type: 'warning' },
  ACCEPTED: { label: '已接单', type: '' },
  IN_PRODUCTION: { label: '生产中', type: 'info' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELLED: { label: '已撤销', type: 'info' }
}

function statusLabel(status) {
  return STATUS_MAP[status]?.label || status
}
function statusType(status) {
  return STATUS_MAP[status]?.type ?? 'info'
}

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const createDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const detail = ref({})
const createFormRef = ref()
const bomOptions = ref([])
const manufacturerOptions = ref([])
const userStore = useUserStore()
const isSupplierApproved = computed(() => userStore.isSupplierApproved)

const queryParams = reactive({
  status: '',
  pageNum: 1,
  pageSize: 10
})

const createForm = reactive({
  bomId: '',
  quantity: 1,
  expectedDelivery: '',
  qualityRequirement: '',
  targetManufacturer: undefined
})

const createRules = {
  bomId: [{ required: true, message: '请选择BOM', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入生产数量', trigger: 'blur' }],
  expectedDelivery: [{ required: true, message: '请选择期望交期', trigger: 'change' }]
}

async function fetchList() {
  loading.value = true
  try {
    const params = { ...queryParams }
    if (!params.status) delete params.status
    const res = await getProductionOrderList(params)
    tableData.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  } catch {
    ElMessage.error('获取订单列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.pageNum = 1
  fetchList()
}

async function fetchBomOptions() {
  try {
    const res = await getBomList({ pageNum: 1, pageSize: 200 })
    bomOptions.value = res.data?.records || res.data?.list || []
  } catch {
    /* silently ignore */
  }
}

async function fetchManufacturers() {
  try {
    const res = await listManufacturerOptions()
    manufacturerOptions.value = res.data || []
  } catch {
    manufacturerOptions.value = []
  }
}

function openCreateDialog() {
  if (!isSupplierApproved.value) {
    ElMessage.warning('资质审核通过后才可发布生产订单')
    return
  }
  fetchBomOptions()
  fetchManufacturers()
  createDialogVisible.value = true
}

function resetCreateForm() {
  createFormRef.value?.resetFields()
  createForm.targetManufacturer = undefined
}

async function submitCreate() {
  if (!isSupplierApproved.value) {
    ElMessage.warning('资质审核通过后才可发布生产订单')
    return
  }
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = { ...createForm }
    if (!payload.targetManufacturer) delete payload.targetManufacturer
    await createProductionOrder(payload)
    ElMessage.success('订单发布成功')
    createDialogVisible.value = false
    fetchList()
  } catch {
    ElMessage.error('订单发布失败')
  } finally {
    submitting.value = false
  }
}

async function handleView(row) {
  try {
    const res = await getProductionOrderDetail(row.id)
    detail.value = res.data || {}
    detailDialogVisible.value = true
  } catch {
    ElMessage.error('获取订单详情失败')
  }
}

async function handleCancel(row) {
  try {
    await ElMessageBox.confirm('确认撤销该订单？撤销后制造商将无法再接单。', '撤销订单', { type: 'warning' })
    await cancelProductionOrder(row.id)
    ElMessage.success('已撤销')
    fetchList()
  } catch (err) {
    if (err !== 'cancel') {
      /* 错误由拦截器或后端消息处理 */
    }
  }
}

onMounted(() => fetchList())
</script>
