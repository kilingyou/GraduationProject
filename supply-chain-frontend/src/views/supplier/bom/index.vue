<template>
  <div class="page-container">
    <div class="table-toolbar">
      <el-button type="primary" :disabled="!isSupplierApproved" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>创建BOM
      </el-button>
      <el-button :disabled="!isSupplierApproved" @click="downloadBomTemplate">下载 Excel 模板</el-button>
      <el-upload
        :auto-upload="false"
        :show-file-list="false"
        accept=".xlsx,.xls"
        :on-change="handleBomExcelPick"
      >
        <el-button type="success" plain :disabled="!isSupplierApproved">Excel 导入物料</el-button>
      </el-upload>
    </div>

    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="bomName" label="BOM名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="designDocName" label="关联设计文档" min-width="160" show-overflow-tooltip />
      <el-table-column prop="version" label="版本" width="90" align="center" />
      <el-table-column prop="chainStatus" label="链上状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.chainStatus === 'ON_CHAIN' ? 'success' : 'warning'" class="status-tag">
            {{ row.chainStatus === 'ON_CHAIN' ? '已上链' : '待上链' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" align="center" />
      <el-table-column label="操作" width="160" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleView(row)">查看明细</el-button>
          <el-button link type="danger" :disabled="!isSupplierApproved" @click="handleDelete(row)">删除</el-button>
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
    <el-dialog v-model="createDialogVisible" title="创建BOM" width="780px" @close="resetCreateForm">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="110px">
        <el-form-item label="BOM名称" prop="bomName">
          <el-input v-model="createForm.bomName" placeholder="请输入BOM名称" />
        </el-form-item>
        <el-form-item label="关联设计文档" prop="designDocId">
          <el-select
            v-model="createForm.designDocId"
            placeholder="请选择设计文档"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="doc in designDocOptions"
              :key="doc.id"
              :label="doc.docName"
              :value="doc.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="版本号" prop="version">
          <el-input v-model="createForm.version" placeholder="例如 v1.0" />
        </el-form-item>
        <el-form-item label="物料清单">
          <div style="width: 100%">
            <el-table :data="createForm.items" border size="small">
              <el-table-column label="物料名称" min-width="130">
                <template #default="{ row }">
                  <el-input v-model="row.materialName" placeholder="物料名称" />
                </template>
              </el-table-column>
              <el-table-column label="物料编号" min-width="120">
                <template #default="{ row }">
                  <el-input v-model="row.materialCode" placeholder="物料编号" />
                </template>
              </el-table-column>
              <el-table-column label="规格型号" min-width="120">
                <template #default="{ row }">
                  <el-input v-model="row.specification" placeholder="规格型号" />
                </template>
              </el-table-column>
              <el-table-column label="数量" width="100">
                <template #default="{ row }">
                  <el-input-number v-model="row.quantity" :min="1" size="small" controls-position="right" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="单位" width="90">
                <template #default="{ row }">
                  <el-input v-model="row.unit" placeholder="单位" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70" align="center">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="removeItem($index)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button class="add-item-btn" type="primary" plain @click="addItem">
              <el-icon><Plus /></el-icon>添加物料
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">确认创建</el-button>
      </template>
    </el-dialog>

    <!-- Detail Dialog -->
    <el-dialog v-model="detailDialogVisible" title="BOM明细" width="780px">
      <el-descriptions :column="2" border style="margin-bottom: 20px">
        <el-descriptions-item label="BOM名称">{{ detail.bomName }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ detail.version }}</el-descriptions-item>
        <el-descriptions-item label="关联设计文档">{{ detail.designDocName }}</el-descriptions-item>
        <el-descriptions-item label="链上状态">
          <el-tag :type="detail.chainStatus === 'ON_CHAIN' ? 'success' : 'warning'" class="status-tag">
            {{ detail.chainStatus === 'ON_CHAIN' ? '已上链' : '待上链' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ detail.createTime }}</el-descriptions-item>
      </el-descriptions>

      <h4 style="margin-bottom: 12px">物料清单</h4>
      <el-table :data="detail.items || []" border size="small">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="partName" label="物料名称" min-width="130" />
        <el-table-column prop="partNumber" label="物料编号" min-width="120" />
        <el-table-column prop="specification" label="规格型号" min-width="120" />
        <el-table-column prop="quantity" label="数量" width="80" align="center" />
        <el-table-column prop="unit" label="单位" width="80" align="center" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import { createBom, getBomList, getBomDetail, deleteBom, parseBomExcel } from '@/api/supplier'
import { getDesignDocList } from '@/api/supplier'
import { useUserStore } from '@/store/user'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const createDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const detail = ref({})
const createFormRef = ref()
const designDocOptions = ref([])
const userStore = useUserStore()
const isSupplierApproved = computed(() => userStore.isSupplierApproved)

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10
})

