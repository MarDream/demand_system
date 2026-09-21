<template>
  <aside class="widget-panel">
    <header class="widget-panel__header">
      <i :class="def.icon" class="widget-panel__type-icon" />
      <span class="widget-panel__type-name">{{ def.label }}</span>
      <el-button link class="widget-panel__close" @click="emit('close')">
        <el-icon><Close /></el-icon>
      </el-button>
    </header>

    <el-tabs v-model="activeTab" class="widget-panel__tabs">
      <!-- ============ 基础配置 ============ -->
      <el-tab-pane label="基础配置" name="basic">
        <div class="widget-panel__form">
          <div class="form-item">
            <div class="form-item__label">名称</div>
            <el-input v-model="widget.title" placeholder="组件名称" @change="emit('change')" />
          </div>

          <template v-if="def.dataMode !== 'none'">
            <!-- 多数据源开关 -->
            <div class="form-item form-item--inline">
              <el-checkbox v-model="multiSource" @change="onMultiSourceToggle">多数据源模式</el-checkbox>
              <el-tooltip content="开启后一个组件可同时统计多张数据表，按同名字段对齐计算" placement="top">
                <i class="ri-question-line hint-icon" />
              </el-tooltip>
            </div>

            <!-- 单源：表选择 + 共享筛选 -->
            <template v-if="!multiSource">
              <div class="form-item">
                <div class="form-item__label">源数据表</div>
                <el-select
                  v-model="tableId"
                  placeholder="选择数据表"
                  style="width: 100%"
                  @change="onTableChange"
                >
                  <el-option v-for="t in tables" :key="t.id" :label="t.name" :value="t.id" />
                </el-select>
              </div>

              <div class="form-item">
                <div class="form-item__label">数据范围</div>
                <div class="data-range">
                  <el-select model-value="all" disabled style="flex: 1">
                    <el-option label="全部数据" value="all" />
                  </el-select>
                  <el-popover placement="bottom-end" :width="380" trigger="click">
                    <template #reference>
                      <el-badge :value="filterCount" :hidden="!filterCount" type="primary">
                        <el-button><i class="ri-filter-3-line" />&nbsp;筛选</el-button>
                      </el-badge>
                    </template>
                    <div class="filter-builder">
                      <el-radio-group v-model="filterLogic" size="small">
                        <el-radio-button value="and">且</el-radio-button>
                        <el-radio-button value="or">或</el-radio-button>
                      </el-radio-group>
                      <div v-for="(rule, i) in filterRules" :key="i" class="filter-builder__rule">
                        <el-select v-model="rule.fieldId" placeholder="字段" size="small" style="width: 118px">
                          <el-option v-for="f in fields" :key="f.id" :label="f.name" :value="f.id" />
                        </el-select>
                        <el-select v-model="rule.operator" placeholder="条件" size="small" style="width: 96px">
                          <el-option v-for="op in FILTER_OPERATORS" :key="op.value" :label="op.label" :value="op.value" />
                        </el-select>
                        <el-input
                          v-if="!isEmptyOp(rule.operator)"
                          v-model="rule.value"
                          placeholder="值"
                          size="small"
                          style="flex: 1"
                        />
                        <el-button link type="danger" size="small" @click="removeRule(i)">
                          <el-icon><Delete /></el-icon>
                        </el-button>
                      </div>
                      <el-button size="small" style="width: 100%" @click="addRule">
                        <el-icon><Plus /></el-icon> 添加条件
                      </el-button>
                    </div>
                  </el-popover>
                </div>
              </div>
            </template>

            <!-- 多源：数据源列表（每源独立筛选） -->
            <template v-else>
              <div class="form-item">
                <div class="form-item__label">数据源（{{ sources.length }}/{{ MAX_SOURCES }}）</div>
                <div v-for="(src, i) in sources" :key="i" class="source-row">
                  <el-select
                    v-model="src.tableId"
                    placeholder="选择数据表"
                    size="small"
                    style="flex: 1"
                    @change="onSourceTableChange(src)"
                  >
                    <el-option v-for="t in tables" :key="t.id" :label="t.name" :value="t.id" />
                  </el-select>
                  <el-popover placement="bottom-end" :width="380" trigger="click">
                    <template #reference>
                      <el-badge :value="sourceFilterCount(src)" :hidden="!sourceFilterCount(src)" type="primary">
                        <el-button size="small"><i class="ri-filter-3-line" />&nbsp;筛选</el-button>
                      </el-badge>
                    </template>
                    <div class="filter-builder">
                      <el-radio-group
                        :model-value="src.filterConfig?.logic || 'and'"
                        size="small"
                        @update:model-value="(v: string) => setSourceFilterLogic(src, v)"
                      >
                        <el-radio-button value="and">且</el-radio-button>
                        <el-radio-button value="or">或</el-radio-button>
                      </el-radio-group>
                      <div v-for="(rule, ri) in src.filterConfig?.rules || []" :key="ri" class="filter-builder__rule">
                        <el-select v-model="rule.fieldId" placeholder="字段" size="small" style="width: 118px">
                          <el-option v-for="f in sourceFields(src)" :key="f.id" :label="f.name" :value="f.id" />
                        </el-select>
                        <el-select v-model="rule.operator" placeholder="条件" size="small" style="width: 96px">
                          <el-option v-for="op in FILTER_OPERATORS" :key="op.value" :label="op.label" :value="op.value" />
                        </el-select>
                        <el-input
                          v-if="!isEmptyOp(rule.operator)"
                          v-model="rule.value"
                          placeholder="值"
                          size="small"
                          style="flex: 1"
                        />
                        <el-button link type="danger" size="small" @click="removeSourceRule(src, ri)">
                          <el-icon><Delete /></el-icon>
                        </el-button>
                      </div>
                      <el-button size="small" style="width: 100%" @click="addSourceRule(src)">
                        <el-icon><Plus /></el-icon> 添加条件
                      </el-button>
                    </div>
                  </el-popover>
                  <el-button
                    link
                    type="danger"
                    size="small"
                    :disabled="sources.length <= 1"
                    @click="removeSource(i)"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
                <el-button
                  size="small"
                  style="width: 100%"
                  :disabled="sources.length >= MAX_SOURCES"
                  @click="addSource"
                >
                  <el-icon><Plus /></el-icon> 添加数据源
                </el-button>
                <div class="form-item__tip">各表同名字段参与计算；字段筛选在对应数据源上单独配置</div>
              </div>
            </template>

            <!-- 单值类：统计方式 -->
            <template v-if="def.dataMode === 'single'">
              <div class="form-item">
                <div class="form-item__label">统计方式</div>
                <el-radio-group v-model="singleMode">
                  <el-radio-button value="count">记录数</el-radio-button>
                  <el-radio-button value="field">字段值</el-radio-button>
                </el-radio-group>
              </div>
              <template v-if="singleMode === 'field'">
                <div class="form-item">
                  <div class="form-item__label">聚合字段</div>
                  <el-select
                    v-if="!multiSource"
                    v-model="primaryMetric.fieldId"
                    placeholder="选择数值字段"
                    style="width: 100%"
                    @change="emit('change')"
                  >
                    <el-option
                      v-for="f in metricFields"
                      :key="f.id"
                      :label="f.name"
                      :value="f.id"
                    >
                      <span>{{ f.name }}</span>
                      <span class="field-type-tag">{{ fieldLabel(f.fieldType) }}</span>
                    </el-option>
                  </el-select>
                  <el-select
                    v-else
                    v-model="primaryMetric.fieldName"
                    placeholder="选择数值字段（按名字跨表对齐）"
                    no-data-text="所选数据表无同名字段"
                    style="width: 100%"
                    @change="onPrimaryMetricNameChange"
                  >
                    <el-option v-for="f in multiMetricFields" :key="f.name" :label="f.name" :value="f.name">
                      <span>{{ f.name }}</span>
                      <span class="field-type-tag">{{ fieldLabel(f.fieldType) }}</span>
                    </el-option>
                  </el-select>
                </div>
                <div class="form-item">
                  <div class="form-item__label">聚合方式</div>
                  <el-select v-model="primaryMetric.aggregation" style="width: 100%" @change="emit('change')">
                    <el-option v-for="op in AGGREGATION_OPTIONS.slice(1)" :key="op.value" :label="op.label" :value="op.value" />
                  </el-select>
                </div>
              </template>
            </template>

            <!-- 分组类：维度 + 指标 -->
            <template v-if="def.dataMode === 'grouped'">
              <div class="form-item">
                <div class="form-item__label">维度字段</div>
                <el-select
                  v-if="!multiSource"
                  v-model="dimensionFieldId"
                  placeholder="选择分组维度（可空）"
                  style="width: 100%"
                  clearable
                  @change="onDimensionChange"
                >
                  <el-option v-for="f in fields" :key="f.id" :label="f.name" :value="f.id">
                    <span>{{ f.name }}</span>
                    <span class="field-type-tag">{{ fieldLabel(f.fieldType) }}</span>
                  </el-option>
                </el-select>
                <el-select
                  v-else
                  v-model="dimensionFieldName"
                  placeholder="选择分组维度（按名字跨表对齐，可空）"
                  no-data-text="所选数据表无同名字段"
                  style="width: 100%"
                  clearable
                  @change="emit('change')"
                >
                  <el-option v-for="f in multiCommonFields" :key="f.name" :label="f.name" :value="f.name">
                    <span>{{ f.name }}</span>
                    <span class="field-type-tag">{{ fieldLabel(f.fieldType) }}</span>
                  </el-option>
                </el-select>
              </div>
              <div v-if="isDateDimension" class="form-item">
                <div class="form-item__label">日期粒度</div>
                <el-select v-model="granularity" style="width: 100%" @change="emit('change')">
                  <el-option label="自动（按天）" value="auto" />
                  <el-option label="按天" value="day" />
                  <el-option label="按周" value="week" />
                  <el-option label="按月" value="month" />
                </el-select>
              </div>

              <div class="form-item">
                <div class="form-item__label">指标（{{ metrics.length }}/{{ def.maxMetrics ?? 1 }}）</div>
                <div v-for="(metric, i) in metrics" :key="i" class="metric-row">
                  <el-select v-model="metric.aggregation" size="small" style="width: 84px" @change="emit('change')">
                    <el-option v-for="op in AGGREGATION_OPTIONS" :key="op.value" :label="op.label" :value="op.value" />
                  </el-select>
                  <el-select
                    v-if="!multiSource"
                    v-model="metric.fieldId"
                    size="small"
                    placeholder="数值字段"
                    style="flex: 1"
                    :disabled="metric.aggregation === 'count'"
                    @change="emit('change')"
                  >
                    <el-option v-for="f in metricFields" :key="f.id" :label="f.name" :value="f.id" />
                  </el-select>
                  <el-select
                    v-else
                    v-model="metric.fieldName"
                    size="small"
                    placeholder="数值字段"
                    no-data-text="所选数据表无同名字段"
                    style="flex: 1"
                    :disabled="metric.aggregation === 'count'"
                    @change="onMetricNameChange(metric)"
                  >
                    <el-option v-for="f in multiMetricFields" :key="f.name" :label="f.name" :value="f.name" />
                  </el-select>
                  <el-button v-if="metrics.length > 1" link type="danger" size="small" @click="removeMetric(i)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
                <el-button
                  v-if="metrics.length < (def.maxMetrics ?? 1)"
                  size="small"
                  style="width: 100%"
                  @click="addMetric"
                >
                  <el-icon><Plus /></el-icon> 添加指标
                </el-button>
              </div>

              <div class="form-item">
                <div class="form-item__label">排序</div>
                <el-select v-model="sortMode" style="width: 100%" @change="emit('change')">
                  <el-option label="默认顺序" value="default" />
                  <el-option label="按值升序" value="asc" />
                  <el-option label="按值降序" value="desc" />
                </el-select>
              </div>
              <div class="form-item">
                <div class="form-item__label">显示数量（0 为全部）</div>
                <el-input-number v-model="groupLimit" :min="0" :max="50" style="width: 100%" @change="emit('change')" />
              </div>
            </template>
          </template>
        </div>
      </el-tab-pane>

      <!-- ============ 自定义配置 ============ -->
      <el-tab-pane label="自定义配置" name="custom">
        <div class="widget-panel__form">
          <!-- 单值类样式 -->
          <template v-if="def.dataMode === 'single'">
            <div class="form-item">
              <div class="form-item__label">数值颜色</div>
              <el-color-picker v-model="display.valueColor" :predefine="PRESET_COLORS" @change="emit('change')" />
            </div>
            <div class="form-item">
              <div class="form-item__label">小数位</div>
              <el-input-number v-model="display.decimals" :min="0" :max="4" style="width: 100%" @change="emit('change')" />
            </div>
            <div class="form-item">
              <div class="form-item__label">单位</div>
              <el-input v-model="display.unit" placeholder="如：个 / 万元" @change="emit('change')" />
            </div>
            <div v-if="widget.type === 'progress'" class="form-item">
              <div class="form-item__label">目标值</div>
              <el-input-number v-model="display.targetValue" :min="0" style="width: 100%" @change="emit('change')" />
            </div>
          </template>

          <!-- 图表类样式 -->
          <template v-else-if="def.dataMode === 'grouped'">
            <div v-if="widget.type === 'rank'" class="form-item">
              <div class="form-item__label">显示进度条</div>
              <el-switch v-model="display.showBar" @change="emit('change')" />
            </div>
            <template v-else>
              <div class="form-item">
                <div class="form-item__label">配色方案</div>
                <el-select v-model="display.colorScheme" style="width: 100%" @change="emit('change')">
                  <el-option v-for="s in COLOR_SCHEME_OPTIONS" :key="s.value" :label="s.label" :value="s.value">
                    <span class="scheme-preview">
                      <i v-for="c in schemeColors(s.value).slice(0, 5)" :key="c" :style="{ background: c }" />
                    </span>
                    {{ s.label }}
                  </el-option>
                </el-select>
              </div>
              <div class="form-item">
                <div class="form-item__label">显示图例</div>
                <el-switch v-model="display.showLegend" @change="emit('change')" />
              </div>
              <div class="form-item">
                <div class="form-item__label">显示数值标签</div>
                <el-switch v-model="display.showValue" @change="emit('change')" />
              </div>
              <div v-if="widget.type === 'line' || widget.type === 'area' || widget.type === 'combo'" class="form-item">
                <div class="form-item__label">平滑曲线</div>
                <el-switch v-model="display.smooth" @change="emit('change')" />
              </div>
            </template>
            <div class="form-item">
              <div class="form-item__label">数值单位</div>
              <el-input v-model="display.unit" placeholder="如：个 / 万元" @change="emit('change')" />
            </div>
            <div class="form-item">
              <div class="form-item__label">小数位</div>
              <el-input-number v-model="display.decimals" :min="0" :max="4" style="width: 100%" @change="emit('change')" />
            </div>
          </template>

          <!-- 静态类 -->
          <template v-else>
            <template v-if="widget.type === 'text'">
              <div class="form-item">
                <div class="form-item__label">文本内容</div>
                <el-input v-model="display.content" type="textarea" :rows="6" @change="emit('change')" />
              </div>
            </template>
            <template v-else-if="widget.type === 'image'">
              <div class="form-item">
                <div class="form-item__label">图片地址</div>
                <el-input v-model="display.url" placeholder="https://..." clearable @change="emit('change')" />
              </div>
              <div class="form-item">
                <div class="form-item__label">填充方式</div>
                <el-select v-model="display.fit" style="width: 100%" @change="emit('change')">
                  <el-option label="等比缩放（完整显示）" value="contain" />
                  <el-option label="裁剪填充（铺满组件）" value="cover" />
                </el-select>
              </div>
              <img v-if="display.url" :src="display.url" class="image-preview" alt="" />
            </template>
            <template v-else-if="widget.type === 'clock'">
              <div class="form-item">
                <div class="form-item__label">显示日期</div>
                <el-switch v-model="display.showDate" @change="emit('change')" />
              </div>
            </template>
          </template>
        </div>
      </el-tab-pane>
    </el-tabs>

    <footer class="widget-panel__footer">
      <el-button type="danger" plain style="width: 100%" @click="emit('delete')">
        <el-icon><Delete /></el-icon>&nbsp;删除组件
      </el-button>
    </footer>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Close, Delete, Plus } from '@element-plus/icons-vue'
