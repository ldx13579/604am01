<template>
  <div class="user-manage">
    <div class="page-header">
      <h2>用户权限管理</h2>
      <el-button type="primary" @click="showCreateDialog = true">新增用户</el-button>
    </div>

    <el-table :data="users" border stripe style="width: 100%">
      <el-table-column prop="username" label="用户名" width="150" />
      <el-table-column prop="displayName" label="显示名" width="150" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="角色列表" min-width="250">
        <template #default="{ row }">
          <el-tag
            v-for="r in row.roles"
            :key="r.id"
            size="small"
            style="margin-right: 4px; margin-bottom: 4px;"
          >
            {{ r.role }}({{ r.environment }}/{{ r.namespace }})
          </el-tag>
          <span v-if="!row.roles || row.roles.length === 0" style="color: #999;">无角色</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="openRoleDialog(row)">编辑角色</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create User Dialog -->
    <el-dialog v-model="showCreateDialog" title="新增用户" width="450px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="createForm.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="显示名" prop="displayName">
          <el-input v-model="createForm.displayName" placeholder="请输入显示名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- Role Assignment Dialog -->
    <el-dialog v-model="showRoleDialog" title="编辑角色" width="600px">
      <div v-if="currentUser">
        <h4 style="margin-top: 0;">当前角色 - {{ currentUser.username }}</h4>
        <el-table :data="currentUser.roles || []" border size="small" style="margin-bottom: 20px;">
          <el-table-column prop="role" label="角色" width="120" />
          <el-table-column prop="environment" label="环境" width="100" />
          <el-table-column prop="namespace" label="命名空间" width="120" />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button size="small" type="danger" text @click="handleRemoveRole(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <h4>添加角色</h4>
        <el-form :model="roleForm" label-width="80px" inline>
          <el-form-item label="角色">
            <el-select v-model="roleForm.role" placeholder="选择角色" style="width: 130px;">
              <el-option label="ADMIN" value="ADMIN" />
              <el-option label="DEVELOPER" value="DEVELOPER" />
              <el-option label="VIEWER" value="VIEWER" />
            </el-select>
          </el-form-item>
          <el-form-item label="环境">
            <el-select v-model="roleForm.environment" placeholder="选择环境" style="width: 110px;">
              <el-option label="dev" value="dev" />
              <el-option label="test" value="test" />
              <el-option label="prod" value="prod" />
              <el-option label="*" value="*" />
            </el-select>
          </el-form-item>
          <el-form-item label="命名空间">
            <el-input v-model="roleForm.namespace" placeholder="命名空间" style="width: 120px;" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleAssignRole">添加</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUsers, createUser, deleteUser, assignRole, removeRole } from '../api/user'

const users = ref([])
const showCreateDialog = ref(false)
const showRoleDialog = ref(false)
const createLoading = ref(false)
const currentUser = ref(null)
const createFormRef = ref(null)

const createForm = reactive({
  username: '',
  password: '',
  displayName: ''
})

const createRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, message: '密码至少6位', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入显示名', trigger: 'blur' }]
}

const roleForm = reactive({
  role: 'VIEWER',
  environment: '*',
  namespace: '*'
})

async function loadUsers() {
  try {
    const res = await getUsers()
    users.value = res.data
  } catch (e) {
    ElMessage.error('获取用户列表失败')
  }
}

async function handleCreate() {
  if (!createFormRef.value) return
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return

  createLoading.value = true
  try {
    await createUser({ ...createForm })
    ElMessage.success('用户创建成功')
    showCreateDialog.value = false
    createForm.username = ''
    createForm.password = ''
    createForm.displayName = ''
    await loadUsers()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '创建用户失败')
  } finally {
    createLoading.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除用户 "${row.username}" 吗？`, '确认删除', { type: 'warning' })
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    await loadUsers()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

function openRoleDialog(row) {
  currentUser.value = row
  showRoleDialog.value = true
}

async function handleAssignRole() {
  if (!roleForm.role) {
    ElMessage.warning('请选择角色')
    return
  }
  try {
    await assignRole(currentUser.value.id, { ...roleForm })
    ElMessage.success('角色分配成功')
    await loadUsers()
    // refresh currentUser
    currentUser.value = users.value.find(u => u.id === currentUser.value.id) || currentUser.value
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '角色分配失败')
  }
}

async function handleRemoveRole(role) {
  try {
    await removeRole(currentUser.value.id, role.id)
    ElMessage.success('角色已移除')
    await loadUsers()
    currentUser.value = users.value.find(u => u.id === currentUser.value.id) || currentUser.value
  } catch (e) {
    ElMessage.error('移除角色失败')
  }
}

onMounted(() => {
  loadUsers()
})
</script>

<style scoped>
.user-manage {
  padding: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
}
</style>
