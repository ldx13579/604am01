<template>
  <div id="app">
    <el-container>
      <el-header>
        <div class="header-content">
          <h1>分布式配置中心</h1>
          <span class="version-info">Distributed Config Center</span>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
</script>

<style>
body {
  margin: 0;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background-color: #f5f7fa;
}

.el-header {
  background-color: #409eff;
  color: white;
  display: flex;
  align-items: center;
  padding: 0 20px;
}

.header-content {
  display: flex;
  align-items: baseline;
  gap: 16px;
}

.header-content h1 {
  margin: 0;
  font-size: 20px;
}

.version-info {
  font-size: 13px;
  opacity: 0.8;
}

.el-main {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}
</style>
