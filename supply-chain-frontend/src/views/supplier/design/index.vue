<template>
  <div class="page-container">
    <div class="table-toolbar">
      <div class="search-bar">
        <el-button type="primary" :disabled="!isSupplierApproved" @click="openUploadDialog">
          <el-icon><Upload /></el-icon>上传文档
        </el-button>
        <el-input
          v-model="queryParams.keyword"
          placeholder="搜索文档名称"
          clearable
          style="width: 240px"
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        >
          <template #append>
            <el-button @click="handleSearch"><el-icon><Search /></el-icon></el-button>
          </template>
        </el-input>
      </div>
    </div>

    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="docName" label="文档名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="docType" label="文档类型" width="120" align="center">
        <template #default="{ row }">
          {{ row.docType === 'DRAWING' ? '图纸' : '合规声明' }}
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="80" align="center" />
      <el-table-column prop="fileHash" label="文件哈希" min-width="180" show-overflow-tooltip />
      <el-table-column prop="chainStatus" label="链上状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.chainStatus === 'ON_CHAIN' ? 'success' : 'warning'" class="status-tag">
            {{ row.chainStatus === 'ON_CHAIN' ? '已上链' : '待上链' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ipfsCid" label="IPFS CID" min-width="180" show-overflow-tooltip />
      <el-table-column prop="createTime" label="创建时间" width="170" align="center" />
      <el-table-column label="操作" width="240" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleView(row)">查看</el-button>
          <el-button
            link
            type="warning"
            :loading="row._verifying"
            @click="handleVerify(row)"
          >校验哈希</el-button>
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

    <!-- Upload Dialog -->
    <el-dialog v-model="uploadDialogVisible" title="上传设计文档" width="540px" @close="resetUploadForm">
      <el-form ref="uploadFormRef" :model="uploadForm" :rules="uploadRules" label-width="100px">
        <el-form-item label="文档名称" prop="docName">
          <el-input v-model="uploadForm.docName" placeholder="请输入文档名称" />
        </el-form-item>
        <el-form-item label="文档类型" prop="docType">
          <el-select v-model="uploadForm.docType" placeholder="请选择文档类型" style="width: 100%">
            <el-option label="图纸" value="DRAWING" />
            <el-option label="合规声明" value="COMPLIANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本号" prop="version">
          <el-input v-model="uploadForm.version" placeholder="例如 v1.0" />
        </el-form-item>
        <el-form-item label="更新说明" prop="updateNote">
          <el-input v-model="uploadForm.updateNote" type="textarea" :rows="3" placeholder="请输入更新说明" />
        </el-form-item>
        <el-form-item label="选择文件" prop="file">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
          >
            <el-button type="primary" plain>选择文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">确认上传</el-button>
      </template>
    </el-dialog>

    <!-- Detail Dialog -->
    <el-dialog v-model="detailDialogVisible" title="文档详情" width="600px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="文档名称">{{ detail.docName }}</el-descriptions-item>
        <el-descriptions-item label="文档类型">
          {{ detail.docType === 'DRAWING' ? '图纸' : '合规声明' }}
        </el-descriptions-item>
        <el-descriptions-item label="版本">{{ detail.version }}</el-descriptions-item>
        <el-descriptions-item label="链上状态">
          <el-tag :type="detail.chainStatus === 'ON_CHAIN' ? 'success' : 'warning'" class="status-tag">
            {{ detail.chainStatus === 'ON_CHAIN' ? '已上链' : '待上链' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="文件哈希" :span="2">{{ detail.fileHash }}</el-descriptions-item>
        <el-descriptions-item label="IPFS CID" :span="2">{{ detail.ipfsCid }}</el-descriptions-item>
        <el-descriptions-item label="更新说明" :span="2">{{ detail.updateNote }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detail.updateTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import {
  uploadDesignDoc,
  getDesignDocList,
  getDesignDocDetail,
  verifyDesignDoc,
  deleteDesignDoc
} from '@/api/supplier'

const loading = ref(false)
const uploading = ref(false)
const tableData = ref([])
const total = ref(0)
const uploadDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const detail = ref({})
const uploadFormRef = ref()
const uploadRef = ref()
const userStore = useUserStore()
const isSupplierApproved = computed(() => userStore.isSupplierApproved)

const queryParams = reactive({
  keyword: '',
  pageNum: 1,
  pageSize: 10
})

const uploadForm = reactive({
  docName: '',
  docType: '',
  version: '',
  updateNote: '',
  file: null
})

const uploadRules = {
  docName: [{ required: true, message: '请输入文档名称', trigger: 'blur' }],
  docType: [{ required: true, message: '请选择文档类型', trigger: 'change' }],
  version: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
  file: [{ required: true, message: '请选择文件', validator: (rule, value, cb) => uploadForm.file ? cb() : cb(new Error('请选择文件')) }]
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getDesignDocList(queryParams)
    tableData.value = (res.data?.records || res.data?.list || []).map(item => ({ ...item, _verifying: false }))
    total.value = res.data?.total || 0
  } catch {
    ElMessage.error('获取文档列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.pageNum = 1
  fetchList()
}

function handleFileChange(file) {
  uploadForm.file = file.raw
}

function handleFileRemove() {
  uploadForm.file = null
}

function resetUploadForm() {
  uploadFormRef.value?.resetFields()
  uploadForm.file = null
  uploadRef.value?.clearFiles()
}

async function submitUpload() {
  if (!isSupplierApproved.value) {
    ElMessage.warning('资质审核通过后才可上传设计文档')
    return
  }
  const valid = await uploadFormRef.value.validate().catch(() => false)
  if (!valid) return

  const formData = new FormData()
  formData.append('file', uploadForm.file)
  formData.append('docName', uploadForm.docName)
  formData.append('docType', uploadForm.docType)
  formData.append('version', uploadForm.version)
  formData.append('updateNote', uploadForm.updateNote)

  uploading.value = true
  try {
    await uploadDesignDoc(formData)
    ElMessage.success('上传成功')
    uploadDialogVisible.value = false
    fetchList()
  } catch {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

async function handleView(row) {
  try {
    const res = await getDesignDocDetail(row.id)
    detail.value = res.data || {}
    detailDialogVisible.value = true
  } catch {
    ElMessage.error('获取文档详情失败')
  }
}

async function handleVerify(row) {
  row._verifying = true
  try {
    const res = await verifyDesignDoc(row.id)
    if (res.data?.match) {
      ElMessage.success('哈希校验通过，文件完整性验证成功')
    } else {
      ElMessage.error('哈希校验不通过，文件可能已被篡改')
    }
  } catch {
    ElMessage.error('校验请求失败')
  } finally {
    row._verifying = false
  }
}

async function handleDelete(row) {
  if (!isSupplierApproved.value) {
    ElMessage.warning('资质审核通过后才可删除设计文档')
    return
  }
  try {
    await ElMessageBox.confirm('确认删除该文档？删除后不可恢复', '提示', { type: 'warning' })
    await deleteDesignDoc(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (err) {
    if (err !== 'cancel') ElMessage.error('删除失败')
  }
}

function openUploadDialog() {
  if (!isSupplierApproved.value) {
    ElMessage.warning('资质审核通过后才可上传设计文档')
    return
  }
  uploadDialogVisible.value = true
}

onMounted(() => fetchList())
</script>

<style scoped lang="scss">
.search-bar {
  margin-bottom: 0;
}
</style>
