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

    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="consistencyParams">
        <el-form-item label="一致性角色筛选">
          <el-select v-model="consistencyParams.roleKey" placeholder="全部角色" clearable style="width: 140px">
            <el-option label="供应商" value="supplier" />
            <el-option label="制造商" value="manufacturer" />
            <el-option label="组装商" value="assembler" />
            <el-option label="分销商" value="distributor" />
            <el-option label="监管机构" value="regulator" />
            <el-option label="终端用户" value="enduser" />
          </el-select>
        </el-form-item>
        <el-form-item label="检查数量">
          <el-input-number v-model="consistencyParams.limit" :min="1" :max="200" />
        </el-form-item>
        <el-form-item label="仅修复不一致">
          <el-switch v-model="consistencyParams.onlyInconsistent" />
        </el-form-item>
        <el-form-item>
          <el-button :loading="consistencyLoading" @click="handleCheckConsistency">检查角色一致性</el-button>
          <el-button type="warning" :loading="repairLoading" @click="handleRepairConsistency">批量修复不一致</el-button>
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
            <el-button link type="warning" size="small" @click="handleInitChainAccount(row)">初始化链上</el-button>
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

    <el-dialog v-model="consistencyDialogVisible" title="系统角色与链上角色一致性" width="980px" destroy-on-close>
      <el-table :data="consistencyRows" border stripe max-height="420">
        <el-table-column prop="userId" label="用户ID" width="90" />
        <el-table-column prop="username" label="账号" min-width="120" />
        <el-table-column prop="systemRoleKey" label="系统角色" width="120" />
        <el-table-column prop="expectedContractRole" label="期望链上角色" width="120" align="center" />
        <el-table-column prop="chainRole" label="链上角色" width="100" align="center" />
        <el-table-column label="一致性" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.consistent ? 'success' : 'danger'" size="small">
              {{ row.consistent ? '一致' : '不一致' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="备注" min-width="220" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button @click="consistencyDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import {
  getUserList,
  updateUser,
  deleteUser,
  toggleUserStatus,
  assignUserRole,
  getRoleConsistency,
  repairRoleConsistency,
  initUserBlockchainAccount
} from '@/api/system'
import { ElMessage, ElMessageBox } from 'element-plus'
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

const consistencyLoading = ref(false)
const repairLoading = ref(false)
const consistencyDialogVisible = ref(false)
const consistencyRows = ref([])
const consistencyParams = reactive({
  roleKey: '',
  limit: 50,
  onlyInconsistent: true
})

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
    const oldRoleKey = tableData.value.find(x => x.id === editForm.id)?.roleKey
    await updateUser(editForm.id, editForm)
    if (editForm.roleKey && editForm.roleKey !== oldRoleKey) {
      await assignUserRole(editForm.id, editForm.roleKey)
    }
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

async function handleInitChainAccount(row) {
  try {
    await ElMessageBox.confirm(
      `将为用户 ${row.username} 初始化链上账户，是否继续？`,
      '初始化链上账户',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await initUserBlockchainAccount(row.id)
    ElMessage.success('链上账户初始化成功')
    fetchList()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '初始化链上账户失败')
  }
}

async function handleCheckConsistency() {
  consistencyLoading.value = true
  try {
    const res = await getRoleConsistency({
      roleKey: consistencyParams.roleKey || undefined,
      limit: consistencyParams.limit
    })
    consistencyRows.value = res.data || []
    consistencyDialogVisible.value = true
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '一致性检查失败')
  } finally {
    consistencyLoading.value = false
  }
}

async function handleRepairConsistency() {
  try {
    await ElMessageBox.confirm(
      '将按当前筛选批量修复链上角色，是否继续？',
      '批量修复确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  repairLoading.value = true
  try {
    const res = await repairRoleConsistency({
      roleKey: consistencyParams.roleKey || undefined,
      limit: consistencyParams.limit,
      onlyInconsistent: consistencyParams.onlyInconsistent
    })
    const data = res.data || {}
    const failed = (data.details || []).filter(x => x && x.action === 'FAILED')
    ElMessage.success(`修复完成：扫描${data.scanned || 0}，修复${data.repaired || 0}，失败${data.failed || 0}`)
    if (failed.length > 0) {
      const first = failed[0]
      await ElMessageBox.alert(
        `失败用户数：${failed.length}\n首条失败：userId=${first.userId || '-'}，原因=${first.error || '未知错误'}`,
        '批量修复失败详情',
        { type: 'error' }
      )
    }
    await handleCheckConsistency()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '批量修复失败')
  } finally {
    repairLoading.value = false
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