import type { WidgetInstance, WidgetFilterRule, WidgetSourceConfig } from './widgetDefs'
import {
  getWidgetDef, isMetricField, DATE_FIELD_TYPES,
  FILTER_OPERATORS, AGGREGATION_OPTIONS,
} from './widgetDefs'
import { COLOR_SCHEME_OPTIONS, schemeColors } from './chartOptions'

const props = defineProps<{
  widget: WidgetInstance
  tables: any[]
  fieldsByTable: Record<number, any[]>
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'change'): void
  (e: 'ensure-fields', tableId: number): void
  (e: 'delete'): void
}>()

const def = computed(() => getWidgetDef(props.widget.type))
const display = computed(() => props.widget.displayConfig)

const activeTab = ref<'basic' | 'custom'>('basic')
watch(() => props.widget.id, () => { activeTab.value = 'basic' })

const PRESET_COLORS = ['#3b82f6', '#22d3ee', '#a78bfa', '#34d399', '#fbbf24', '#fb7185', '#f97316', '#ef4444']

// ---- 数据表与字段 ----
const tableId = computed({
  get: () => props.widget.dataSourceConfig.tableId,
  set: (v) => { props.widget.dataSourceConfig.tableId = v },
})
const fields = computed<any[]>(() => {
  const tid = props.widget.dataSourceConfig.tableId
  if (!tid) return []
  return props.fieldsByTable[tid] || []
})
const metricFields = computed(() => fields.value.filter((f) => isMetricField(f)))

