<template>
  <el-form :model="config" label-width="100px" size="small" class="field-attr-form">
    <!-- ==================== 字段类型 ==================== -->
    <el-form-item v-if="showTypeSelector" label="字段类型" required>
      <el-select
        :model-value="fieldType"
        placeholder="选择字段类型"
        style="width: 100%"
        @update:model-value="onTypeChange"
      >
        <el-option-group v-for="group in typeGroups" :key="group.label" :label="group.label">
          <el-option
            v-for="opt in group.options"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-option-group>
      </el-select>
    </el-form-item>

    <!-- ==================== 通用属性 ==================== -->
    <el-form-item label="字段名称" required>
      <el-input v-model="base.name" maxlength="50" show-word-limit />
    </el-form-item>
    <el-form-item label="字段描述">
      <el-input v-model="base.description" type="textarea" :rows="2" maxlength="500" />
    </el-form-item>
    <el-form-item label="字段宽度">
      <el-input-number v-model="base.width" :min="50" :max="500" :step="10" controls-position="right" />
    </el-form-item>
    <el-form-item v-if="showRequired" label="是否必填">
      <el-switch v-model="base.required" />
    </el-form-item>
    <el-form-item v-if="showUnique" label="值唯一">
      <el-switch v-model="config.unique" />
    </el-form-item>
    <el-form-item label="表单隐藏">
      <el-switch v-model="config.formHidden" />
    </el-form-item>
    <el-form-item v-if="showPlaceholder" label="输入提示">
      <el-input v-model="config.formPlaceholder" />
    </el-form-item>

    <!-- ==================== 文本 ==================== -->
    <template v-if="fieldType === 'text'">
      <el-divider content-position="left">文本属性</el-divider>
      <el-form-item label="输入模式">
        <el-radio-group v-model="config.inputMode">
          <el-radio-button value="single">单行</el-radio-button>
          <el-radio-button value="multiline">多行</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="最大长度">
        <el-input-number v-model="config.maxLength" :min="1" :max="10000" controls-position="right" />
      </el-form-item>
      <el-form-item label="格式校验">
        <el-radio-group v-model="textPatternMode">
          <el-radio-button value="none">不限</el-radio-button>
          <el-radio-button value="regex">正则表达式</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <template v-if="textPatternMode === 'regex'">
        <el-form-item label="正则">
          <el-input v-model="config.pattern" placeholder="^[A-Za-z0-9]+$" />
        </el-form-item>
        <el-form-item label="错误提示">
          <el-input v-model="config.patternMessage" placeholder="仅支持字母和数字" />
        </el-form-item>
      </template>
      <el-form-item label="默认值">
        <el-input
          v-model="defaultText"
          :type="config.inputMode === 'multiline' ? 'textarea' : 'text'"
          :rows="2"
        />
      </el-form-item>
    </template>

    <!-- ==================== 电话 ==================== -->
    <template v-else-if="fieldType === 'phone'">
      <el-divider content-position="left">电话属性</el-divider>
      <el-form-item label="默认区号">
        <el-input v-model="config.countryCode" style="width: 120px" />
      </el-form-item>
      <el-form-item label="可切换区号">
        <el-switch v-model="config.allowCountrySwitch" />
      </el-form-item>
      <el-form-item label="脱敏显示">
        <el-switch v-model="config.masked" />
      </el-form-item>
    </template>

    <!-- ==================== 超链接 ==================== -->
    <template v-else-if="fieldType === 'url'">
      <el-divider content-position="left">超链接属性</el-divider>
      <el-form-item label="显示文本">
        <el-input v-model="config.displayText" placeholder="留空则显示完整链接" />
      </el-form-item>
      <el-form-item label="打开方式">
        <el-radio-group v-model="config.openInNewTab">
          <el-radio-button :value="true">新标签页</el-radio-button>
          <el-radio-button :value="false">当前页</el-radio-button>
        </el-radio-group>
      </el-form-item>
    </template>

    <!-- ==================== 地理位置 ==================== -->
    <template v-else-if="fieldType === 'location'">
      <el-divider content-position="left">地理位置属性</el-divider>
      <el-form-item label="输入方式">
        <el-radio-group v-model="config.locationInputMethod">
          <el-radio-button value="map">地图选点</el-radio-button>
          <el-radio-button value="text">地址解析</el-radio-button>
          <el-radio-button value="latlng">经纬度</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="显示内容">
        <el-radio-group v-model="config.locationDisplayMode">
          <el-radio-button value="name">地名</el-radio-button>
          <el-radio-button value="address">详细地址</el-radio-button>
          <el-radio-button value="latlng">经纬度</el-radio-button>
        </el-radio-group>
      </el-form-item>
    </template>

    <!-- ==================== 数字 ==================== -->
    <template v-else-if="fieldType === 'number'">
      <el-divider content-position="left">数字属性</el-divider>
      <el-form-item label="数字格式">
        <el-radio-group v-model="config.numberFormat">
          <el-radio-button value="plain">常规数字</el-radio-button>
          <el-radio-button value="percent">百分比</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="小数位数">
        <el-input-number v-model="config.precision" :min="0" :max="10" controls-position="right" />
      </el-form-item>
      <el-form-item label="千分位">
        <el-switch v-model="config.thousandSeparator" />
      </el-form-item>
      <el-form-item label="前缀">
        <el-input v-model="config.prefix" placeholder="约" style="width: 160px" />
      </el-form-item>
      <el-form-item label="后缀">
        <el-input v-model="config.suffix" placeholder="kg" style="width: 160px" />
      </el-form-item>
      <el-form-item label="最小值">
        <el-input-number v-model="config.min" controls-position="right" />
      </el-form-item>
      <el-form-item label="最大值">
        <el-input-number v-model="config.max" controls-position="right" />
      </el-form-item>
      <el-form-item label="默认值">
        <el-input-number v-model="defaultNumber" controls-position="right" />
      </el-form-item>
    </template>

    <!-- ==================== 货币 ==================== -->
    <template v-else-if="fieldType === 'currency'">
      <el-divider content-position="left">货币属性</el-divider>
      <el-form-item label="币种">
        <el-select v-model="config.currency" style="width: 100%">
          <el-option
            v-for="c in CURRENCY_OPTIONS"
            :key="c.value"
            :label="`${c.label}（${c.symbol}）`"
            :value="c.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="符号位置">
        <el-radio-group v-model="config.currencySymbolPosition">
          <el-radio-button value="prefix">数字前</el-radio-button>
          <el-radio-button value="suffix">数字后</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="小数位数">
        <el-input-number v-model="config.precision" :min="0" :max="10" controls-position="right" />
      </el-form-item>
      <el-form-item label="千分位">
        <el-switch v-model="config.thousandSeparator" />
      </el-form-item>
      <el-form-item label="默认值">
        <el-input-number v-model="defaultNumber" controls-position="right" />
      </el-form-item>
    </template>

    <!-- ==================== 进度 ==================== -->
    <template v-else-if="fieldType === 'progress'">
      <el-divider content-position="left">进度属性</el-divider>
      <el-form-item label="显示样式">
        <el-radio-group v-model="config.progressStyle">
          <el-radio-button value="bar">进度条</el-radio-button>
          <el-radio-button value="percent">百分比</el-radio-button>
          <el-radio-button value="number">纯数字</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="步长">
        <el-input-number v-model="config.step" :min="1" :max="100" controls-position="right" />
      </el-form-item>
      <el-form-item label="颜色规则">
        <el-radio-group v-model="config.progressColorMode">
          <el-radio-button value="single">统一颜色</el-radio-button>
          <el-radio-button value="threshold">按阈值变色</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="颜色">
        <el-color-picker v-model="config.progressColor" />
      </el-form-item>
      <el-form-item v-if="config.progressColorMode === 'threshold'" label="阈值规则">
        <div class="attr-list">
          <div v-for="(rule, idx) in config.progressRules || []" :key="idx" class="attr-row">
            <span class="attr-row__label">低于</span>
            <el-input-number v-model="rule.below" :min="0" :max="100" size="small" controls-position="right" />
            <span class="attr-row__label">%</span>
            <el-color-picker v-model="rule.color" size="small" />
            <el-button link size="small" @click="removeProgressRule(idx)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          <el-button link type="primary" size="small" @click="addProgressRule">
            <el-icon><Plus /></el-icon> 添加规则
          </el-button>
        </div>
      </el-form-item>
    </template>

    <!-- ==================== 评分 ==================== -->
    <template v-else-if="fieldType === 'rating'">
      <el-divider content-position="left">评分属性</el-divider>
      <el-form-item label="最大分值">
        <el-input-number v-model="config.maxRating" :min="1" :max="10" controls-position="right" />
      </el-form-item>
      <el-form-item label="图标样式">
        <el-select v-model="config.ratingIcon" style="width: 100%">
          <el-option
            v-for="opt in RATING_ICON_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="图标颜色">
        <el-color-picker v-model="config.ratingColor" />
      </el-form-item>
      <el-form-item label="允许半星">
        <el-switch v-model="config.allowHalf" />
      </el-form-item>
    </template>

    <!-- ==================== 单选 / 多选 / 流程 ==================== -->
    <template v-else-if="isOptionType">
      <el-divider content-position="left">选项</el-divider>
      <el-form-item label="选项列表">
        <div class="attr-list">
          <div v-for="(opt, idx) in config.options || []" :key="idx" class="option-block">
            <div class="attr-row">
              <span class="option-color-dot" :style="{ background: optionColorCss(opt.color) }" />
              <el-input v-model="opt.label" placeholder="选项名称" size="small" />
              <el-select v-model="opt.color" placeholder="颜色" size="small" style="width: 82px">
                <el-option v-for="c in OPTION_COLORS" :key="c" :label="c" :value="c" />
              </el-select>
              <el-button link size="small" :disabled="idx === 0" @click="moveOption(idx, -1)">
                <el-icon><Top /></el-icon>
              </el-button>
              <el-button
                link
                size="small"
                :disabled="idx === (config.options || []).length - 1"
                @click="moveOption(idx, 1)"
              >
                <el-icon><Bottom /></el-icon>
              </el-button>
              <el-button link size="small" @click="removeOption(idx)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-input v-model="opt.desc" size="small" placeholder="选项说明" class="option-block__desc" />
          </div>
          <el-button link type="primary" size="small" @click="addOption">
            <el-icon><Plus /></el-icon> 添加选项
          </el-button>
        </div>
      </el-form-item>
      <el-form-item label="默认选项">
        <el-select v-model="config.defaultOption" clearable placeholder="无" style="width: 100%">
          <el-option v-for="opt in filledOptions" :key="opt.label" :label="opt.label" :value="opt.label" />
        </el-select>
      </el-form-item>
      <el-form-item label="允许新增">
        <el-switch v-model="config.allowAddOption" />
      </el-form-item>
      <el-form-item v-if="fieldType === 'multi_select'" label="最大选择数">
        <el-input-number v-model="config.maxSelect" :min="1" :max="100" controls-position="right" />
      </el-form-item>
    </template>

    <!-- ==================== 复选框 ==================== -->
    <template v-else-if="fieldType === 'checkbox' || fieldType === 'check'">
      <el-divider content-position="left">复选框属性</el-divider>
      <el-form-item label="默认状态">
        <el-radio-group v-model="config.defaultChecked">
          <el-radio-button :value="true">默认勾选</el-radio-button>
          <el-radio-button :value="false">默认不勾选</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="显示样式">
        <el-radio-group v-model="config.checkboxStyle">
          <el-radio-button value="checkbox">勾选框</el-radio-button>
          <el-radio-button value="switch">开关</el-radio-button>
        </el-radio-group>
      </el-form-item>
    </template>

    <!-- ==================== 日期 ==================== -->
    <template v-else-if="fieldType === 'date' || fieldType === 'date_range'">
      <el-divider content-position="left">日期属性</el-divider>
      <el-form-item label="日期格式">
        <el-select v-model="config.dateFormat" style="width: 100%">
          <el-option v-for="f in DATE_FORMAT_OPTIONS" :key="f" :label="f" :value="f" />
        </el-select>
      </el-form-item>
      <el-form-item label="包含时间">
        <el-switch v-model="config.withTime" />
      </el-form-item>
      <el-form-item v-if="config.withTime" label="时间格式">
        <el-radio-group v-model="config.timeFormat">
          <el-radio-button value="24h">24 小时制</el-radio-button>
          <el-radio-button value="12h">12 小时制</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="默认值">
        <el-radio-group v-model="config.dateDefaultMode">
          <el-radio-button value="none">无</el-radio-button>
          <el-radio-button value="today">当前日期</el-radio-button>
          <el-radio-button value="now">当前时间</el-radio-button>
          <el-radio-button value="fixed">固定日期</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="config.dateDefaultMode === 'fixed'" label="固定日期">
        <el-date-picker
          v-model="defaultText"
          type="date"
          value-format="YYYY-MM-DD"
          style="width: 100%"
        />
      </el-form-item>
    </template>

    <!-- ==================== 系统时间字段 ==================== -->
    <template v-else-if="isSystemTimeType">
      <el-divider content-position="left">显示格式</el-divider>
      <el-form-item label="日期格式">
        <el-select v-model="config.dateFormat" style="width: 100%">
          <el-option v-for="f in DATE_FORMAT_OPTIONS" :key="f" :label="f" :value="f" />
        </el-select>
      </el-form-item>
      <el-form-item label="包含时间">
        <el-switch v-model="config.withTime" />
      </el-form-item>
      <el-form-item v-if="config.withTime" label="时间格式">
        <el-radio-group v-model="config.timeFormat">
          <el-radio-button value="24h">24 小时制</el-radio-button>
          <el-radio-button value="12h">12 小时制</el-radio-button>
        </el-radio-group>
      </el-form-item>
    </template>

    <!-- ==================== 人员 ==================== -->
    <template v-else-if="fieldType === 'user'">
      <el-divider content-position="left">人员属性</el-divider>
      <el-form-item label="选择模式">
        <el-radio-group v-model="config.userMode">
          <el-radio-button value="single">单人</el-radio-button>
          <el-radio-button value="multiple">多人</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="人员范围">
        <el-radio-group v-model="config.userScope">
          <el-radio-button value="all">全组织</el-radio-button>
          <el-radio-button value="dept">指定部门</el-radio-button>
          <el-radio-button value="self">仅自己</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="config.userScope === 'dept'" label="指定部门">
        <el-tree-select
          v-model="config.userDeptIds"
          :data="orgTree"
          multiple
          show-checkbox
          check-strictly
          node-key="id"
          :props="{ label: 'name', children: 'children' }"
          placeholder="选择部门"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="显示样式">
        <el-radio-group v-model="config.userDisplay">
          <el-radio-button value="avatar">仅头像</el-radio-button>
          <el-radio-button value="name">仅姓名</el-radio-button>
          <el-radio-button value="both">头像+姓名</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="默认当前用户">
        <el-switch v-model="config.defaultCurrentUser" />
      </el-form-item>
    </template>

    <!-- ==================== 群组 ==================== -->
    <template v-else-if="fieldType === 'group'">
      <el-divider content-position="left">群组属性</el-divider>
      <el-form-item label="选择模式">
        <el-radio-group v-model="config.groupMode">
          <el-radio-button value="single">单群</el-radio-button>
          <el-radio-button value="multiple">多群</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="可选范围">
        <el-radio-group v-model="config.groupScope">
          <el-radio-button value="joined">我加入的群</el-radio-button>
          <el-radio-button value="all">组织内所有群</el-radio-button>
        </el-radio-group>
      </el-form-item>
    </template>

    <!-- ==================== 附件 ==================== -->
    <template v-else-if="fieldType === 'attachment'">
      <el-divider content-position="left">附件属性</el-divider>
      <el-form-item label="文件类型">
        <el-radio-group v-model="config.fileTypeLimit">
          <el-radio-button value="any">不限</el-radio-button>
          <el-radio-button value="image">仅图片</el-radio-button>
          <el-radio-button value="doc">仅文档</el-radio-button>
          <el-radio-button value="custom">自定义</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="config.fileTypeLimit === 'custom'" label="后缀">
        <el-select
          v-model="config.allowedExtensions"
          multiple
          filterable
          allow-create
          default-first-option
          placeholder=".pdf"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="大小限制">
        <el-input-number v-model="config.maxFileSizeMb" :min="1" :max="2048" controls-position="right" />
        <span class="attr-hint">MB</span>
      </el-form-item>
      <el-form-item label="数量限制">
        <el-input-number v-model="config.maxFiles" :min="1" :max="100" controls-position="right" />
        <span class="attr-hint">个</span>
      </el-form-item>
      <el-form-item label="显示样式">
        <el-radio-group v-model="config.attachmentDisplay">
          <el-radio-button value="list">列表</el-radio-button>
          <el-radio-button value="thumbnail">缩略图</el-radio-button>
          <el-radio-button value="cover">封面</el-radio-button>
        </el-radio-group>
      </el-form-item>
    </template>

    <!-- ==================== 条码 ==================== -->
    <template v-else-if="fieldType === 'barcode'">
      <el-divider content-position="left">条码属性</el-divider>
      <el-form-item label="码类型">
        <el-radio-group v-model="config.barcodeType">
          <el-radio-button value="qrcode">二维码</el-radio-button>
          <el-radio-button value="code128">条形码</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="内容来源">
        <el-radio-group v-model="config.barcodeSource">
          <el-radio-button value="self">当前字段</el-radio-button>
          <el-radio-button value="field">引用字段</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="config.barcodeSource === 'field'" label="引用字段">
        <el-select v-model="config.barcodeSourceFieldId" placeholder="选择字段" style="width: 100%">
          <el-option v-for="f in otherFields" :key="f.id" :label="f.name" :value="f.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="显示原文">
        <el-switch v-model="config.showBarcodeText" />
      </el-form-item>
      <el-form-item label="码尺寸">
        <el-input-number v-model="config.barcodeSize" :min="60" :max="320" :step="10" controls-position="right" />
        <span class="attr-hint">px</span>
      </el-form-item>
      <el-form-item label="码颜色">
        <el-color-picker v-model="config.barcodeColor" />
      </el-form-item>
    </template>

    <!-- ==================== 自动编号 ==================== -->
    <template v-else-if="fieldType === 'auto_number'">
      <el-divider content-position="left">编号规则</el-divider>
      <el-form-item label="前缀">
        <el-input v-model="config.prefix" placeholder="ORD-" />
      </el-form-item>
      <el-form-item label="日期部分">
        <el-select v-model="config.dateFormat" clearable placeholder="无" style="width: 100%">
          <el-option v-for="f in DATE_FORMAT_OPTIONS" :key="f" :label="f" :value="f" />
        </el-select>
      </el-form-item>
      <el-form-item label="序号位数">
        <el-input-number v-model="config.digits" :min="1" :max="12" controls-position="right" />
      </el-form-item>
      <el-form-item label="后缀">
        <el-input v-model="config.suffix" placeholder="-A" />
      </el-form-item>
      <el-form-item label="重置周期">
        <el-radio-group v-model="config.resetCycle">
          <el-radio-button value="never">不重置</el-radio-button>
          <el-radio-button value="year">按年</el-radio-button>
          <el-radio-button value="month">按月</el-radio-button>
          <el-radio-button value="day">按日</el-radio-button>
        </el-radio-group>
      </el-form-item>
    </template>

    <!-- ==================== 按钮 ==================== -->
    <template v-else-if="fieldType === 'button'">
      <el-divider content-position="left">按钮属性</el-divider>
      <el-form-item label="按钮文案">
        <el-input v-model="buttonConfig.label" placeholder="按钮" />
      </el-form-item>
      <el-form-item label="按钮颜色">
        <el-color-picker v-model="buttonConfig.color" />
      </el-form-item>
      <el-form-item label="触发动作">
        <el-radio-group v-model="buttonConfig.actionType">
          <el-radio-button value="openUrl">打开链接</el-radio-button>
          <el-radio-button value="updateRecord">更新记录</el-radio-button>
          <el-radio-button value="automation">运行自动化</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="buttonConfig.actionType === 'openUrl'" label="链接地址">
        <el-input v-model="buttonConfig.actionUrl" placeholder="https://" />
      </el-form-item>
      <el-form-item label="可用条件">
        <div class="attr-row">
          <el-select v-model="buttonConfig.conditionFieldId" clearable placeholder="字段" style="width: 45%">
            <el-option v-for="f in otherFields" :key="f.id" :label="f.name" :value="f.id" />
          </el-select>
          <span class="attr-row__label">=</span>
          <el-input v-model="buttonConfig.conditionValue" placeholder="值" style="width: 45%" />
        </div>
      </el-form-item>
      <el-form-item label="二次确认">
        <el-input v-model="buttonConfig.confirmText" placeholder="留空则不确认" />
      </el-form-item>
      <el-form-item label="成功提示">
        <el-input v-model="buttonConfig.successText" />
      </el-form-item>
      <el-form-item label="失败提示">
        <el-input v-model="buttonConfig.failText" />
      </el-form-item>
    </template>

    <!-- ==================== 关联 ==================== -->
    <template v-else-if="isLinkType">
      <el-divider content-position="left">关联属性</el-divider>
      <el-form-item label="目标表">
        <el-select
          v-model="config.linkTargetTableId"
          placeholder="选择关联数据表"
          style="width: 100%"
          @change="onLinkTargetChange"
        >
          <el-option v-for="t in linkableTables" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="显示字段">
        <el-select v-model="config.linkDisplayFieldId" clearable placeholder="主字段" style="width: 100%">
          <el-option v-for="f in linkTargetFields" :key="f.id" :label="f.name" :value="f.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="fieldType === 'bidirectional_link'" label="反向字段">
        <el-select v-model="config.reverseFieldId" clearable placeholder="目标表中的反向关联字段" style="width: 100%">
          <el-option v-for="f in linkFieldCandidates" :key="f.id" :label="f.name" :value="f.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="关联数量">
        <el-radio-group v-model="config.allowMultipleLink">
          <el-radio-button :value="false">单条</el-radio-button>
          <el-radio-button :value="true">多条</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="删除策略">
        <el-radio-group v-model="config.linkDeleteStrategy">
          <el-radio-button value="clear">清空引用</el-radio-button>
          <el-radio-button value="keep">保留失效引用</el-radio-button>
        </el-radio-group>
      </el-form-item>
    </template>

    <!-- ==================== 查找引用 / 汇总 ==================== -->
    <template v-else-if="fieldType === 'lookup' || fieldType === 'rollup'">
      <el-divider content-position="left">引用计算</el-divider>
      <el-form-item label="关联字段">
        <el-select
          v-model="config.linkFieldId"
          clearable
          placeholder="选择本表关联字段"
          style="width: 100%"
          @change="onLookupLinkFieldChange"
        >
          <el-option v-for="f in linkFields" :key="f.id" :label="f.name" :value="f.id" />
        </el-select>
      </el-form-item>
      <el-form-item :label="fieldType === 'rollup' ? '统计字段' : '引用字段'">
        <el-select v-model="config.targetFieldId" clearable placeholder="选择目标表字段" style="width: 100%">
          <el-option v-for="f in linkTargetFields" :key="f.id" :label="f.name" :value="f.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="fieldType === 'rollup'" label="汇总方式">
        <el-select v-model="config.aggregation" style="width: 100%">
          <el-option label="计数" value="count" />
          <el-option label="求和" value="sum" />
          <el-option label="平均值" value="average" />
          <el-option label="最小值" value="min" />
          <el-option label="最大值" value="max" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="fieldType === 'rollup'" label="结果格式">
        <el-radio-group v-model="config.rollupFormat">
          <el-radio-button value="number">数字</el-radio-button>
          <el-radio-button value="date">日期</el-radio-button>
          <el-radio-button value="text">文本</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-else label="聚合方式">
        <el-select v-model="config.aggregation" clearable placeholder="原值" style="width: 100%">
          <el-option label="原值拼接" value="concat" />
          <el-option label="计数" value="count" />
          <el-option label="去重计数" value="distinctCount" />
          <el-option label="求和" value="sum" />
          <el-option label="最大值" value="max" />
          <el-option label="最小值" value="min" />
        </el-select>
      </el-form-item>
    </template>

    <!-- ==================== 公式 ==================== -->
    <template v-else-if="fieldType === 'formula'">
      <el-divider content-position="left">公式属性</el-divider>
      <el-form-item label="公式表达式">
        <el-input v-model="config.formulaExpr" type="textarea" :rows="3" placeholder="{单价} * {数量}" />
      </el-form-item>
      <el-form-item v-if="$slots['formula-extra']" label=" ">
        <slot name="formula-extra" />
      </el-form-item>
      <el-form-item label="结果格式">
        <el-radio-group v-model="config.formulaResultFormat">
          <el-radio-button value="auto">自动识别</el-radio-button>
          <el-radio-button value="text">文本</el-radio-button>
          <el-radio-button value="number">数字</el-radio-button>
          <el-radio-button value="date">日期</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="错误显示">
        <el-radio-group v-model="config.formulaErrorDisplay">
          <el-radio-button value="empty">空</el-radio-button>
          <el-radio-button value="zero">0</el-radio-button>
          <el-radio-button value="custom">自定义</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="config.formulaErrorDisplay === 'custom'" label="错误文案">
        <el-input v-model="config.formulaErrorText" placeholder="#ERROR" />
      </el-form-item>
    </template>

    <!-- ==================== AI 字段 ==================== -->
    <template v-else-if="fieldType === 'ai_text' || fieldType === 'ai_select'">
      <el-divider content-position="left">AI 字段属性</el-divider>
      <el-form-item label="AI 提示词">
        <el-input
          v-model="base.aiPrompt"
          type="textarea"
          :rows="3"
          placeholder="根据{需求描述}生成一段概要"
        />
      </el-form-item>
      <el-form-item label="源字段">
        <el-select v-model="config.sourceFieldIds" multiple placeholder="参与生成的字段" style="width: 100%">
          <el-option v-for="f in aiSourceFields" :key="f.id" :label="f.name" :value="f.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="触发时机">
        <el-radio-group v-model="config.aiTriggerMode">
          <el-radio-button value="manual">手动生成</el-radio-button>
          <el-radio-button value="onCreate">创建记录时</el-radio-button>
          <el-radio-button value="onDependencyChange">源字段变更时</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="温度">
        <el-slider v-model="config.aiTemperature" :min="0" :max="1" :step="0.1" show-input />
      </el-form-item>
      <el-form-item label="最大 Token">
        <el-input-number
          v-model="config.aiMaxTokens"
          :min="64"
          :max="32000"
          :step="64"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item label="兜底策略">
        <el-radio-group v-model="config.aiFallbackMode">
          <el-radio-button value="empty">留空</el-radio-button>
          <el-radio-button value="text">固定文本</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="config.aiFallbackMode === 'text'" label="兜底文案">
        <el-input v-model="config.aiFallbackText" />
      </el-form-item>
      <el-form-item v-if="fieldType === 'ai_select'" label="选项列表">
        <div class="attr-list">
          <div v-for="(opt, idx) in config.options || []" :key="idx" class="attr-row">
            <span class="option-color-dot" :style="{ background: optionColorCss(opt.color) }" />
            <el-input v-model="opt.label" placeholder="选项名称" size="small" />
            <el-select v-model="opt.color" placeholder="颜色" size="small" style="width: 82px">
              <el-option v-for="c in OPTION_COLORS" :key="c" :label="c" :value="c" />
            </el-select>
            <el-button link size="small" @click="removeOption(idx)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          <el-button link type="primary" size="small" @click="addOption">
            <el-icon><Plus /></el-icon> 添加选项
          </el-button>
        </div>
      </el-form-item>
    </template>
  </el-form>
