<template>
  <div class="settings-container">
    <div class="settings-header">
      <h2>系统设置</h2>
      <p class="settings-desc">管理系统的项目、用户和组织架构</p>
    </div>
    <el-row :gutter="20" class="settings-cards">
      <el-col :span="8">
        <el-card shadow="hover" class="settings-card" @click="$router.push('/settings/projects')">
          <div class="card-content">
            <el-icon :size="48" color="#409EFF"><Folder /></el-icon>
            <h3>项目管理</h3>
            <p>创建、编辑和管理项目，配置项目成员</p>
            <el-button type="primary" text>进入管理 &rarr;</el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="settings-card" @click="$router.push('/settings/users')">
          <div class="card-content">
            <el-icon :size="48" color="#67C23A"><User /></el-icon>
            <h3>用户管理</h3>
            <p>管理系统用户、角色权限和账号状态</p>
            <el-button type="success" text>进入管理 &rarr;</el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="settings-card" @click="$router.push('/settings/org')">
          <div class="card-content">
            <el-icon :size="48" color="#E6A23C"><OfficeBuilding /></el-icon>
            <h3>组织架构</h3>
            <p>管理区域、部门和岗位的层级结构</p>
            <el-button type="warning" text>进入管理 &rarr;</el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="settings-card" @click="$router.push('/settings/requirements')">
          <div class="card-content">
            <el-icon :size="48" color="#909399"><Setting /></el-icon>
            <h3>需求配置</h3>
            <p>管理系统需求类型和优先级的配置</p>
            <el-button text>进入管理 &rarr;</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <router-view />
  </div>
</template>

<script setup lang="ts">
import { Folder, User, OfficeBuilding, Setting } from '@element-plus/icons-vue'
</script>

<style lang="scss" scoped>
.settings-container {
  padding: 20px;
}

.settings-header {
  margin-bottom: 24px;

  h2 {
    margin: 0 0 8px;
    font-size: 22px;
    color: #303133;
  }

  .settings-desc {
    margin: 0;
    color: #909399;
    font-size: 14px;
  }
}

.settings-cards {
  .settings-card {
    cursor: pointer;
    transition: transform 0.2s;

    &:hover {
      transform: translateY(-4px);
    }

    :deep(.el-card__body) {
      padding: 24px;
    }
  }

  .card-content {
    text-align: center;

    h3 {
      margin: 16px 0 8px;
      font-size: 18px;
      color: #303133;
    }

    p {
      margin: 0 0 16px;
      color: #909399;
      font-size: 13px;
    }
  }
}
</style>