function fieldLabel(type: string): string {
  const map: Record<string, string> = {
    text: '文本', number: '数字', date: '日期', single_select: '单选', multi_select: '多选',
    user: '人员', currency: '货币', progress: '进度', rating: '评分', auto_number: '编号',
    formula: '公式', rollup: '汇总', lookup: '引用', department: '部门', created_time: '创建时间',
    created_by: '创建人', checkbox: '复选框', url: '链接', email: '邮箱', phone: '电话',
  }
  return map[type] || type
}

function onTableChange() {
  const ds = props.widget.dataSourceConfig
  ds.dimension = undefined
  ds.metrics = [{ fieldId: null, aggregation: 'count' }]
  if (ds.tableId) emit('ensure-fields', ds.tableId)
  emit('change')
}

// ==================== 多数据源模式 ====================

/** 与后端 MAX_SOURCES 对齐 */
const MAX_SOURCES = 10

const multiSource = computed({
  get: () => !!props.widget.dataSourceConfig.multiSource,
  set: (v) => { props.widget.dataSourceConfig.multiSource = v },
})

const sources = computed<WidgetSourceConfig[]>(() => props.widget.dataSourceConfig.sources || [])

/** 各源数据表字段的同名交集（多源模式下维度/指标的候选） */
const multiCommonFields = computed<any[]>(() => {
  const lists = sources.value
    .map((s) => (s.tableId ? props.fieldsByTable[s.tableId] || [] : []))
    .filter((l) => l.length)
  if (!lists.length) return []
  const [first, ...rest] = lists
  return first.filter((f: any) => f?.name && rest.every((list: any[]) => list.some((g: any) => g.name === f.name)))
})
const multiMetricFields = computed(() => multiCommonFields.value.filter((f) => isMetricField(f)))

