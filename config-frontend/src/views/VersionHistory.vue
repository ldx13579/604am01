<template>
  <div class="version-history">
    <div class="header">
      <el-button @click="$router.back()">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
      <h2>版本历史 - {{ configKey }}</h2>
    </div>

    <el-timeline v-loading="loading">
      <el-timeline-item
        v-for="item in history"
        :key="item.id"
        :timestamp="formatTime(item.createdAt)"
        :type="getTimelineType(item.operation)"
        placement="top"
      >
        <el-card>
          <div class="version-item">
            <div class="version-info">
              <el-tag :type="getTagType(item.operation)" size="small">
                {{ item.operation }}
              </el-tag>
              <span class="version-num">版本 {{ item.version }}</span>
            </div>
            <div class="version-value">
              <strong>值:</strong> {{ item.configValue }}
            </div>
            <div class="version-actions" v-if="item.operation !== 'DELETE'">
              <el-button
                size="small"
                type="warning"
                @click="handleRollback(item)"
                :loading="rollingBack"
              >
                回滚到此版本
              </el-button>
            </div>
          </div>
        </el-card>
      </el-timeline-item>
    </el-timeline>

    <el-empty v-if="!loading && history.length === 0" description="暂无版本历史" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getVersionHistory, rollbackConfig } from '../api/config'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const configId = route.params.id
const history = ref([])
const configKey = ref('')
const loading = ref(false)
const rollingBack = ref(false)

const loadHistory = async () => {
  loading.value = true
  try {
    const res = await getVersionHistory(configId)
    history.value = res.data
    if (res.data.length > 0) {
      configKey.value = res.data[0].configKey
    }
  } catch (e) {
    ElMessage.error('加载版本历史失败')
  } finally {
    loading.value = false
  }
}

const handleRollback = async (item) => {
  try {
    await ElMessageBox.confirm(
      `确定回滚到版本 ${item.version}？\n配置值将恢复为: "${item.configValue}"`,
      '确认回滚',
      { type: 'warning' }
    )
    rollingBack.value = true
    await rollbackConfig(configId, item.version)
    ElMessage.success('回滚成功')
    loadHistory()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('回滚失败')
  } finally {
    rollingBack.value = false
  }
}

const getTimelineType = (op) => {
  const map = { CREATE: 'success', UPDATE: 'primary', ROLLBACK: 'warning', DELETE: 'danger' }
  return map[op] || 'info'
}

const getTagType = (op) => {
  const map = { CREATE: 'success', UPDATE: '', ROLLBACK: 'warning', DELETE: 'danger' }
  return map[op] || 'info'
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(loadHistory)
</script>

<style scoped>
.version-history {
  background: white;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.header h2 {
  margin: 0;
  font-size: 18px;
}

.version-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.version-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.version-num {
  color: #666;
  font-size: 13px;
}

.version-value {
  font-size: 14px;
  color: #333;
  word-break: break-all;
}

.version-actions {
  margin-top: 4px;
}
</style>
