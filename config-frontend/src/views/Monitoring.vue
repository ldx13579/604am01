<template>
  <div class="monitoring-page">
    <div class="monitoring-header">
      <h2>系统监控面板</h2>
      <p class="description">通过 Grafana 仪表盘实时监控配置中心运行状态</p>
    </div>

    <div class="metrics-summary">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-card shadow="hover">
            <template #header>配置拉取延迟</template>
            <div class="metric-value">Prometheus + Grafana</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <template #header>活跃客户端数</template>
            <div class="metric-value">实时监控</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <template #header>配置变更率</template>
            <div class="metric-value">按环境统计</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <template #header>僵尸配置数</template>
            <div class="metric-value">自动检测</div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <el-card class="grafana-card">
      <template #header>
        <div class="grafana-header">
          <span>Grafana 监控面板</span>
          <el-button type="primary" size="small" @click="openGrafana">
            在新窗口打开
          </el-button>
        </div>
      </template>
      <div class="grafana-container">
        <iframe
          :src="grafanaUrl"
          frameborder="0"
          class="grafana-iframe"
          allowfullscreen
        ></iframe>
      </div>
    </el-card>

    <el-card class="endpoints-card">
      <template #header>监控端点</template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="Prometheus 指标">
          <el-link type="primary" :href="prometheusMetricsUrl" target="_blank">
            {{ prometheusMetricsUrl }}
          </el-link>
        </el-descriptions-item>
        <el-descriptions-item label="健康检查">
          <el-link type="primary" :href="healthUrl" target="_blank">
            {{ healthUrl }}
          </el-link>
        </el-descriptions-item>
        <el-descriptions-item label="Prometheus UI">
          <el-link type="primary" href="http://localhost:9090" target="_blank">
            http://localhost:9090
          </el-link>
        </el-descriptions-item>
        <el-descriptions-item label="Grafana UI">
          <el-link type="primary" href="http://localhost:3001" target="_blank">
            http://localhost:3001 (admin / admin123)
          </el-link>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const grafanaUrl = ref('http://localhost:3001/d/config-center?orgId=1&kiosk')
const prometheusMetricsUrl = ref('http://localhost:8080/actuator/prometheus')
const healthUrl = ref('http://localhost:8080/actuator/health')

function openGrafana() {
  window.open('http://localhost:3001/d/config-center?orgId=1', '_blank')
}
</script>

<style scoped>
.monitoring-page {
  background: white;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.monitoring-header {
  margin-bottom: 20px;
}

.monitoring-header h2 {
  margin: 0 0 8px;
}

.description {
  color: #606266;
  font-size: 14px;
  margin: 0;
}

.metrics-summary {
  margin-bottom: 20px;
}

.metric-value {
  text-align: center;
  color: #409eff;
  font-weight: bold;
}

.grafana-card {
  margin-bottom: 20px;
}

.grafana-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.grafana-container {
  width: 100%;
  height: 600px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
}

.grafana-iframe {
  width: 100%;
  height: 100%;
}

.endpoints-card {
  margin-top: 20px;
}
</style>
