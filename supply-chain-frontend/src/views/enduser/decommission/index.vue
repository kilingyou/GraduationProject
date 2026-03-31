<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="card-header">
        <div class="title">报废登记</div>
        <div class="toolbar">
          <el-button @click="bindDialogVisible = true">绑定产品</el-button>
          <el-button type="primary" @click="openDialog">申请报废</el-button>
        </div>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe style="margin-top: 16px">
        <el-table-column prop="sn" label="产品SN" min-width="220" />
        <el-table-column prop="disposalMethod" label="处置方式" min-width="160" />
        <el-table-column prop="recyclerName" label="回收机构" min-width="180" />
        <el-table-column prop="status" label="状态" min-width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'warning'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
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

    <el-dialog v-model="dialogVisible" title="申请报废" width="560px" @close="resetForm">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-form-item label="产品SN" prop="sn">
          <el-select v-model="form.sn" filterable placeholder="请选择已绑定 SN">
            <el-option
              v-for="item in boundProducts"
              :key="item.id"
              :label="item.sn"
              :value="item.sn"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="处置方式" prop="disposalMethod">
          <el-select v-model="form.disposalMethod" placeholder="请选择">
            <el-option label="物理粉碎" value="PHYSICAL_CRUSH" />
            <el-option label="化学提炼" value="CHEMICAL_EXTRACTION" />
            <el-option label="元器件拆解" value="DISASSEMBLE" />
          </el-select>
        </el-form-item>

        <el-form-item label="回收机构" prop="recyclerName">
          <el-input v-model="form.recyclerName" placeholder="请输入回收机构名称" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitDecommission">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bindDialogVisible" title="绑定产品" width="520px" @close="resetBindForm">
      <el-form :model="bindForm" :rules="bindRules" ref="bindFormRef" label-width="100px">
        <el-form-item label="产品 SN" prop="sn">
          <el-input v-model="bindForm.sn" placeholder="销售后的整机 SN" />
        </el-form-item>
        <el-form-item label="姓名" prop="customerName">
          <el-input v-model="bindForm.customerName" placeholder="与销售登记一致（可留空）" />
        </el-form-item>
        <el-form-item label="手机号" prop="customerPhone">
          <el-input v-model="bindForm.customerPhone" placeholder="与销售登记一致" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="binding" @click="submitBind">确认绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  applyDecommission,
  bindUserProduct,
  getDecommissionList,
  getUserProductList
} from '@/api/enduser'

const loading = ref(false)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const tableData = ref([])

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const boundProducts = ref([])
const bindDialogVisible = ref(false)
const binding = ref(false)
const bindFormRef = ref(null)

const form = reactive({
  sn: '',
  disposalMethod: '',
  recyclerName: ''
})

const rules = {
  sn: [{ required: true, message: '请输入产品 SN', trigger: 'blur' }],
  disposalMethod: [{ required: true, message: '请选择处置方式', trigger: 'change' }],
  recyclerName: [{ required: true, message: '请输入回收机构名称', trigger: 'blur' }]
}
const bindForm = reactive({ sn: '', customerName: '', customerPhone: '' })
const bindRules = {
  sn: [{ required: true, message: '请输入产品 SN', trigger: 'blur' }],
  customerPhone: [{ required: true, message: '请输入手机号', trigger: 'blur' }]
}

function openDialog() {
  if (!boundProducts.value.length) {
    ElMessage.warning('请先绑定产品后再申请报废')
    bindDialogVisible.value = true
    return
  }
  dialogVisible.value = true
}

function resetForm() {
  form.sn = ''
  form.disposalMethod = ''
  form.recyclerName = ''
}

function resetBindForm() {
  bindForm.sn = ''
  bindForm.customerName = ''
  bindForm.customerPhone = ''
}

async function submitDecommission() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }

  submitting.value = true
  try {
    await applyDecommission(form)
    ElMessage.success('提交成功')
    dialogVisible.value = false
    await fetchList()
  } catch {
    ElMessage.error('提交失败')
  } finally {
    submitting.value = false
  }
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getDecommissionList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      page: pageNum.value,
      size: pageSize.value
    })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    ElMessage.error('获取列表失败')
  } finally {
    loading.value = false
  }
}

async function fetchBoundProducts() {
  try {
    const res = await getUserProductList()
    boundProducts.value = res.data || []
  } catch {
    ElMessage.error('获取已绑定产品失败')
  }
}

async function submitBind() {
  if (!bindFormRef.value) return
  try { await bindFormRef.value.validate() } catch { return }
  binding.value = true
  try {
    await bindUserProduct({
      sn: bindForm.sn.trim(),
      customerName: bindForm.customerName?.trim() || '',
      customerPhone: bindForm.customerPhone.trim()
    })
    ElMessage.success('绑定成功')
    bindDialogVisible.value = false
    await fetchBoundProducts()
    if (!form.sn && boundProducts.value.length) {
      form.sn = boundProducts.value[0].sn
    }
  } catch {
    ElMessage.error('绑定失败')
  } finally {
    binding.value = false
  }
}

onMounted(() => {
  fetchList()
  fetchBoundProducts()
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

.toolbar {
  display: flex;
  gap: 10px;
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
</style>