/** 按字段名在源表字段中找代表字段（判断类型用） */
function findFieldByName(name: string | null | undefined): any {
  if (!name) return null
  for (const s of sources.value) {
    const f = (props.fieldsByTable[s.tableId] || []).find((x: any) => x.name === name)
    if (f) return f
  }
  return null
}

/** 开关多数据源模式：开启时把当前表/筛选迁入 sources，字段切到 fieldName 对齐；关闭时反向迁回 */
function onMultiSourceToggle(on: boolean | string | number) {
  const ds = props.widget.dataSourceConfig
  const enabled = on === true
  ds.multiSource = enabled
  if (enabled) {
    if (!ds.sources?.length) {
      const initialTableId = ds.tableId ?? props.tables[0]?.id
      ds.sources = initialTableId ? [{ tableId: initialTableId, filterConfig: ds.filterConfig ?? null }] : []
    }
    const originFields = (ds.tableId && props.fieldsByTable[ds.tableId]) || []
    if (ds.dimension?.fieldId != null && !ds.dimension.fieldName) {
      const f = originFields.find((x: any) => x.id === ds.dimension!.fieldId)
      if (f) ds.dimension.fieldName = f.name
    }
    ds.metrics?.forEach((m) => {
      if (m.fieldId != null && !m.fieldName) {
        const f = originFields.find((x: any) => x.id === m.fieldId)
        if (f) m.fieldName = f.name
      }
    })
    ds.sources.forEach((s) => { if (s.tableId) emit('ensure-fields', s.tableId) })
  } else {
    const first = ds.sources?.find((s) => s.tableId)
    if (first) {
      ds.tableId = first.tableId
      ds.filterConfig = first.filterConfig ?? null
    }
    const targetFields = (ds.tableId && props.fieldsByTable[ds.tableId]) || []
    if (ds.dimension?.fieldName) {
      const f = targetFields.find((x: any) => x.name === ds.dimension!.fieldName)
      ds.dimension.fieldId = f?.id ?? null
      ds.dimension.fieldName = null
    }
    ds.metrics?.forEach((m) => {
      if (m.fieldName) {
        const f = targetFields.find((x: any) => x.name === m.fieldName)
        m.fieldId = f?.id ?? null
        m.fieldName = null
      }
    })
    ds.sources = undefined
    if (ds.tableId) emit('ensure-fields', ds.tableId)
  }
  emit('change')
}