</template>

<script setup lang="ts">
/**
 * 字段属性面板（新增字段 / 字段配置共用）
 *
 * 约定：
 * - `base` 与 `config` 由父级持有，本组件就地修改（父级保存时读取同一对象）。
 * - `config` 的键位由 `utils/bitableFieldConfig` 约束；切换字段类型时本组件把 config 重置为
 *   该类型的默认配置，避免残留上一个类型的键。
 */
import { computed, onMounted, ref } from 'vue'
import { Delete, Plus, Top, Bottom } from '@element-plus/icons-vue'
import { listFields } from '@/api/modules/bitable'
import { getOrgTree } from '@/api/modules/user'
import {
  CURRENCY_OPTIONS,
  DATE_FORMAT_OPTIONS,
  OPTION_COLORS,
  RATING_ICON_OPTIONS,
  READONLY_FIELD_TYPES,
  SYSTEM_FIELD_TYPES,
  UNIQUE_CAPABLE_TYPES,
  createDefaultFieldConfig,
} from '@/utils/bitableFieldConfig'
import type { BitableField, BitableTable, FieldConfig } from '@/types/bitable'

interface FieldBaseModel {
  name: string
  description: string
  width: number
  required: boolean
  aiPrompt: string
}

const props = withDefaults(
  defineProps<{
    fieldType: string
    config: FieldConfig
    base: FieldBaseModel
    fields: BitableField[]
    tables: BitableTable[]
    activeTableId: number | null
    /** 显示字段类型选择器（新增字段时） */
    showTypeSelector?: boolean
    /** 当前正在编辑的字段 ID（用于排除自身，避免自引用） */
    editingFieldId?: number | null
    /** 字段类型下拉分组 */
    typeGroups?: { label: string; options: { label: string; value: string }[] }[]
  }>(),
  {
    showTypeSelector: false,
    editingFieldId: null,
    typeGroups: () => [],
  },
)

