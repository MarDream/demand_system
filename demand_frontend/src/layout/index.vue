<template>
  <div class="layout-container">
    <el-container>
      <el-header>
        <span>综合运营管理平台</span>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped lang="scss">
.layout-container {
  height: 100vh;
  .el-header {
    background: var(--color-sidebar-bg-gradient);
    color: white;
    line-height: var(--header-height);
    font-size: var(--font-size-md);
  }
}
</style>
