<template>
  <el-table :data="data" v-loading="loading" stripe>
    <el-table-column prop="fileName" label="文件名" />
    <el-table-column prop="fileType" label="类型" width="80" />
    <el-table-column prop="vectorStatus" label="状态" width="100">
      <template #default="{ row }">
        <el-tag :type="statusType(row.vectorStatus)">{{ statusText(row.vectorStatus) }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="uploaderName" label="上传者" width="100" />
    <el-table-column prop="uploadedAt" label="时间" width="160" />
    <el-table-column label="操作" width="200">
      <template #default="{ row }">
        <el-button size="small" @click="$emit('view', row)">查看</el-button>
        <el-button size="small" type="primary" @click="$emit('download', row)">下载</el-button>
        <el-button size="small" type="danger" @click="$emit('delete', row)">删除</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
defineProps(['data', 'loading'])
defineEmits(['view', 'download', 'delete'])
const statusType = s => ({ COMPLETED: 'success', PROCESSING: 'warning', FAILED: 'danger' }[s] || 'info')
const statusText = s => ({ COMPLETED: '已完成', PROCESSING: '处理中', FAILED: '失败', PENDING: '待处理' }[s] || s)
</script>