function addSource() {
  const ds = props.widget.dataSourceConfig
  if (!ds.sources) ds.sources = []
  const used = new Set(ds.sources.map((s) => s.tableId))
  const candidate: any = props.tables.find((t: any) => !used.has(t.id)) || props.tables[0]
  if (!candidate) return
  ds.sources.push({ tableId: candidate.id, filterConfig: null })
  emit('ensure-fields', candidate.id)
  emit('change')
}

function removeSource(i: number) {
  props.widget.dataSourceConfig.sources?.splice(i, 1)
  emit('change')
}

function onSourceTableChange(src: WidgetSourceConfig) {
  if (src.tableId) emit('ensure-fields', src.tableId)
  emit('change')
}

// ---- 每源独立筛选 ----
function sourceFields(src: WidgetSourceConfig): any[] {
  return (src.tableId && props.fieldsByTable[src.tableId]) || []
}
function sourceFilterCount(src: WidgetSourceConfig): number {
  return src.filterConfig?.rules?.length || 0
}
function addSourceRule(src: WidgetSourceConfig) {
  if (!src.filterConfig) src.filterConfig = { logic: 'and', rules: [] }
  const fs = sourceFields(src)
  src.filterConfig.rules.push({ fieldId: fs[0]?.id ?? null, operator: 'eq', value: '' })
  emit('change')
}
function removeSourceRule(src: WidgetSourceConfig, i: number) {
  const fc = src.filterConfig
  if (!fc) return
  fc.rules.splice(i, 1)
  if (!fc.rules.length) src.filterConfig = null
  emit('change')
}
function setSourceFilterLogic(src: WidgetSourceConfig, logic: string) {
  if (!src.filterConfig) src.filterConfig = { logic: 'and', rules: [] }
  src.filterConfig.logic = logic || 'and'
  emit('change')
}

