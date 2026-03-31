<template>
  <div class="app-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <el-icon><ShoppingCart /></el-icon>
          <span>销售记录</span>
          <el-button type="primary" class="ml-auto" @click="dialogVisible = true">登记销售</el-button>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="sn" label="SN" width="200" />
        <el-table-column prop="saleTime" label="销售时间" width="180" />
        <el-table-column prop="customerHash" label="客户哈希" min-width="200" show-overflow-tooltip />
        <el-table-column prop="invoiceHash" label="发票哈希" min-width="200" show-overflow-tooltip />
        <el-table-column prop="txHash" label="交易哈希" min-width="200" show-overflow-tooltip />
      </el-table>
      <el-pagination
        class="mt-16"
        v-model:current-page="page.page"
        v-model:page-size="page.size"
        :total="page.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadList"
        @current-change="loadList"
      />
    </el-card>

    <!-- Register Sale Dialog -->
    <el-dialog v-model="dialogVisible" title="登记销售" width="560px" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="产品SN" prop="sn">
          <el-input v-model="form.sn" placeholder="请输入产品 SN" />
        </el-form-item>
        <el-form-item label="销售时间" prop="saleTime">
          <el-date-picker
            v-model="form.saleTime"
            type="datetime"
            placeholder="选择销售时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="客户姓名" prop="customerName">
          <el-input v-model="form.customerName" placeholder="将加密存储" />
        </el-form-item>
        <el-form-item label="客户电话" prop="customerPhone">
          <el-input v-model="form.customerPhone" placeholder="将加密存储" />
        </el-form-item>
        <el-form-item label="发票附件">
          <el-upload
            :auto-upload="false"
            :limit="1"
            :on-change="(f) => (form.invoice = f.raw)"
            :on-exceed="() => ElMessage.warning('只能上传一个文件')"
          >
            <template #trigger>
              <el-button>选择文件</el-button>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确认登记</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ShoppingCart } from '@element-plus/icons-vue'
import { registerSale, getSalesList } from '@/api/distributor'

const loading = ref(false)
const list = ref([])
const page = reactive({ page: 1, size: 10, total: 0 })

async function loadList() {
  loading.value = true
  try {
    const res = await getSalesList({ page: page.page, size: page.size })
    list.value = res.data?.records || res.data?.list || []
    page.total = res.data?.total || 0
  } catch {
    ElMessage.error('加载销售记录失败')
  } finally {
    loading.value = false
  }
}

const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({ sn: '', saleTime: '', customerName: '', customerPhone: '', invoice: null })
const rules = {
  sn: [{ required: true, message: '请输入 SN', trigger: 'blur' }],
  saleTime: [{ required: true, message: '请选择销售时间', trigger: 'change' }]
}
const submitting = ref(false)

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const fd = new FormData()
    fd.append('sn', form.sn)
    fd.append('saleTime', form.saleTime instanceof Date ? form.saleTime.toISOString() : form.saleTime)
    if (form.customerName) fd.append('customerName', form.customerName)
    if (form.customerPhone) fd.append('customerPhone', form.customerPhone)
    if (form.invoice) fd.append('invoice', form.invoice)
    await registerSale(fd)
    ElMessage.success('销售登记成功')
    dialogVisible.value = false
    Object.assign(form, { sn: '', saleTime: '', customerName: '', customerPhone: '', invoice: null })
    loadList()
  } catch (e) {
    ElMessage.error(e.message || '登记失败')
  } finally {
    submitting.value = false
  }
}

onMounted(loadList)
</script>

<style scoped lang="scss">
.app-container {
  padding: 20px;
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
.ml-auto {
  margin-left: auto;
}
</style>
