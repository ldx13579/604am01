<template>
  <div class="grayscale-page">
    <div class="toolbar">
      <el-tabs v-model="currentEnv" @tab-change="loadData">
        <el-tab-pane label="开发环境 (dev)" name="dev" />
        <el-tab-pane label="测试环境 (test)" name="test" />
        <el-tab-pane label="生产环境 (prod)" name="prod" />
      </el-tabs>
      <el-button type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon>
        新增灰度规则
      </el-button>
    </div>

    <!-- Grayscale Rules Table -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <span>灰度规则列表</span>
      </template>
      <el-table :data="rules" stripe border v-loading="loadingRules">
        <el-table-column prop="ruleName" label="规则名称" min-width="150" />
        <el-table-column prop="targetVersion" label="目标版本" width="100" align="center" />
        <el-table-column prop="ipList" label="灰度IP列表" min-width="250" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'ACTIVE'">
              <el-button size="small" @click="showEditDialog(row)">编辑</el-button>
              <el-button size="small" type="success" @click="handleFullRelease(row)">全量发布</el-button>
              <el-button size="small" type="danger" @click="handleCancel(row)">取消</el-button>
            </template>
            <el-tag v-else :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Client Instances Table -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="client-header">
          <span>客户端实例状态</span>
          <el-button size="small" :icon="Refresh" @click="loadClients" :loading="loadingClients">刷新</el-button>
        </div>
      </template>
      <el-table :data="clients" stripe border v-loading="loadingClients">
        <el-table-column prop="clientIp" label="客户端IP" width="150" />
        <el-table-column prop="currentVersion" label="当前版本" width="100" align="center" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ONLINE' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="灰度标记" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.inGrayscale" type="warning">灰度中</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastHeartbeat" label="最后心跳" min-width="180">
          <template #default="{ row }">
            {{ formatTime(row.lastHeartbeat) }}
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loadingClients && clients.length === 0" description="暂无客户端连接" />
    </el-card>

    <!-- Create/Edit Rule Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑灰度规则' : '新增灰度规则'"
      width="550px"
    >
      <el-form :model="form" label-width="100px" :rules="formRules" ref="formRef">
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="form.ruleName" placeholder="例如: 新版数据库连接池测试" />
        </el-form-item>
        <el-form-item label="目标版本" prop="targetVersion">
          <el-input-number v-model="form.targetVersion" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="灰度IP列表" prop="ipList">
          <el-input
            v-model="form.ipList"
            type="textarea"
            :rows="4"
            placeholder="多个IP用逗号分隔，例如: 192.168.1.10, 192.168.1.11"
          />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="环境">
          <el-select v-model="form.environment" style="width: 100%">
            <el-option label="dev" value="dev" />
            <el-option label="test" value="test" />
            <el-option label="prod" value="prod" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import {
  getGrayscaleRules, createGrayscaleRule, updateGrayscaleRule,
  fullReleaseRule, cancelRule, getClients
} from '../api/config'
import { ElMessage, ElMessageBox } from 'element-plus'

const currentEnv = ref('dev')
const rules = ref([])
const clients = ref([])
const loadingRules = ref(false)
const loadingClients = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const submitting = ref(false)
const formRef = ref(null)
let refreshTimer = null

const form = ref({
  ruleName: '',
  targetVersion: 1,
  ipList: '',
  environment: 'dev',
  namespace: 'default'
})

const formRules = {
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  targetVersion: [{ required: true, message: '请输入目标版本', trigger: 'blur' }],
  ipList: [{ required: true, message: '请输入灰度IP列表', trigger: 'blur' }]
}

const loadData = () => {
  loadRules()
  loadClients()
}

const loadRules = async () => {
  loadingRules.value = true
  try {
    const res = await getGrayscaleRules(currentEnv.value)
    rules.value = res.data
  } catch (e) {
    ElMessage.error('加载灰度规则失败')
  } finally {
    loadingRules.value = false
  }
}

const loadClients = async () => {
  loadingClients.value = true
  try {
    const res = await getClients(currentEnv.value)
    clients.value = res.data
  } catch (e) {
    ElMessage.error('加载客户端列表失败')
  } finally {
    loadingClients.value = false
  }
}

const showCreateDialog = () => {
  isEdit.value = false
  editId.value = null
  form.value = { ruleName: '', targetVersion: 1, ipList: '', environment: currentEnv.value, namespace: 'default' }
  dialogVisible.value = true
}

const showEditDialog = (row) => {
  isEdit.value = true
  editId.value = row.id
  form.value = {
    ruleName: row.ruleName,
    targetVersion: row.targetVersion,
    ipList: row.ipList,
    environment: row.environment,
    namespace: row.namespace
  }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateGrayscaleRule(editId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await createGrayscaleRule(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadRules()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const handleFullRelease = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定将规则 "${row.ruleName}" 全量发布？所有客户端将拉取版本 ${row.targetVersion}`,
      '全量发布确认',
      { type: 'warning' }
    )
    await fullReleaseRule(row.id)
    ElMessage.success('全量发布成功')
    loadRules()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

const handleCancel = async (row) => {
  try {
    await ElMessageBox.confirm(`确定取消规则 "${row.ruleName}" ？`, '取消确认', { type: 'warning' })
    await cancelRule(row.id)
    ElMessage.success('规则已取消')
    loadRules()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

const statusType = (status) => {
  switch (status) {
    case 'ACTIVE': return 'warning'
    case 'FULL_RELEASE': return 'success'
    case 'CANCELLED': return 'info'
    default: return ''
  }
}

const statusLabel = (status) => {
  switch (status) {
    case 'ACTIVE': return '灰度中'
    case 'FULL_RELEASE': return '已全量'
    case 'CANCELLED': return '已取消'
    default: return status
  }
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(() => {
  loadData()
  refreshTimer = setInterval(loadClients, 10000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style scoped>
.grayscale-page {
  background: white;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.section-card {
  margin-bottom: 20px;
}

.client-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
