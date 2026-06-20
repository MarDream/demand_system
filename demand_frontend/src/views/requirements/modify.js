const fs = require('fs');
const path = './index.vue';
let content = fs.readFileSync(path, 'utf8');

// 1. 添加"我的已办"选项
content = content.replace(
  '          <el-radio-button value="drafts">我的草稿</el-radio-button>\n        </el-radio-group>',
  '          <el-radio-button value="drafts">我的草稿</el-radio-button>\n          <el-radio-button value="done">我的已办</el-radio-button>\n        </el-radio-group>'
);

// 2. 导入 getMyRequirementDone
content = content.replace(
  'import { getMyRequirementPending } from \'@/api/modules/requirement\'',
  'import { getMyRequirementPending, getMyRequirementDone } from \'@/api/modules/requirement\''
);

// 3. 修改 viewMode 类型并处理 'done' 初始化
content = content.replace(
  'const viewMode = ref<\'all\' | \'drafts\' | \'pending\'>(route.query.view === \'drafts\' ? \'drafts\' : route.query.view === \'pending\' ? \'pending\' : \'all\')',
  'const viewMode = ref<\'all\' | \'drafts\' | \'pending\' | \'done\'>(route.query.view === \'drafts\' ? \'drafts\' : route.query.view === \'pending\' ? \'pending\' : route.query.view === \'done\' ? \'done\' : \'all\')'
);

// 4. 添加 isDoneView 计算属性
content = content.replace(
  'const isPendingView = computed(() => viewMode.value === \'pending\')',
  'const isPendingView = computed(() => viewMode.value === \'pending\')\nconst isDoneView = computed(() => viewMode.value === \'done\')'
);

// 5. 添加筛选条件中的 !isDoneView
content = content.replace(/v-if="!isDraftView && !isPendingView"/g, 'v-if="!isDraftView && !isPendingView && !isDoneView"');
content = content.replace(/v-show="filterExpanded && !isDraftView && !isPendingView"/g, 'v-show="filterExpanded && !isDraftView && !isPendingView && !isDoneView"');

// 6. 修改 handleViewModeChange 类型
content = content.replace(
  'function handleViewModeChange(value: \'all\' | \'drafts\' | \'pending\')',
  'function handleViewModeChange(value: \'all\' | \'drafts\' | \'pending\' | \'done\')'
);

// 7. 添加 'done' 处理到 handleViewModeChange
content = content.replace(
  'else if (value === \'pending\') query.view = \'pending\'\n  router.replace({ query })',
  'else if (value === \'pending\') query.view = \'pending\'\n  else if (value === \'done\') query.view = \'done\'\n  router.replace({ query })'
);

// 8. 在 fetchData 中添加 isDoneView 处理
content = content.replace(
  '    if (isPendingView.value) {\n      const params: RequirementMyListQuery = {\n        keyword: filterForm.keyword || undefined,\n        pageNum: pagination.pageNum,\n        pageSize: pagination.pageSize,\n      }\n      const data = await getMyRequirementPending(params)\n      tableData.value = data.list\n      pagination.total = data.total\n      return\n    }\n\n    const params: RequirementQuery = {',
  '    if (isPendingView.value) {\n      const params: RequirementMyListQuery = {\n        keyword: filterForm.keyword || undefined,\n        pageNum: pagination.pageNum,\n        pageSize: pagination.pageSize,\n      }\n      const data = await getMyRequirementPending(params)\n      tableData.value = data.list\n      pagination.total = data.total\n      return\n    }\n\n    if (isDoneView.value) {\n      const data = await getMyRequirementDone({ keyword: filterForm.keyword })\n      tableData.value = data\n      pagination.total = data.length\n      return\n    }\n\n    const params: RequirementQuery = {'
);

// 9. 修改操作按钮部分
content = content.replace(
  '<template v-else-if="col.key === \'operations\'">\n                  <el-tooltip content="查看详情" placement="top">\n                    <el-button link type="primary" :icon="View" @click="handleOpen(row)" />\n                  </el-tooltip>\n                  <el-tooltip content="编辑" placement="top">\n                    <el-button link type="primary" :icon="Edit" @click="handleEdit(row)" />\n                  </el-tooltip>\n                  <el-popconfirm title="确定删除该需求吗？" @confirm="handleDelete(row.id)">\n                    <template #reference>\n                      <el-button link type="danger" :icon="Delete" title="删除" />\n                    </template>\n                  </el-popconfirm>\n                </template>',
  '<template v-else-if="col.key === \'operations\'">\n                  <!-- 我的待办/已办视图根据operationType显示不同按钮 -->\n                  <template v-if="isPendingView || isDoneView">\n                    <el-button v-if="row.operationType === \'edit\'" link type="primary" @click="handleEdit(row)">编辑</el-button>\n                    <el-button v-if="row.operationType === \'approve\'" link type="warning" @click="handleOpen(row)">待办</el-button>\n                    <el-button v-if="row.operationType === \'view\'" link type="primary" @click="handleOpen(row)">查看</el-button>\n                  </template>\n                  <!-- 全部需求/草稿视图显示原有操作按钮 -->\n                  <template v-else>\n                    <el-tooltip content="查看详情" placement="top">\n                      <el-button link type="primary" :icon="View" @click="handleOpen(row)" />\n                    </el-tooltip>\n                    <el-tooltip content="编辑" placement="top">\n                      <el-button link type="primary" :icon="Edit" @click="handleEdit(row)" />\n                    </el-tooltip>\n                    <el-popconfirm title="确定删除该需求吗？" @confirm="handleDelete(row.id)">\n                      <template #reference>\n                        <el-button link type="danger" :icon="Delete" title="删除" />\n                      </template>\n                    </el-popconfirm>\n                  </template>\n                </template>'
);

fs.writeFileSync(path, content, 'utf8');
