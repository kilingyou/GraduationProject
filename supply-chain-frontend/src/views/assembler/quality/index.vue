<template>
  <div class="app-container">
    <!-- Upload Report -->
    <el-card shadow="hover" class="mb-16">
      <template #header>
        <div class="card-header">
          <el-icon><Document /></el-icon>
          <span>上传质检报告</span>
        </div>
      </template>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="关联类型" prop="targetType">
              <el-select v-model="form.targetType" placeholder="请选择" style="width: 100%">
                <el-option label="产品 SN" value="SN" />
                <el-option label="组装批次" value="BATCH" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="关联标识" prop="targetId">
              <el-input v-model="form.targetId" :placeholder="form.targetType === 'SN' ? '请输入 SN' : '请输入批次号'" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="检测结果" prop="result">
              <el-select v-model="form.result" placeholder="请选择" style="width: 100%">
                <el-option label="通过 (PASS)" value="PASS" />
                <el-option label="不通过 (FAIL)" value="FAIL" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="16">
            <el-form-item label="报告文件" prop="file">
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
      </el-form>
    </el-card>

    <!-- Report List -->
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, List } from '@element-plus/icons-vue'
import { uploadAssemblyReport, getAssemblyReportList } from '@/api/assembler'

const formRef = ref()
const uploadRef = ref()
const form = reactive({ targetType: 'SN', targetId: '', result: '', file: null })
const rules = {
  targetType: [{ required: true, message: '请选择关联类型', trigger: 'change' }],
  targetId: [{ required: true, message: '请输入关联标识', trigger: 'blur' }],
  result: [{ required: true, message: '请选择检测结果', trigger: 'change' }]
}
const submitting = ref(false)

function handleFileChange(file) {
  form.file = file.raw
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (!form.file) {
    ElMessage.warning('请选择报告文件')
    return
  }
  submitting.value = true
  try {
    const fd = new FormData()
    fd.append('targetType', form.targetType)
    fd.append('targetId', form.targetId)
    fd.append('result', form.result)
    fd.append('file', form.file)
    await uploadAssemblyReport(fd)
    ElMessage.success('报告上传成功')
    formRef.value.resetFields()
    uploadRef.value?.clearFiles()
    form.file = null
    loadList()
  } catch (e) {
    ElMessage.error(e.message || '上传失败')
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
  } catch (e) {
    ElMessage.error('加载报告列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadList)
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
</style>
