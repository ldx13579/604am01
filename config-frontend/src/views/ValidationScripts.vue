<template>
  <div class="validation-scripts">
    <div class="page-header">
      <h2>配置校验脚本</h2>
      <el-button type="primary" @click="openCreateDialog">新增脚本</el-button>
    </div>

    <!-- Environment Tabs -->
    <el-tabs v-model="currentEnv" @tab-change="loadScripts">
      <el-tab-pane label="dev" name="dev" />
      <el-tab-pane label="test" name="test" />
      <el-tab-pane label="prod" name="prod" />
    </el-tabs>

    <!-- Scripts Table -->
    <el-table :data="scripts" border stripe style="width: 100%">
      <el-table-column prop="scriptName" label="脚本名" min-width="180" />
      <el-table-column prop="enabled" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? 'enabled' : 'disabled' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.updatedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="openEditDialog(row)">编辑</el-button>
          <el-button size="small" type="warning" @click="openTestDialog(row)">测试</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="showEditDialog" :title="isEdit ? '编辑脚本' : '新增脚本'" width="700px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="脚本名">
          <el-input v-model="editForm.scriptName" placeholder="请输入脚本名" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="脚本内容">
          <el-input
            v-model="editForm.scriptContent"
            type="textarea"
            :rows="12"
            placeholder="请输入校验脚本内容"
            style="font-family: 'Courier New', monospace;"
          />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="editForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" :loading="saveLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- Test Dialog -->
    <el-dialog v-model="showTestDialog" title="测试脚本" width="550px">
      <el-form :model="testForm" label-width="100px">
        <el-form-item label="配置Key">
          <el-input v-model="testForm.configKey" placeholder="请输入测试配置Key" />
        </el-form-item>
        <el-form-item label="配置Value">
          <el-input
            v-model="testForm.configValue"
            type="textarea"
            :rows="4"
            placeholder="请输入测试配置Value"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="testLoading" @click="handleTest">执行测试</el-button>
        </el-form-item>
      </el-form>
      <div v-if="testResult !== null" class="test-result">
        <el-alert
          :title="testResult.valid ? '校验通过' : '校验失败'"
          :type="testResult.valid ? 'success' : 'error'"
          :description="testResult.message || ''"
          show-icon
          :closable="false"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getScripts, createScript, updateScript, deleteScript, testScript } from '../api/validation'

const currentEnv = ref('dev')
const scripts = ref([])
const showEditDialog = ref(false)
const showTestDialog = ref(false)
const isEdit = ref(false)
const saveLoading = ref(false)
const testLoading = ref(false)
const testResult = ref(null)
const currentScript = ref(null)

const editForm = reactive({
  scriptName: '',
  scriptContent: '',
  enabled: true
})

const testForm = reactive({
  configKey: '',
  configValue: ''
})

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return d.toLocaleString('zh-CN')
}

async function loadScripts() {
  try {
    const res = await getScripts(currentEnv.value)
    scripts.value = res.data
  } catch (e) {
    ElMessage.error('获取脚本列表失败')
  }
}

function openCreateDialog() {
  isEdit.value = false
  editForm.scriptName = ''
  editForm.scriptContent = ''
  editForm.enabled = true
  showEditDialog.value = true
}

function openEditDialog(row) {
  isEdit.value = true
  currentScript.value = row
  editForm.scriptName = row.scriptName
  editForm.scriptContent = row.scriptContent
  editForm.enabled = row.enabled
  showEditDialog.value = true
}

async function handleSave() {
  if (!editForm.scriptName) {
    ElMessage.warning('请输入脚本名')
    return
  }
  if (!editForm.scriptContent) {
    ElMessage.warning('请输入脚本内容')
    return
  }

  saveLoading.value = true
  try {
    const data = {
      scriptName: editForm.scriptName,
      scriptContent: editForm.scriptContent,
      enabled: editForm.enabled,
      environment: currentEnv.value
    }
    if (isEdit.value) {
      await updateScript(currentScript.value.id, data)
      ElMessage.success('脚本更新成功')
    } else {
      await createScript(data)
      ElMessage.success('脚本创建成功')
    }
    showEditDialog.value = false
    await loadScripts()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    saveLoading.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除脚本 "${row.scriptName}" 吗？`, '确认删除', { type: 'warning' })
    await deleteScript(row.id)
    ElMessage.success('删除成功')
    await loadScripts()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

function openTestDialog(row) {
  currentScript.value = row
  testForm.configKey = ''
  testForm.configValue = ''
  testResult.value = null
  showTestDialog.value = true
}

async function handleTest() {
  if (!testForm.configKey) {
    ElMessage.warning('请输入配置Key')
    return
  }

  testLoading.value = true
  try {
    const res = await testScript({
      scriptId: currentScript.value.id,
      configKey: testForm.configKey,
      configValue: testForm.configValue,
      environment: currentEnv.value
    })
    testResult.value = res.data
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '测试执行失败')
  } finally {
    testLoading.value = false
  }
}

onMounted(() => {
  loadScripts()
})
</script>

<style scoped>
.validation-scripts {
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

.test-result {
  margin-top: 16px;
}
</style>