const emit = defineEmits<{ 'update:fieldType': [value: string] }>()

const linkTargetFields = ref<BitableField[]>([])
const orgTree = ref<{ id: number; name: string }[]>([])

// ==================== 派生 ====================

const isSystemTimeType = computed(() =>
  ['created_time', 'modified_time', 'last_modified_time'].includes(props.fieldType),
)
const isOptionType = computed(() =>
  ['single_select', 'multi_select', 'process'].includes(props.fieldType),
)
const isLinkType = computed(() => ['link', 'bidirectional_link'].includes(props.fieldType))

/** 系统字段/只读计算字段不提供「必填」配置 */
const showRequired = computed(() => !READONLY_FIELD_TYPES.has(props.fieldType))
const showUnique = computed(() => UNIQUE_CAPABLE_TYPES.has(props.fieldType))
const showPlaceholder = computed(
  () => !READONLY_FIELD_TYPES.has(props.fieldType) && !SYSTEM_FIELD_TYPES.has(props.fieldType),
)

/** 本表其它字段（引用类下拉的数据源） */
const otherFields = computed(() => props.fields.filter((f) => f.id !== props.editingFieldId))
/** 本表的关联字段（lookup/rollup 的依赖） */
const linkFields = computed(() =>
  otherFields.value.filter((f) => ['link', 'bidirectional_link'].includes(f.fieldType)),
)
/** 目标表中的关联字段（双向关联的反向字段候选） */
const linkFieldCandidates = computed(() =>
  linkTargetFields.value.filter((f) => ['link', 'bidirectional_link'].includes(f.fieldType)),
)
const linkableTables = computed(() => props.tables.filter((t) => t.id !== props.activeTableId))
const filledOptions = computed(() =>
  (props.config.options || []).filter((o) => String(o.label || '').trim()),
)
const aiSourceFields = computed(() =>
  otherFields.value.filter((f) => f.fieldType !== 'ai_text' && f.fieldType !== 'ai_select'),
)

