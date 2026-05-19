<template>
  <PageContainer title="文件预览服务">
    <el-card shadow="never">
      <div class="status-container">
        <el-result
          :icon="status.available ? 'success' : 'warning'"
          :title="status.available ? 'kkFileView 预览服务已连接' : 'kkFileView 预览服务不可用'"
          :sub-title="status.message"
        >
          <template #extra>
            <el-button type="primary" @click="checkStatus">
              <el-icon><Refresh /></el-icon>
              刷新状态
            </el-button>
          </template>
        </el-result>

        <el-divider />

        <div class="info-section">
          <h3>服务说明</h3>
          <el-alert type="info" :closable="false" show-icon>
            <template #title>
              文件在线预览服务 (kkFileView)
            </template>
            <div>
              <p>kkFileView 文件在线预览服务支持：</p>
              <ul>
                <li>Word 文档在线预览（doc, docx）</li>
                <li>Excel 表格在线预览（xls, xlsx）</li>
                <li>PowerPoint 演示文稿在线预览（ppt, pptx）</li>
                <li>PDF 文档在线预览</li>
                <li>图片在线预览（png, jpg, gif, bmp, webp, svg）</li>
                <li>文本文件在线预览（txt, md, csv, json, xml, log 等）</li>
              </ul>
            </div>
          </el-alert>

          <el-alert
            v-if="!status.available"
            type="warning"
            :closable="false"
            show-icon
            class="mt-4"
          >
            <template #title>
              部署说明
            </template>
            <div>
              <p>kkFileView 文件预览服务需要通过 Docker 部署。</p>
              <p>部署命令：</p>
              <code class="code-block">docker run -d --name kkfileview --restart=always -p 8012:8012 keking/kkfileview:latest</code>
              <p class="mt-2">部署后等待约 2 分钟服务初始化完成。</p>
            </div>
          </el-alert>

          <el-alert
            v-else
            type="success"
            :closable="false"
            show-icon
            class="mt-4"
          >
            <template #title>
              功能已启用
            </template>
            <div>
              <p>您可以在知识库详情页对文档进行在线预览。</p>
            </div>
          </el-alert>
        </div>

        <el-divider />

        <div class="info-section">
          <h3>支持的格式</h3>
          <el-space wrap>
            <el-tag type="success" effect="plain">.doc</el-tag>
            <el-tag type="success" effect="plain">.docx</el-tag>
            <el-tag type="success" effect="plain">.xls</el-tag>
            <el-tag type="success" effect="plain">.xlsx</el-tag>
            <el-tag type="success" effect="plain">.ppt</el-tag>
            <el-tag type="success" effect="plain">.pptx</el-tag>
            <el-tag type="primary" effect="plain">.pdf</el-tag>
            <el-tag type="info" effect="plain">.png</el-tag>
            <el-tag type="info" effect="plain">.jpg</el-tag>
            <el-tag type="info" effect="plain">.gif</el-tag>
            <el-tag type="warning" effect="plain">.txt</el-tag>
            <el-tag type="warning" effect="plain">.md</el-tag>
            <el-tag type="warning" effect="plain">.csv</el-tag>
          </el-space>
        </div>
      </div>
    </el-card>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import request from '@/api/request'
import PageContainer from '@/components/common/PageContainer.vue'

const KK_FILEVIEW_BASE = (import.meta.env.VITE_KK_FILEVIEW_BASE || 'http://localhost:8012').replace(/\/$/, '')

const status = ref({
  available: false,
  message: '正在检测...'
})

async function checkStatus() {
  try {
    const resp = await fetch(`${KK_FILEVIEW_BASE}/`, { method: 'GET', mode: 'no-cors' })
    status.value = {
      available: true,
      message: `kkFileView 服务运行正常（${KK_FILEVIEW_BASE}）`
    }
  } catch {
    status.value = {
      available: false,
      message: `无法连接到 kkFileView 服务（${KK_FILEVIEW_BASE}），请确认 Docker 容器已启动`
    }
  }
}

onMounted(() => {
  checkStatus()
})
</script>

<style scoped lang="scss">
.status-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.info-section {
  h3 {
    margin: 0 0 16px;
    font-size: 16px;
    font-weight: 600;
  }

  p {
    margin: 8px 0;
    line-height: 1.6;
  }

  ul {
    margin: 8px 0;
    padding-left: 20px;
  }

  li {
    margin: 4px 0;
  }
}

.mt-4 {
  margin-top: 16px;
}

.mt-2 {
  margin-top: 8px;
}

.code-block {
  display: block;
  background: #f5f5f5;
  padding: 12px 16px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  margin-top: 8px;
}
</style>