// ---- 筛选 ----
const filterRules = computed<WidgetFilterRule[]>(() => {
  const fc = props.widget.dataSourceConfig.filterConfig
  return fc?.rules || []
})
const filterLogic = computed({
  get: () => props.widget.dataSourceConfig.filterConfig?.logic || 'and',
  set: (v) => {
    if (props.widget.dataSourceConfig.filterConfig) {
      props.widget.dataSourceConfig.filterConfig.logic = v
    }
    emit('change')
  },
})
const filterCount = computed(() => filterRules.value.length)

function addRule() {
  const ds = props.widget.dataSourceConfig
  if (!ds.filterConfig) ds.filterConfig = { logic: 'and', rules: [] }
  ds.filterConfig.rules.push({ fieldId: fields.value[0]?.id ?? null, operator: 'eq', value: '' })
  emit('change')
}
function removeRule(i: number) {
  const fc = props.widget.dataSourceConfig.filterConfig
  if (!fc) return
  fc.rules.splice(i, 1)
  if (!fc.rules.length) props.widget.dataSourceConfig.filterConfig = null
  emit('change')
}
function isEmptyOp(op: string) {
  return op === 'is_empty' || op === 'is_not_empty'
}

// ---- 单值统计方式 ----
const primaryMetric = computed(() => {
  const ds = props.widget.dataSourceConfig
  if (!ds.metrics?.length) ds.metrics = [{ fieldId: null, aggregation: 'count' }]
  return ds.metrics[0]
})
const singleMode = computed({
  get: () => primaryMetric.value.aggregation !== 'count' ? 'field' : 'count',
  set: (v) => {
    if (v === 'count') {
      primaryMetric.value.aggregation = 'count'
      primaryMetric.value.fieldId = null
      primaryMetric.value.fieldName = null
    } else {
      primaryMetric.value.aggregation = 'sum'
      if (multiSource.value) {
        if (!primaryMetric.value.fieldName) {
          primaryMetric.value.fieldName = multiMetricFields.value[0]?.name ?? null
        }
        primaryMetric.value.fieldId = null
      } else if (!primaryMetric.value.fieldId) {
        primaryMetric.value.fieldId = metricFields.value[0]?.id ?? null
      }
    }
    emit('change')
  },
})

