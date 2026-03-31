<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div class="title">产品绑定</div>
          <div class="hint">
            绑定后方可投诉/报废。需该 SN 已销售登记，且填写的姓名、手机号须与销售登记时一致（与链上客户身份哈希比对）。
          </div>
        </div>
      </template>

      <el-form ref="bindFormRef" :model="bindForm" :rules="bindRules" class="bind-form" label-width="100px" @submit.prevent="submitBind">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="产品 SN" prop="sn">
              <el-input v-model="bindForm.sn" placeholder="整机 SN" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="姓名" prop="customerName">
              <el-input v-model="bindForm.customerName" placeholder="与销售登记一致（可留空）" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="手机号" prop="customerPhone">
              <el-input v-model="bindForm.customerPhone" placeholder="与销售登记一致" clearable />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item>
          <el-button type="primary" :loading="binding" @click="submitBind">绑定</el-button>
          <el-button @click="loadList">刷新列表</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="sn" label="SN" min-width="200" />
        <el-table-column prop="verifyStatus" label="校验状态" width="120" />
        <el-table-column prop="bindTime" label="绑定时间" min-width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { bindUserProduct, getUserProductList } from '@/api/enduser'

const bindFormRef = ref()
const bindForm = reactive({ sn: '', customerName: '', customerPhone: '' })
const bindRules = {
  sn: [{ required: true, message: '请输入 SN', trigger: 'blur' }],
  customerPhone: [{ required: true, message: '请输入手机号', trigger: 'blur' }]
}
const list = ref([])
const loading = ref(false)
const binding = ref(false)

async function loadList() {
  loading.value = true
  try {
    const res = await getUserProductList()
    list.value = res.data || []
  } catch {
    ElMessage.error('加载绑定列表失败')
  } finally {
    loading.value = false
  }
}

async function submitBind() {
  const ok = await bindFormRef.value?.validate().catch(() => false)
  if (!ok) return
  binding.value = true
  try {
    await bindUserProduct({
      sn: bindForm.sn.trim(),
      customerName: bindForm.customerName?.trim() || '',
      customerPhone: bindForm.customerPhone.trim()
    })
    ElMessage.success('绑定成功')
    bindForm.sn = ''
    bindForm.customerName = ''
    bindForm.customerPhone = ''
    await loadList()
  } catch {
    /* 拦截器已提示 */
  } finally {
    binding.value = false
  }
}

onMounted(() => {
  loadList()
})
</script>

<style scoped lang="scss">
.page-container {
  padding: 10px 20px 30px;
}

.card-header {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.title {
  font-size: 16px;
  font-weight: 700;
}

.hint {
  font-size: 13px;
  color: #909399;
}

.bind-form {
  margin-bottom: 16px;
}
</style>
