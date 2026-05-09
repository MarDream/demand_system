<template>
  <PageContainer
    title="组织架构管理"
    subtitle="统一维护区域、部门和岗位，建立清晰的组织层级与职责结构"
    :breadcrumb="false"
  >
    <div class="organization-page">
      <el-row :gutter="16" class="overview-row">
        <el-col :xs="24" :md="8" v-for="item in overviewCards" :key="item.name">
          <el-card
            class="overview-card"
            shadow="hover"
            :class="{ 'is-active': activeTab === item.name }"
            @click="activeTab = item.name"
          >
            <div class="overview-card__title">{{ item.title }}</div>
            <div class="overview-card__desc">{{ item.description }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-card class="organization-panel" shadow="never">
        <el-tabs v-model="activeTab" class="organization-tabs">
          <el-tab-pane label="区域管理" name="region">
            <RegionManagement />
          </el-tab-pane>
          <el-tab-pane label="部门管理" name="department">
            <DepartmentManagement />
          </el-tab-pane>
          <el-tab-pane label="岗位管理" name="position">
            <PositionManagement />
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import PageContainer from '@/components/common/PageContainer.vue'
import RegionManagement from './RegionManagement.vue'
import DepartmentManagement from './DepartmentManagement.vue'
import PositionManagement from './PositionManagement.vue'

const activeTab = ref('region')
const overviewCards = [
  { name: 'region', title: '区域管理', description: '维护区域树结构，支持层级划分与上下级区域管理。' },
  { name: 'department', title: '部门管理', description: '维护部门层级与归属区域，形成清晰的组织关系。' },
  { name: 'position', title: '岗位管理', description: '维护岗位、职级与职责描述，支撑人员分配与权限配置。' },
]
</script>

<style scoped lang="scss">
.organization-page {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.overview-row {
  margin-bottom: 0;
}

.overview-card {
  cursor: pointer;
  border-radius: $card-radius;
  border: 1px solid $border-color;
  transition: all 0.2s ease;
}

.overview-card.is-active {
  border-color: $primary-color;
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.12);
}

.overview-card__title {
  font-size: 16px;
  font-weight: 600;
  color: $text-color;
}

.overview-card__desc {
  margin-top: 8px;
  color: $text-color-secondary;
  font-size: $font-size-sm;
  line-height: 1.7;
}

.organization-panel {
  border-radius: $card-radius;
  border: 1px solid $border-color;
}

.organization-tabs :deep(.el-tabs__header) {
  margin-bottom: $spacing-md;
}

.organization-tabs :deep(.el-tabs__nav-wrap::after) {
  background-color: $border-color;
}

.organization-tabs :deep(.el-tabs__item) {
  height: 40px;
  line-height: 40px;
  font-weight: 500;
}

.organization-tabs :deep(.el-tabs__content) {
  min-height: 560px;
}
</style>