/** 多源模式下聚合字段按名选择：清掉单表 fieldId 避免歧义 */
function onPrimaryMetricNameChange() {
  primaryMetric.value.fieldId = null
  emit('change')
}

function onMetricNameChange(metric: { fieldId?: number | null }) {
  metric.fieldId = null
  emit('change')
}

// ---- 维度 ----
const dimensionFieldId = computed({
  get: () => props.widget.dataSourceConfig.dimension?.fieldId ?? null,
  set: (v) => {
    const ds = props.widget.dataSourceConfig
    if (v == null) ds.dimension = undefined
    else if (ds.dimension) ds.dimension.fieldId = v
    else ds.dimension = { fieldId: v, granularity: 'day' }
  },
})
const granularity = computed({
  get: () => props.widget.dataSourceConfig.dimension?.granularity || 'auto',
  set: (v) => {
    const ds = props.widget.dataSourceConfig
    if (ds.dimension) ds.dimension.granularity = v
  },
})
const isDateDimension = computed(() => {
  if (multiSource.value) {
    const f = findFieldByName(dimensionFieldName.value)
    return !!f && DATE_FIELD_TYPES.has(f.fieldType)
  }
  const f = fields.value.find((f) => f.id === dimensionFieldId.value)
  return !!f && DATE_FIELD_TYPES.has(f.fieldType)
})

