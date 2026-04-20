<template>
  <div class="layout-container">
    <el-container>
      <el-header>
        <span>需求管理系统</span>
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
    background-color: $sidebar-bg;
    color: white;
    line-height: $header-height;
    font-size: $font-size-md;
  }
}
</style>