/** 文本「格式校验」：无正则即视为不限 */
const textPatternMode = computed({
  get: () => (props.config.pattern ? 'regex' : 'none'),
  set: (mode: string) => {
    if (mode === 'none') {
      props.config.pattern = ''
      props.config.patternMessage = ''
    }
  },
})

/** 按钮配置：保证对象存在（模板里不再判空） */
if (!props.config.button) {
  props.config.button = { actionType: 'openUrl', color: '#3B82F6' }
}
const buttonConfig = computed(() => props.config.button as NonNullable<FieldConfig['button']>)

/** 人员可选部门（规范键，见 FieldConfig.userDeptIds） */
if (props.config.userDeptIds === undefined) props.config.userDeptIds = []

/** 默认值的类型安全代理（FieldConfig.defaultValue 是 unknown，不能直接 v-model） */
const defaultText = computed({
  get: () => (typeof props.config.defaultValue === 'string' ? props.config.defaultValue : ''),
  set: (v: string) => {
    props.config.defaultValue = v || undefined
  },
})
const defaultNumber = computed({
  get: () => (typeof props.config.defaultValue === 'number' ? props.config.defaultValue : undefined),
  set: (v: number | undefined) => {
    props.config.defaultValue = v ?? undefined
  },
})

// ==================== 交互 ====================