function emptyItem() {
  return { materialName: '', materialCode: '', specification: '', quantity: 1, unit: '' }
}

const createForm = reactive({
  bomName: '',
  designDocId: '',
  version: '',
  items: [emptyItem()]
})

const createRules = {
  bomName: [{ required: true, message: '请输入BOM名称', trigger: 'blur' }],
  designDocId: [{ required: true, message: '请选择设计文档', trigger: 'change' }],
  version: [{ required: true, message: '请输入版本号', trigger: 'blur' }]
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getBomList(queryParams)
    tableData.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  } catch {
    ElMessage.error('获取BOM列表失败')
  } finally {
    loading.value = false
  }
}

async function fetchDesignDocs() {
  try {
    const res = await getDesignDocList({ pageNum: 1, pageSize: 200 })
    designDocOptions.value = res.data?.records || res.data?.list || []
  } catch {
    /* silently ignore, user can retry */
  }
}

function openCreateDialog() {
  if (!isSupplierApproved.value) {
    ElMessage.warning('资质审核通过后才可创建BOM')
    return
  }
  fetchDesignDocs()
  createDialogVisible.value = true
}

async function downloadBomTemplate() {
  if (!isSupplierApproved.value) {
    ElMessage.warning('资质审核通过后才可下载模板并创建BOM')
    return
  }
  const token = userStore.token
  try {
    const r = await fetch('/api/supplier/bom/import-template', {
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    })
    if (!r.ok) throw new Error('download failed')
    const blob = await r.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'BOM导入模板.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('模板下载失败，请登录后重试')
  }
}

async function handleBomExcelPick(uploadFile) {
  if (!isSupplierApproved.value) {
    ElMessage.warning('资质审核通过后才可导入BOM物料')
    return
  }
  const raw = uploadFile?.raw
  if (!raw) return
  try {
    const res = await parseBomExcel(raw)
    const items = res.data || []
    await fetchDesignDocs()
    createForm.bomName = ''
    createForm.designDocId = ''
    createForm.version = ''
    createForm.items = items.map((i) => ({
      materialName: i.partName,
      materialCode: i.partNumber,
      specification: i.specification || '',
      quantity: i.quantity || 1,
      unit: i.unit || ''
    }))
    createDialogVisible.value = true
    ElMessage.success(`已解析 ${items.length} 条物料，请填写 BOM 名称、设计文档与版本后创建`)
  } catch {
    /* interceptor */
  }
}

function addItem() {
  createForm.items.push(emptyItem())
}

function removeItem(index) {
  if (createForm.items.length <= 1) {
    ElMessage.warning('至少保留一条物料')
    return
  }
  createForm.items.splice(index, 1)
}

function resetCreateForm() {
  createFormRef.value?.resetFields()
  createForm.items = [emptyItem()]
}

async function submitCreate() {
  if (!isSupplierApproved.value) {
    ElMessage.warning('资质审核通过后才可创建BOM')
    return
  }
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return

  const hasEmpty = createForm.items.some(i => !i.materialName || !i.materialCode)
  if (hasEmpty) {
    ElMessage.warning('请填写完整的物料信息（名称和编号必填）')
    return
  }

  submitting.value = true
  try {
    await createBom({
      bomName: createForm.bomName,
      designDocId: createForm.designDocId,
      version: createForm.version,
      items: createForm.items
    })
    ElMessage.success('创建成功')
    createDialogVisible.value = false
    fetchList()
  } catch {
    ElMessage.error('创建失败')
  } finally {
    submitting.value = false
  }
}

async function handleView(row) {
  try {
    const res = await getBomDetail(row.id)
    detail.value = res.data || {}
    detailDialogVisible.value = true
  } catch {
    ElMessage.error('获取BOM详情失败')
  }
}

async function handleDelete(row) {
  if (!isSupplierApproved.value) {
    ElMessage.warning('资质审核通过后才可删除BOM')
    return
  }
  try {
    await ElMessageBox.confirm('确认删除该BOM？删除后不可恢复', '提示', { type: 'warning' })
    await deleteBom(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (err) {
    if (err !== 'cancel') ElMessage.error('删除失败')
  }
}

onMounted(() => fetchList())
</script>

<style scoped lang="scss">
.table-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.add-item-btn {
  margin-top: 12px;
  width: 100%;
}
</style>
