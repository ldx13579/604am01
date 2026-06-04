<template>
  <div id="app">
    <el-container>
      <el-header v-if="auth.state.isAuthenticated">
        <div class="header-content">
          <h1>分布式配置中心</h1>
          <nav class="header-nav">
            <router-link to="/" class="nav-link">配置管理</router-link>
            <router-link to="/grayscale" class="nav-link">灰度发布</router-link>
            <router-link v-if="auth.isAdmin()" to="/users" class="nav-link">权限管理</router-link>
            <router-link v-if="auth.isAdmin()" to="/audit" class="nav-link">审计日志</router-link>
            <router-link to="/validation" class="nav-link">校验脚本</router-link>
          </nav>
          <div class="header-user">
            <span class="username">{{ auth.state.user?.displayName || auth.state.user?.username }}</span>
            <el-button type="default" size="small" @click="handleLogout">退出</el-button>
          </div>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import auth from './store/auth'

const router = useRouter()

function handleLogout() {
  auth.logout()
  router.push('/login')
}
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
  align-items: center;
  gap: 32px;
  width: 100%;
  justify-content: flex-start;
}

.header-user {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-user .username {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
}

.header-content h1 {
  margin: 0;
  font-size: 20px;
}

.header-nav {
  display: flex;
  gap: 16px;
}

.nav-link {
  color: rgba(255, 255, 255, 0.8);
  text-decoration: none;
  font-size: 14px;
  padding: 4px 12px;
  border-radius: 4px;
  transition: all 0.2s;
}

.nav-link:hover,
.nav-link.router-link-active {
  color: white;
  background: rgba(255, 255, 255, 0.2);
}

.el-main {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}
</style>
