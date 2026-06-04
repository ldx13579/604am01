<template>
  <div class="audit-log">
    <h2>审计日志</h2>

    <!-- Filter Bar -->
    <el-form :inline="true" class="filter-bar">
      <el-form-item label="用户">
        <el-input v-model="filters.username" placeholder="用户名" clearable style="width: 140px;" />
      </el-form-item>
      <el-form-item label="操作类型">
        <el-select v-model="filters.action" placeholder="选择操作" clearable style="width: 160px;">
          <el-option label="CREATE_CONFIG" value="CREATE_CONFIG" />
          <el-option label="UPDATE_CONFIG" value="UPDATE_CONFIG" />
          <el-option label="DELETE_CONFIG" value="DELETE_CONFIG" />
          <el-option label="ROLLBACK" value="ROLLBACK" />
          <el-option label="CREATE_USER" value="CREATE_USER" />
          <el-option label="DELETE_USER" value="DELETE_USER" />
          <el-option label="ASSIGN_ROLE" value="ASSIGN_ROLE" />
          <el-option label="REMOVE_ROLE" value="REMOVE_ROLE" />
        </el-select>
      </el-form-item>
      <el-form-item label="环境">
        <el-select v-model="filters.environment" placeholder="选择环境" clearable style="width: 110px;">
          <el-option label="dev" value="dev" />
          <el-option label="test" value="test" />
          <el-option label="prod" value="prod" />
        </el-select>
      </el-form-item>
      <el-form-item label="时间范围">
        <el-date-picker
          v-model="filters.timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          style="width: 340px;"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </el-form-item>
    </el-form>

    <!-- Results Table -->
    <el-table :data="logs" border stripe style="width: 100%" @row-click="handleRowClick">
      <el-table-column prop="timestamp" label="时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.timestamp) }}
        </template>
      </el-table-column>
      <el-table-column prop="username" label="用户" width="120" />
      <el-table-column prop="action" label="操作" width="150" />
      <el-table-column prop="resourceType" label="资源类型" width="120" />
      <el-table-column prop="resourceId" label="资源ID" width="120" />
      <el-table-column prop="environment" label="环境" width="80" />
      <el-table-column prop="result" label="结果" width="80">
        <template #default="{ row }">
          <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
            {{ row.result }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>

    <!-- Expanded Detail -->
    <el-dialog v-model="showDetail" title="变更详情" width="700px">
      <div v-if="selectedLog">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="时间">{{ formatTime(selectedLog.timestamp) }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ selectedLog.username }}</el-descriptions-item>
          <el-descriptions-item label="操作">{{ selectedLog.action }}</el-descriptions-item>
          <el-descriptions-item label="结果">{{ selectedLog.result }}</el-descriptions-item>
          <el-descriptions-item label="资源类型">{{ selectedLog.resourceType }}</el-descriptions-item>
          <el-descriptions-item label="资源ID">{{ selectedLog.resourceId }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="selectedLog.oldValue" style="margin-top: 16px;">
          <h4>旧值 (old_value)</h4>
          <pre class="value-block">{{ selectedLog.oldValue }}</pre>
        </div>
        <div v-if="selectedLog.newValue" style="margin-top: 16px;">
          <h4>新值 (new_value)</h4>
          <pre class="value-block">{{ selectedLog.newValue }}</pre>
        </div>
      </div>
    </el-dialog>

    <!-- Pagination -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSearch"
        @current-change="handleSearch"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { queryAuditLogs } from '../api/audit'

const logs = ref([])
const showDetail = ref(false)
const selectedLog = ref(null)

const filters = reactive({
  username: '',
  action: '',
  environment: '',
  timeRange: null
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return d.toLocaleString('zh-CN')
}

async function handleSearch() {
  try {
    const params = {
      page: pagination.page - 1,
      size: pagination.size
    }
    if (filters.username) params.username = filters.username
    if (filters.action) params.action = filters.action
    if (filters.environment) params.environment = filters.environment
    if (filters.timeRange && filters.timeRange.length === 2) {
      params.startTime = filters.timeRange[0].toISOString()
      params.endTime = filters.timeRange[1].toISOString()
    }
    const res = await queryAuditLogs(params)
    logs.value = res.data.content || res.data
    pagination.total = res.data.totalElements || logs.value.length
  } catch (e) {
    ElMessage.error('查询审计日志失败')
  }
}

function handleRowClick(row) {
  selectedLog.value = row
  showDetail.value = true
}

onMounted(() => {
  handleSearch()
})
</script>

<style scoped>
.audit-log h2 {
  margin-top: 0;
  margin-bottom: 20px;
}

.filter-bar {
  margin-bottom: 16px;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.value-block {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow-y: auto;
}
</style>