/** 多源模式下维度按字段名选择（清空单表 fieldId） */
const dimensionFieldName = computed({
  get: () => props.widget.dataSourceConfig.dimension?.fieldName ?? null,
  set: (v) => {
    const ds = props.widget.dataSourceConfig
    if (v == null) {
      ds.dimension = undefined
    } else if (ds.dimension) {
      ds.dimension.fieldName = v
      ds.dimension.fieldId = null
    } else {
      ds.dimension = { fieldName: v, fieldId: null, granularity: 'day' }
    }
    emit('change')
  },
})
function onDimensionChange(v: number | null) {
  dimensionFieldId.value = v
  if (v != null) {
    const f = fields.value.find((f) => f.id === v)
    if (f && DATE_FIELD_TYPES.has(f.fieldType)) {
      props.widget.dataSourceConfig.dimension = { fieldId: v, granularity: props.widget.dataSourceConfig.dimension?.granularity || 'auto' }
    }
  }
  emit('change')
}

// ---- 指标 ----
const metrics = computed(() => {
  const ds = props.widget.dataSourceConfig
  if (!ds.metrics?.length) ds.metrics = [{ fieldId: null, aggregation: 'count' }]
  return ds.metrics
})
function addMetric() {
  if (multiSource.value) {
    const f = multiMetricFields.value[0]
    metrics.value.push({ fieldId: null, fieldName: f?.name ?? null, aggregation: 'sum' })
  } else {
    metrics.value.push({ fieldId: metricFields.value[0]?.id ?? null, aggregation: 'sum' })
  }
  emit('change')
}
function removeMetric(i: number) {
  metrics.value.splice(i, 1)
  emit('change')
}

// ---- 排序 / 数量 ----
const sortMode = computed({
  get: () => props.widget.dataSourceConfig.sort || 'default',
  set: (v) => { props.widget.dataSourceConfig.sort = v },
})
const groupLimit = computed({
  get: () => props.widget.dataSourceConfig.limit ?? 0,
  set: (v) => { props.widget.dataSourceConfig.limit = v || undefined },
})
</script>

<style scoped lang="scss">
.widget-panel {
  width: 340px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-left: 1px solid var(--color-border);
  background: var(--color-surface);
  min-height: 0;

  &__header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 14px 16px 10px;
    font-weight: 600;
    color: var(--color-text-primary);

    .widget-panel__type-icon {
      font-size: 16px;
      color: var(--color-primary);
    }

    .widget-panel__close {
      margin-left: auto;
    }
  }

  &__tabs {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;

    :deep(.el-tabs__content) {
      flex: 1;
      overflow-y: auto;
    }

    :deep(.el-tabs__header) {
      margin: 0 16px;
    }
  }

  &__form {
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding: 16px;
  }

  &__footer {
    padding: 12px 16px;
    border-top: 1px solid var(--color-border);
  }
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;

  &__label {
    font-size: 13px;
    font-weight: 500;
    color: var(--color-text-primary);
  }

  &--inline {
    flex-direction: row;
    align-items: center;
    gap: 8px;
  }
}

.data-range {
  display: flex;
  gap: 8px;
  align-items: center;
}

.metric-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.source-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.hint-icon {
  color: var(--color-text-tertiary);
  font-size: 14px;
  cursor: help;
}

.form-item__tip {
  font-size: 12px;
  color: var(--color-text-tertiary);
  line-height: 1.5;
}

.filter-builder {
  display: flex;
  flex-direction: column;
  gap: 10px;

  &__rule {
    display: flex;
    align-items: center;
    gap: 6px;
  }
}

.field-type-tag {
  float: right;
  font-size: 11px;
  color: var(--color-text-tertiary);
}

.scheme-preview {
  display: inline-flex;
  gap: 2px;
  margin-right: 8px;

  i {
    width: 10px;
    height: 10px;
    border-radius: 2px;
    display: inline-block;
  }
}

.image-preview {
  width: 100%;
  max-height: 140px;
  object-fit: contain;
  border-radius: var(--radius-md, 8px);
  border: 1px solid var(--color-border);
}
</style>
