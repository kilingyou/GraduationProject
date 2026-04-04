<template>
  <div class="app-container">
    <el-card shadow="hover" class="mb-16">
      <template #header>
        <div class="card-header">
          <el-icon><Document /></el-icon>
          <span>上传质检报告</span>
        </div>
      </template>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="关联方式" prop="targetType">
              <el-select v-model="form.targetType" placeholder="请选择" style="width: 100%" @change="onTargetTypeChange">
                <el-option label="单台整机（SN）" value="SN" />
                <el-option label="按组装批次（整批）" value="BATCH" />
                <el-option label="多个 SN（批量）" value="MULTI_SN" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="form.targetType === 'SN'" :span="10">
            <el-form-item label="整机 SN" prop="snSingle">
              <el-input v-model="form.snSingle" placeholder="请输入整机 SN" clearable />
            </el-form-item>
          </el-col>
          <el-col v-else-if="form.targetType === 'BATCH'" :span="10">
            <el-form-item label="组装批次" prop="batchNo">
              <el-select
                v-model="form.batchNo"
                placeholder="选择批次（该批下全部整机共用本报告）"
                filterable
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="b in batchList"
                  :key="b.batchNo"
                  :label="`${b.batchNo}（${b.productModel || '—'}）`"
                  :value="b.batchNo"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-else :span="10" />
          <el-col :span="6">
            <el-form-item label="检测结果" prop="result">
              <el-select v-model="form.result" placeholder="请选择" style="width: 100%">
                <el-option label="通过 (PASS)" value="PASS" />
                <el-option label="不通过 (FAIL)" value="FAIL" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row v-if="form.targetType === 'MULTI_SN'" :gutter="20">
          <el-col :span="24">
            <el-form-item label="整机 SN 列表" prop="snBulk">
              <el-input
                v-model="form.snBulk"
                type="textarea"
                :rows="5"
                placeholder="每行一个 SN，或用英文逗号、分号、空格分隔；将使用同一份报告文件批量更新这些整机"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="16">
            <el-form-item label="报告文件">
              <el-upload
                ref="uploadRef"
                :auto-upload="false"
                :limit="1"
                :on-change="handleFileChange"
                :on-exceed="() => ElMessage.warning('只能上传一个文件')"
              >
                <template #trigger>
                  <el-button>选择文件</el-button>
                </template>
              </el-upload>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item>
              <el-button type="primary" :loading="submitting" @click="handleSubmit">
                提交报告
              </el-button>
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert
          v-if="form.targetType === 'BATCH' && form.batchNo"
          type="info"
          :closable="false"
          show-icon
          class="hint-alert"
          :title="batchHint"
        />
      </el-form>
    </el-card>

    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <el-icon><List /></el-icon>
          <span>质检报告列表</span>
        </div>
      </template>
      <el-table :data="reportList" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="sn" label="整机 SN" width="180" />
        <el-table-column prop="assemblyBatchNo" label="组装批次" width="180" />
        <el-table-column prop="testResult" label="检测结果" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.testResult === 'PASS' ? 'success' : row.testResult === 'FAIL' ? 'danger' : 'info'" size="small">
              {{ row.testResult === 'PASS' ? '通过' : row.testResult === 'FAIL' ? '不通过' : row.testResult || '—' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="testReportHash" label="报告哈希" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createTime" label="记录时间" width="180" />
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, List } from '@element-plus/icons-vue'
import {
  uploadAssemblyReport,
  getAssemblyReportList,
  getAssemblyBatchList,
  getAssemblyRecordList
} from '@/api/assembler'

const formRef = ref()
const uploadRef = ref()
const form = reactive({
  targetType: 'SN',
  snSingle: '',
  batchNo: '',
  snBulk: '',
  result: '',
  file: null
})

const rules = computed(() => {
  const base = {
    targetType: [{ required: true, message: '请选择关联方式', trigger: 'change' }],
    result: [{ required: true, message: '请选择检测结果', trigger: 'change' }]
  }
  if (form.targetType === 'SN') {
    base.snSingle = [{ required: true, message: '请输入整机 SN', trigger: 'blur' }]
  } else if (form.targetType === 'BATCH') {
    base.batchNo = [{ required: true, message: '请选择组装批次', trigger: 'change' }]
  } else {
    base.snBulk = [{ required: true, message: '请填写至少一个 SN', trigger: 'blur' }]
  }
  return base
})

const batchList = ref([])
const batchRecordCount = ref({})

const batchHint = computed(() => {
  const n = batchRecordCount.value[form.batchNo]
  if (n != null && n >= 0) {
    return `将使用同一份报告文件，更新该批次下全部整机（当前约 ${n} 条组装记录）。`
  }
  return '将使用同一份报告文件，更新该批次下全部组装记录。'
})

async function loadBatches() {
  try {
    const res = await getAssemblyBatchList({ pageNum: 1, pageSize: 500 })
    batchList.value = res.data?.records || res.data?.list || []
  } catch {
    batchList.value = []
  }
}

async function refreshBatchRecordCount(batchNo) {
  if (!batchNo) return
  try {
    const r2 = await getAssemblyRecordList({ pageNum: 1, pageSize: 1, assemblyBatchNo: batchNo })
    const t2 = r2.data?.total
    if (typeof t2 === 'number') {
      batchRecordCount.value = { ...batchRecordCount.value, [batchNo]: t2 }
    }
  } catch {
    /* ignore */
  }
}

watch(
  () => form.batchNo,
  (bn) => {
    if (form.targetType === 'BATCH' && bn) {
      refreshBatchRecordCount(bn)
    }
  }
)

function onTargetTypeChange() {
  formRef.value?.clearValidate()
}

const submitting = ref(false)

function handleFileChange(file) {
  form.file = file.raw
}

function buildTargetId() {
  if (form.targetType === 'SN') {
    return (form.snSingle || '').trim()
  }
  if (form.targetType === 'BATCH') {
    return (form.batchNo || '').trim()
  }
  return (form.snBulk || '').trim()
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (!form.file) {
    ElMessage.warning('请选择报告文件')
    return
  }
  const mode = form.targetType
  submitting.value = true
  try {
    const fd = new FormData()
    fd.append('targetType', mode)
    fd.append('targetId', buildTargetId())
    fd.append('result', form.result)
    fd.append('file', form.file)
    const res = await uploadAssemblyReport(fd)
    ElMessage.success(res.message || '报告上传成功')
    if (mode === 'MULTI_SN' && res.data?.failed?.length) {
      ElMessage.warning(res.data.failed.join('；'))
    }
    Object.assign(form, {
      targetType: 'SN',
      snSingle: '',
      batchNo: '',
      snBulk: '',
      result: '',
      file: null
    })
    formRef.value?.clearValidate()
    uploadRef.value?.clearFiles()
    loadList()
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

const loading = ref(false)
const reportList = ref([])
const page = reactive({ page: 1, size: 10, total: 0 })

async function loadList() {
  loading.value = true
  try {
    const res = await getAssemblyReportList({ page: page.page, size: page.size })
    reportList.value = res.data?.records || res.data?.list || []
    page.total = res.data?.total || 0
  } catch {
    ElMessage.error('加载报告列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadBatches()
  loadList()
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
.hint-alert {
  margin-top: 8px;
}
</style>