function onTypeChange(next: string) {
  // 切换类型：清空旧键，避免残留上一类型的配置
  for (const key of Object.keys(props.config)) {
    delete (props.config as Record<string, unknown>)[key]
  }
  Object.assign(props.config, createDefaultFieldConfig(next))
  emit('update:fieldType', next)
}

const OPTION_COLOR_HEX: Record<string, string> = {
  red: '#EF4444', orange: '#F97316', yellow: '#EAB308', green: '#22C55E',
  teal: '#14B8A6', blue: '#3B82F6', purple: '#8B5CF6', pink: '#EC4899', gray: '#94A3B8',
}

function optionColorCss(color?: string) {
  return (color && OPTION_COLOR_HEX[color]) || '#94A3B8'
}

function addOption() {
  if (!props.config.options) props.config.options = []
  props.config.options.push({
    label: '',
    color: OPTION_COLORS[props.config.options.length % OPTION_COLORS.length],
  })
}

function removeOption(index: number) {
  props.config.options?.splice(index, 1)
}

function moveOption(index: number, delta: number) {
  const list = props.config.options
  if (!list) return
  const target = index + delta
  if (target < 0 || target >= list.length) return
  const [item] = list.splice(index, 1)
  list.splice(target, 0, item)
}

function addProgressRule() {
  if (!props.config.progressRules) props.config.progressRules = []
  props.config.progressRules.push({ below: 30, color: '#F59E0B' })
}

