<template>
  <div class="config-list">
    <div class="toolbar">
      <el-tabs v-model="currentEnv" @tab-change="loadConfigs">
        <el-tab-pane label="开发环境 (dev)" name="dev" />
        <el-tab-pane label="测试环境 (test)" name="test" />
        <el-tab-pane label="生产环境 (prod)" name="prod" />
      </el-tabs>
      <el-button type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon>
        新增配置
      </el-button>
      <el-button :type="showZombiesOnly ? 'warning' : 'default'" @click="toggleZombieFilter">
        僵尸配置
      </el-button>
    </div>

    <el-table :data="configs" stripe border style="width: 100%" v-loading="loading">
      <el-table-column prop="configKey" label="配置键" min-width="200">
        <template #default="{ row }">
          {{ row.configKey }}
          <el-tag v-if="row.zombie" type="warning" size="small" style="margin-left:8px">
            僵尸
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="configValue" label="配置值" min-width="250" show-overflow-tooltip />
      <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
      <el-table-column prop="version" label="版本" width="80" align="center" />
      <el-table-column prop="updatedAt" label="更新时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.updatedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="showEditDialog(row)">编辑</el-button>
          <el-button size="small" type="info" @click="goToHistory(row)">历史</el-button>
          <el-button size="small" type="warning" @click="showTestLogs(row)">测试</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑配置' : '新增配置'"
      width="500px"
    >
      <el-form :model="form" label-width="80px" :rules="rules" ref="formRef">
        <el-form-item label="配置键" prop="configKey">
          <el-input v-model="form.configKey" :disabled="isEdit" placeholder="例如: app.name" />
        </el-form-item>
        <el-form-item label="配置值" prop="configValue">
          <el-input
            v-model="form.configValue"
            type="textarea"
            :rows="3"
            placeholder="配置值"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="配置项描述（可选）" />
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

    <!-- Test Log Drawer -->
    <el-drawer v-model="testLogDrawerVisible" title="变更测试日志" size="500px">
      <el-table :data="testLogs" stripe border v-loading="testLogLoading">
        <el-table-column prop="testResult" label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.testResult === 'PASS' ? 'success' : row.testResult === 'FAIL' ? 'danger' : 'warning'" size="small">
              {{ row.testResult }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="newValue" label="测试值" show-overflow-tooltip />
        <el-table-column prop="durationMs" label="耗时(ms)" width="90" />
        <el-table-column prop="rolledBack" label="已回滚" width="80">
          <template #default="{ row }">
            {{ row.rolledBack ? '是' : '否' }}
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误信息" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getConfigs, createConfig, updateConfig, deleteConfig, getChangeTestLogs } from '../api/config'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const currentEnv = ref('dev')
const allConfigs = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const editId = ref(null)
const formRef = ref(null)
const showZombiesOnly = ref(false)

const configs = computed(() => {
  if (showZombiesOnly.value) {
    return allConfigs.value.filter(c => c.zombie)
  }
  return allConfigs.value
})

const form = ref({
  configKey: '',
  configValue: '',
  description: '',
  environment: 'dev',
  namespace: 'default'
})

const rules = {
  configKey: [{ required: true, message: '请输入配置键', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入配置值', trigger: 'blur' }]
}

const loadConfigs = async () => {
  loading.value = true
  try {
    const res = await getConfigs(currentEnv.value)
    allConfigs.value = res.data
  } catch (e) {
    ElMessage.error('加载配置失败')
  } finally {
    loading.value = false
  }
}

const toggleZombieFilter = () => {
  showZombiesOnly.value = !showZombiesOnly.value
}

const showCreateDialog = () => {
  isEdit.value = false
  editId.value = null
  form.value = { configKey: '', configValue: '', description: '', environment: currentEnv.value, namespace: 'default' }
  dialogVisible.value = true
}

const showEditDialog = (row) => {
  isEdit.value = true
  editId.value = row.id
  form.value = { configKey: row.configKey, configValue: row.configValue, description: row.description, environment: row.environment, namespace: row.namespace, version: row.version }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateConfig(editId.value, { configValue: form.value.configValue, description: form.value.description, expectedVersion: form.value.version })
      ElMessage.success('更新成功')
    } else {
      await createConfig(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadConfigs()
  } catch (e) {
    ElMessage.error(e.response?.data?.error || '操作失败')
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除配置 "${row.configKey}" ?`, '确认删除', { type: 'warning' })
    await deleteConfig(row.id)
    ElMessage.success('删除成功')
    loadConfigs()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const goToHistory = (row) => {
  router.push(`/history/${row.id}`)
}

const testLogDrawerVisible = ref(false)
const testLogs = ref([])
const testLogLoading = ref(false)

const showTestLogs = async (row) => {
  testLogDrawerVisible.value = true
  testLogLoading.value = true
  try {
    const res = await getChangeTestLogs(row.id)
    testLogs.value = res.data
  } catch (e) {
    ElMessage.error('加载测试日志失败')
  } finally {
    testLogLoading.value = false
  }
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(loadConfigs)
</script>

<style scoped>
.config-list {
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
</style>
