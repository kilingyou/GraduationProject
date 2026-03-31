<template>
  <div class="user-manage">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="账号">
          <el-input v-model="queryParams.username" placeholder="请输入账号" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="queryParams.roleKey" placeholder="全部角色" clearable style="width: 140px">
            <el-option label="系统管理员" value="admin" />
            <el-option label="供应商" value="supplier" />
            <el-option label="制造商" value="manufacturer" />
            <el-option label="组装商" value="assembler" />
            <el-option label="分销商" value="distributor" />
            <el-option label="终端用户" value="enduser" />
            <el-option label="监管机构" value="regulator" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="username" label="账号" min-width="120" />
        <el-table-column prop="enterpriseName" label="企业名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="contactPerson" label="联系人" width="100" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="roleKey" label="角色" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ roleLabels[row.roleKey] || row.roleKey }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              inline-prompt
              active-text="启"
              inactive-text="禁"
              style="margin: 0 8px"
              @change="handleToggleStatus(row)"
            />
            <el-popconfirm title="确认删除该用户？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="100px"
      >
        <el-form-item label="账号" prop="username">
          <el-input v-model="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="企业名称" prop="enterpriseName">
          <el-input v-model="editForm.enterpriseName" />
        </el-form-item>
        <el-form-item label="联系人" prop="contactPerson">
          <el-input v-model="editForm.contactPerson" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="editForm.phone" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="editForm.email" />
        </el-form-item>
        <el-form-item label="角色" prop="roleKey">
          <el-select v-model="editForm.roleKey" style="width: 100%">
            <el-option label="系统管理员" value="admin" />
            <el-option label="供应商" value="supplier" />
            <el-option label="制造商" value="manufacturer" />
            <el-option label="组装商" value="assembler" />
            <el-option label="分销商" value="distributor" />
            <el-option label="终端用户" value="enduser" />
            <el-option label="监管机构" value="regulator" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmitEdit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getUserList, updateUser, deleteUser, toggleUserStatus } from '@/api/system'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'

const roleLabels = {
  admin: '系统管理员',
  supplier: '供应商',
  manufacturer: '制造商',
  assembler: '组装商',
  distributor: '分销商',
  regulator: '监管机构',
  enduser: '终端用户'
}

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const queryParams = reactive({
  username: '',
  roleKey: '',
  pageNum: 1,
  pageSize: 10
})

const dialogVisible = ref(false)
const dialogTitle = ref('编辑用户')
const submitLoading = ref(false)
const editFormRef = ref()

const editForm = reactive({
  id: null,
  username: '',
  enterpriseName: '',
  contactPerson: '',
  phone: '',
  email: '',
  roleKey: ''
})

const editRules = {
  enterpriseName: [{ required: true, message: '请输入企业名称', trigger: 'blur' }],
  contactPerson: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入电话', trigger: 'blur' }],
  roleKey: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getUserList(queryParams)
    tableData.value = res.data.records || res.data.list || []
    total.value = res.data.total || 0
  } catch (err) {
    ElMessage.error('获取用户列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.pageNum = 1
  fetchList()
}

function handleReset() {
  queryParams.username = ''
  queryParams.roleKey = ''
  queryParams.pageNum = 1
  fetchList()
}

function handleEdit(row) {
  dialogTitle.value = '编辑用户'
  Object.assign(editForm, {
    id: row.id,
    username: row.username,
    enterpriseName: row.enterpriseName,
    contactPerson: row.contactPerson,
    phone: row.phone,
    email: row.email,
    roleKey: row.roleKey
  })
  dialogVisible.value = true
}

async function handleSubmitEdit() {
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    await updateUser(editForm.id, editForm)
    ElMessage.success('更新成功')
    dialogVisible.value = false
    fetchList()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '更新失败')
  } finally {
    submitLoading.value = false
  }
}

async function handleToggleStatus(row) {
  try {
    await toggleUserStatus(row.id, row.status)
    ElMessage.success(row.status === 1 ? '已启用' : '已禁用')
  } catch (err) {
    row.status = row.status === 1 ? 0 : 1
    ElMessage.error('操作失败')
  }
}

async function handleDelete(id) {
  try {
    await deleteUser(id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (err) {
    ElMessage.error('删除失败')
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.user-manage {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-card {
  border-radius: 8px;
  :deep(.el-card__body) {
    padding-bottom: 2px;
  }
}

.table-card {
  border-radius: 8px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
