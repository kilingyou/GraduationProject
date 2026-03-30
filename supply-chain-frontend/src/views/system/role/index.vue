<template>
  <div class="role-manage">
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="roleKey" label="角色标识" min-width="120" />
        <el-table-column prop="roleName" label="角色名称" min-width="140" />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleAssignMenu(row)">
              分配权限
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="menuDialogVisible" title="分配菜单权限" width="480px" destroy-on-close>
      <div class="menu-tree-wrap" v-loading="treeLoading">
        <el-tree
          ref="menuTreeRef"
          :data="menuTree"
          :props="treeProps"
          show-checkbox
          node-key="id"
          :default-checked-keys="checkedMenuIds"
          :default-expand-all="true"
        />
      </div>
      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="assignLoading" @click="handleSubmitAssign">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getRoleList, getMenuTree, assignMenus } from '@/api/system'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const tableData = ref([])

const menuDialogVisible = ref(false)
const treeLoading = ref(false)
const assignLoading = ref(false)
const menuTreeRef = ref()
const menuTree = ref([])
const checkedMenuIds = ref([])
const currentRoleId = ref(null)

const treeProps = {
  label: 'menuName',
  children: 'children'
}

async function fetchRoles() {
  loading.value = true
  try {
    const res = await getRoleList()
    tableData.value = res.data || []
  } catch {
    ElMessage.error('获取角色列表失败')
  } finally {
    loading.value = false
  }
}

async function handleAssignMenu(row) {
  currentRoleId.value = row.id
  checkedMenuIds.value = row.menuIds || []
  menuDialogVisible.value = true
  treeLoading.value = true
  try {
    const res = await getMenuTree()
    menuTree.value = res.data || []
  } catch {
    ElMessage.error('获取菜单树失败')
  } finally {
    treeLoading.value = false
  }
}

async function handleSubmitAssign() {
  const checkedKeys = menuTreeRef.value.getCheckedKeys()
  const halfCheckedKeys = menuTreeRef.value.getHalfCheckedKeys()
  const allIds = [...checkedKeys, ...halfCheckedKeys]

  assignLoading.value = true
  try {
    await assignMenus(currentRoleId.value, allIds)
    ElMessage.success('权限分配成功')
    menuDialogVisible.value = false
    fetchRoles()
  } catch {
    ElMessage.error('权限分配失败')
  } finally {
    assignLoading.value = false
  }
}

onMounted(() => {
  fetchRoles()
})
</script>

<style scoped lang="scss">
.role-manage {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.table-card {
  border-radius: 8px;
}

.menu-tree-wrap {
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
}
</style>
