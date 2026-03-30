<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="card-header">
        <div class="title">质量投诉/申请召回</div>
        <el-button type="primary" @click="openDialog">发起投诉</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe style="margin-top: 16px">
        <el-table-column prop="requestNo" label="投诉单号" min-width="150" />
        <el-table-column prop="sn" label="产品SN" min-width="190" />
        <el-table-column prop="faultType" label="故障类型" min-width="140" />
        <el-table-column prop="status" label="状态" min-width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUBMITTED' ? 'warning' : 'success'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">查看</el-button>
          </template>
        </el-table-column>
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

    <el-dialog v-model="dialogVisible" title="发起投诉" width="680px" @close="resetForm">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-form-item label="产品SN" prop="sn">
          <el-input v-model="form.sn" placeholder="请输入产品 SN" />
        </el-form-item>

        <el-form-item label="故障类型" prop="faultType">
          <el-select v-model="form.faultType" placeholder="请选择">
            <el-option label="硬件故障" value="HARDWARE" />
            <el-option label="软件缺陷" value="SOFTWARE" />
            <el-option label="外观损坏" value="APPEARANCE" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>

        <el-form-item label="故障描述" prop="faultDesc">
          <el-input
            v-model="form.faultDesc"
            placeholder="请描述故障现象与时间"
            type="textarea"
            :rows="4"
          />
        </el-form-item>

        <el-form-item label="证据文件" prop="evidenceUrls">
          <el-upload
            class="upload"
            drag
            :auto-upload="false"
            multiple
            :file-list="evidenceFiles"
            :on-change="handleEvidenceChange"
          >
            <el-icon><UploadFilled /></el-icon>
            <div class="el-upload__text">
              将图片/文件拖到这里，或<em>点击选择</em>
            </div>
            <div class="el-upload__tip">本版本仅记录文件名作为占位 evidenceUrls</div>
          </el-upload>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitComplaintForm">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="投诉详情" width="620px">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="投诉单号">{{ detail.requestNo }}</el-descriptions-item>
        <el-descriptions-item label="产品SN">{{ detail.sn }}</el-descriptions-item>
        <el-descriptions-item label="故障类型">{{ detail.faultType }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="故障描述" :span="2">{{ detail.faultDesc }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { submitComplaint as submitComplaintApi, getComplaintList } from '@/api/enduser'

const loading = ref(false)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const tableData = ref([])

const dialogVisible = ref(false)
const submitting = ref(false)
const detailVisible = ref(false)
const detail = ref(null)

const evidenceFiles = ref([])

const form = reactive({
  sn: '',
  faultType: '',
  faultDesc: '',
  evidenceUrls: '[]'
})

const rules = {
  sn: [{ required: true, message: '请输入产品 SN', trigger: 'blur' }],
  faultType: [{ required: true, message: '请选择故障类型', trigger: 'change' }],
  faultDesc: [{ required: true, message: '请输入故障描述', trigger: 'blur' }]
}

const formRef = ref(null)

function openDialog() {
  dialogVisible.value = true
}

function resetForm() {
  evidenceFiles.value = []
  form.sn = ''
  form.faultType = ''
  form.faultDesc = ''
  form.evidenceUrls = '[]'
}

function handleEvidenceChange(_file, fileList) {
  evidenceFiles.value = fileList
}

async function submitComplaintForm() {
  if (!formRef.value) {
    ElMessage.error('表单未就绪')
    return
  }
  await formRef.value.validate()

  submitting.value = true
  try {
    const names = evidenceFiles.value.map(f => f.name)
    form.evidenceUrls = JSON.stringify(names)
    const res = await submitComplaintApi(form)
    ElMessage.success('提交成功')
    dialogVisible.value = false
    await fetchList()
    return res
  } catch (e) {
    ElMessage.error('提交失败')
  } finally {
    submitting.value = false
  }
}

function viewDetail(row) {
  detail.value = row
  detailVisible.value = true
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getComplaintList({ pageNum: pageNum.value, pageSize: pageSize.value })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    ElMessage.error('获取列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchList()
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

.title {
  font-size: 16px;
  font-weight: 700;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.upload {
  width: 100%;
}
</style>

