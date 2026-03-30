<template>
  <div class="app-container">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- Tab: Assembly Batches -->
      <el-tab-pane label="组装批次" name="batch">
        <el-card shadow="never" class="mb-16">
          <el-form :model="batchForm" :rules="batchRules" ref="batchFormRef" inline>
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

        <el-table :data="batchList" v-loading="batchLoading" border stripe>
          <el-table-column prop="batchNo" label="批次号" width="200" />
          <el-table-column prop="productModel" label="产品型号" width="160" />
          <el-table-column prop="plannedQty" label="计划数量" width="100" align="center" />
          <el-table-column prop="completedQty" label="已完成" width="100" align="center" />
          <el-table-column prop="status" label="状态" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="batchStatusType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" min-width="180" />
        </el-table>
        <el-pagination
          class="mt-16"
          v-model:current-page="batchPage.page"
          v-model:page-size="batchPage.size"
          :total="batchPage.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadBatches"
          @current-change="loadBatches"
        />
      </el-tab-pane>

      <!-- Tab: Assembly Records -->
      <el-tab-pane label="组装记录" name="record">
        <el-card shadow="never" class="mb-16">
          <el-form :model="recordForm" :rules="recordRules" ref="recordFormRef" label-width="100px">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="组装批次" prop="batchNo">
                  <el-select v-model="recordForm.batchNo" placeholder="请选择批次" filterable style="width: 100%">
                    <el-option
                      v-for="b in batchList"
                      :key="b.batchNo"
                      :label="`${b.batchNo} (${b.productModel})`"
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
              <el-col :span="24">
                <el-form-item label="ECID列表" prop="ecidList">
                  <el-select
                    v-model="recordForm.ecidList"
                    multiple
                    filterable
                    allow-create
                    default-first-option
                    placeholder="输入 ECID 后回车添加"
                    style="width: 100%"
                  />
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
          <el-table-column prop="sn" label="SN" width="200" />
          <el-table-column prop="batchNo" label="批次号" width="180" />
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
          <el-table-column prop="chainStatus" label="链上状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.chainStatus === 'ON_CHAIN' ? 'success' : 'warning'" size="small">
                {{ row.chainStatus === 'ON_CHAIN' ? '已上链' : '未上链' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.chainStatus !== 'ON_CHAIN'"
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
          v-model:current-page="recordPage.page"
          v-model:page-size="recordPage.size"
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createAssemblyBatch, getAssemblyBatchList,
  createAssemblyRecord, getAssemblyRecordList,
  registerAssemblyOnChain
} from '@/api/assembler'

const activeTab = ref('batch')

// ---- Batch ----
const batchFormRef = ref()
const batchForm = reactive({ productModel: '', plannedQty: 1 })
const batchRules = {
  productModel: [{ required: true, message: '请输入产品型号', trigger: 'blur' }],
  plannedQty: [{ required: true, message: '请输入计划数量', trigger: 'change' }]
}
const batchSubmitting = ref(false)
const batchLoading = ref(false)
const batchList = ref([])
const batchPage = reactive({ page: 1, size: 10, total: 0 })

async function loadBatches() {
  batchLoading.value = true
  try {
    const res = await getAssemblyBatchList({ page: batchPage.page, size: batchPage.size })
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

// ---- Records ----
const recordFormRef = ref()
const recordForm = reactive({ batchNo: '', ecidList: [], firmwareVersion: '' })
const recordRules = {
  batchNo: [{ required: true, message: '请选择批次', trigger: 'change' }],
  ecidList: [{ required: true, type: 'array', min: 1, message: '请添加至少一个 ECID', trigger: 'change' }],
  firmwareVersion: [{ required: true, message: '请输入固件版本', trigger: 'blur' }]
}
const recordSubmitting = ref(false)
const recordLoading = ref(false)
const recordList = ref([])
const recordPage = reactive({ page: 1, size: 10, total: 0 })
const generatedSn = ref('')

async function loadRecords() {
  recordLoading.value = true
  try {
    const res = await getAssemblyRecordList({ page: recordPage.page, size: recordPage.size })
    recordList.value = (res.data?.records || res.data?.list || []).map(r => ({ ...r, _registering: false }))
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
    loadRecords()
  } catch (e) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    recordSubmitting.value = false
  }
}

async function handleRegister(row) {
  row._registering = true
  try {
    await registerAssemblyOnChain(row.id)
    ElMessage.success('注册上链成功')
    row.chainStatus = 'ON_CHAIN'
  } catch (e) {
    ElMessage.error(e.message || '上链失败')
  } finally {
    row._registering = false
  }
}

onMounted(() => {
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
.mt-16 {
  margin-top: 16px;
}
.ecid-tag {
  margin: 2px 4px 2px 0;
}
</style>
