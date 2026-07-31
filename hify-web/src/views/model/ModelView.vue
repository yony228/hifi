<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getHealth } from '@/api/health'

const backendStatus = ref<'loading' | 'connected' | 'error'>('loading')

onMounted(async () => {
  try {
    await getHealth()
    backendStatus.value = 'connected'
  } catch {
    backendStatus.value = 'error'
  }
})
</script>

<template>
  <div class="page-placeholder">
    <h1>模型管理</h1>
    <p>模型管理页面开发中...</p>

    <div class="health-status">
      <span v-if="backendStatus === 'loading'" class="loading">⏳ 检测后端连接...</span>
      <span v-else-if="backendStatus === 'connected'" class="connected">✅ 后端已连接：Hify is running</span>
      <span v-else class="error">❌ 后端未连接</span>
    </div>
  </div>
</template>

<style scoped>
.page-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--el-text-color-secondary);
}
.page-placeholder h1 {
  font-size: 24px;
  margin-bottom: 8px;
}

.health-status {
  margin-top: 16px;
  font-size: 14px;
}
.connected {
  color: #67c23a;
}
.error {
  color: #f56c6c;
}
.loading {
  color: var(--el-text-color-secondary);
}
</style>