function removeProgressRule(index: number) {
  props.config.progressRules?.splice(index, 1)
}

async function loadTargetFields(tableId?: number | null) {
  if (!tableId) {
    linkTargetFields.value = []
    return
  }
  try {
    const res = await listFields(tableId)
    linkTargetFields.value = Array.isArray(res) ? res : (res as any)?.data || []
  } catch {
    linkTargetFields.value = []
  }
}

function onLinkTargetChange(tableId?: number) {
  props.config.linkDisplayFieldId = undefined
  props.config.reverseFieldId = undefined
  loadTargetFields(tableId)
}

function onLookupLinkFieldChange(fieldId?: number) {
  props.config.targetFieldId = undefined
  const linkField = props.fields.find((f) => f.id === fieldId)
  loadTargetFields(linkField?.config?.linkTargetTableId)
}

onMounted(async () => {
  if (isLinkType.value && props.config.linkTargetTableId) {
    await loadTargetFields(props.config.linkTargetTableId)
  }
  if (['lookup', 'rollup'].includes(props.fieldType) && props.config.linkFieldId) {
    const linkField = props.fields.find((f) => f.id === props.config.linkFieldId)
    await loadTargetFields(linkField?.config?.linkTargetTableId)
  }
  if (props.fieldType === 'user' && props.config.userScope === 'dept') {
    try {
      const tree = await getOrgTree()
      orgTree.value = Array.isArray(tree) ? (tree as any) : (tree as any)?.data || []
    } catch {
      orgTree.value = []
    }
  }
})
</script>

<style scoped lang="scss">
.field-attr-form {
  :deep(.el-divider--horizontal) {
    margin: 16px 0 12px;
  }
  :deep(.el-divider__text) {
    font-size: 12px;
    color: var(--color-text-secondary, #64748b);
  }
}

.attr-hint {
  margin-left: 8px;
  font-size: 12px;
  color: var(--color-text-secondary, #64748b);
}

.attr-list {
  width: 100%;
}

.attr-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.attr-row__label {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--color-text-secondary, #64748b);
}

.option-block {
  margin-bottom: 8px;
}

.option-block__desc {
  margin-top: 4px;
}

.option-color-dot {
  flex-shrink: 0;
  width: 12px;
  height: 12px;
  border-radius: 3px;
  display: inline-block;
}
</style>
