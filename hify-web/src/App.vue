<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  ChatDotRound,
  Cpu,
  Connection,
  Document,
  Tools,
  Monitor,
  Tickets,
  Setting,
} from '@element-plus/icons-vue'

const route = useRoute()

const menuItems = [
  { path: '/chat', title: 'Chat 对话', icon: ChatDotRound },
  { path: '/agent', title: 'Agent 配置', icon: Cpu },
  { path: '/workflow', title: 'Workflow 编排', icon: Connection },
  { path: '/knowledge', title: '知识库管理', icon: Document },
  { path: '/tool', title: '工具配置', icon: Tools },
  { path: '/model', title: '模型管理', icon: Monitor },
  { path: '/log', title: '日志查看', icon: Tickets },
  { path: '/settings', title: '系统设置', icon: Setting },
]

const isCollapse = ref(false)
</script>

<template>
  <el-container class="app-layout">
    <!-- 左侧菜单栏 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="app-aside">
      <div class="logo">
        <span v-if="!isCollapse">Hify</span>
        <span v-else>H</span>
      </div>

      <el-menu
        :default-active="route.path"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>

      <div class="collapse-btn" @click="isCollapse = !isCollapse">
        <el-icon :size="18">
          <component :is="isCollapse ? 'DArrowRight' : 'DArrowLeft'" />
        </el-icon>
      </div>
    </el-aside>

    <!-- 右侧内容区 -->
    <el-container>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-layout {
  height: 100vh;
}

.app-aside {
  display: flex;
  flex-direction: column;
  background-color: var(--el-menu-bg-color);
  border-right: 1px solid var(--el-border-color-light);
  transition: width 0.3s;
  overflow: hidden;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 700;
  color: var(--el-color-primary);
  border-bottom: 1px solid var(--el-border-color-light);
  flex-shrink: 0;
}

.el-menu {
  flex: 1;
  border-right: none;
}

.collapse-btn {
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--el-text-color-secondary);
  border-top: 1px solid var(--el-border-color-light);
  flex-shrink: 0;
  transition: color 0.2s;
}
.collapse-btn:hover {
  color: var(--el-color-primary);
}

.el-main {
  background-color: var(--el-bg-color-page);
  padding: 24px;
}
</style>
