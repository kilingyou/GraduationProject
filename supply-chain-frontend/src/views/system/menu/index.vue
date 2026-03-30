<template>
  <div class="menu-manage">
    <el-card shadow="never" class="table-card">
      <div class="table-toolbar">
        <el-button type="primary" :icon="Plus" @click="handleAdd(null)">新增顶级菜单</el-button>
      </div>

      <el-table
        :data="tableData"
        v-loading="loading"
        row-key="id"
        border
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        default-expand-all
      >
        <el-table-column prop="menuName" label="菜单名称" min-width="180" />
        <el-table-column prop="icon" label="图标" width="80" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.icon"><component :is="row.icon" /></el-icon>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路径" min-width="140" show-overflow-tooltip />
        <el-table-column prop="component" label="组件" min-width="160" show-overflow-tooltip />
        <el-table-column prop="perms" label="权限标识" min-width="140" show-overflow-tooltip />
        <el-table-column prop="menuType" label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.menuType === 0" size="small">目录</el-tag>
            <el-tag v-else-if="row.menuType === 1" type="success" size="small">菜单</el-tag>
            <el-tag v-else type="warning" size="small">按钮</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="70" align="center" />
        <el-table-column label="操作" width="220" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleAdd(row)" v-if="row.menuType !== 2">
              新增
            </el-button>
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该菜单？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="580px" destroy-on-close>
      <el-form ref="menuFormRef" :model="menuForm" :rules="menuRules" label-width="100px">
        <el-form-item label="上级菜单">
          <el-tree-select
            v-model="menuForm.parentId"
            :data="parentOptions"
            :props="{ label: 'menuName', value: 'id', children: 'children' }"
            placeholder="无（顶级菜单）"
            clearable
            check-strictly
            :render-after-expand="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单类型" prop="menuType">
          <el-radio-group v-model="menuForm.menuType">
            <el-radio :value="0">目录</el-radio>
            <el-radio :value="1">菜单</el-radio>
            <el-radio :value="2">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="menuForm.menuName" placeholder="请输入菜单名称" />
        </el-form-item>
        <el-form-item label="图标" v-if="menuForm.menuType !== 2">
          <el-input v-model="menuForm.icon" placeholder="Element Plus 图标名" />
        </el-form-item>
        <el-form-item label="路由路径" v-if="menuForm.menuType !== 2" prop="path">
          <el-input v-model="menuForm.path" placeholder="如: user 或 /system" />
        </el-form-item>
        <el-form-item label="组件路径" v-if="menuForm.menuType === 1">
          <el-input v-model="menuForm.component" placeholder="如: system/user/index" />
        </el-form-item>
        <el-form-item label="权限标识" v-if="menuForm.menuType === 2">
          <el-input v-model="menuForm.perms" placeholder="如: system:user:list" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="menuForm.sortOrder" :min="0" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { getMenuList, addMenu, updateMenu, deleteMenu } from '@/api/system'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const loading = ref(false)
const tableData = ref([])

const dialogVisible = ref(false)
const dialogTitle = ref('新增菜单')
const submitLoading = ref(false)
const menuFormRef = ref()
const isEdit = ref(false)

const menuForm = reactive({
  id: null,
  parentId: null,
  menuName: '',
  icon: '',
  path: '',
  component: '',
  perms: '',
  menuType: 0,
  sortOrder: 0
})

const menuRules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
  path: [{ required: true, message: '请输入路由路径', trigger: 'blur' }]
}

const parentOptions = computed(() => {
  return [{ id: 0, menuName: '顶级菜单', children: tableData.value }]
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getMenuList()
    tableData.value = res.data || []
  } catch {
    ElMessage.error('获取菜单列表失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(menuForm, {
    id: null,
    parentId: null,
    menuName: '',
    icon: '',
    path: '',
    component: '',
    perms: '',
    menuType: 0,
    sortOrder: 0
  })
}

function handleAdd(parentRow) {
  resetForm()
  isEdit.value = false
  dialogTitle.value = '新增菜单'
  if (parentRow) {
    menuForm.parentId = parentRow.id
  }
  dialogVisible.value = true
}

function handleEdit(row) {
  resetForm()
  isEdit.value = true
  dialogTitle.value = '编辑菜单'
  Object.assign(menuForm, {
    id: row.id,
    parentId: row.parentId,
    menuName: row.menuName,
    icon: row.icon,
    path: row.path,
    component: row.component,
    perms: row.perms,
    menuType: row.menuType,
    sortOrder: row.sortOrder
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await menuFormRef.value.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateMenu(menuForm.id, menuForm)
      ElMessage.success('更新成功')
    } else {
      await addMenu(menuForm)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '操作失败')
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(id) {
  try {
    await deleteMenu(id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    ElMessage.error('删除失败')
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.menu-manage {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.table-card {
  border-radius: 8px;
}

.table-toolbar {
  margin-bottom: 16px;
}
</style>
