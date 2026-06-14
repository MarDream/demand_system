import LogicFlow from '@logicflow/core'
import { CircleNode, CircleNodeModel, RectNode, RectNodeModel, DiamondNode, DiamondNodeModel } from '@logicflow/core'

// 语义化颜色常量
const COLORS = {
  success: '#10B981',
  accent: '#0369A1',
  warning: '#F59E0B',
  danger: '#DC2626',
  purple: '#8B5CF6',
  muted: '#64748B',
}

// 节点样式配置
export const nodeStyleConfig = {
  start: {
    fill: COLORS.success,
    stroke: COLORS.success,
    strokeWidth: 2,
    fillOpacity: 0.1,
    r: 40
  },
  approval: {
    fill: COLORS.accent,
    stroke: COLORS.accent,
    strokeWidth: 2,
    fillOpacity: 0.1,
    width: 120,
    height: 60,
    rx: 5,
    ry: 5
  },
  cc: {
    fill: COLORS.warning,
    stroke: COLORS.warning,
    strokeWidth: 2,
    fillOpacity: 0.1,
    width: 120,
    height: 60,
    rx: 5,
    ry: 5
  },
  condition: {
    fill: COLORS.danger,
    stroke: COLORS.danger,
    strokeWidth: 2,
    fillOpacity: 0.1,
    width: 80,
    height: 80
  },
  parallel: {
    fill: COLORS.purple,
    stroke: COLORS.purple,
    strokeWidth: 2,
    fillOpacity: 0.1,
    width: 80,
    height: 80
  },
  end: {
    fill: COLORS.muted,
    stroke: COLORS.muted,
    strokeWidth: 2,
    fillOpacity: 0.1,
    r: 40
  }
}

// 注册自定义节点
export function registerCustomNodes(lf: LogicFlow) {
  // 开始节点 - 圆形
  lf.register({
    type: 'start',
    view: CircleNode,
    model: CircleNodeModel
  })

  // 审批节点 - 矩形
  lf.register({
    type: 'approval',
    view: RectNode,
    model: RectNodeModel
  })

  // 抄送节点 - 矩形
  lf.register({
    type: 'cc',
    view: RectNode,
    model: RectNodeModel
  })

  // 条件节点 - 菱形
  lf.register({
    type: 'condition',
    view: DiamondNode,
    model: DiamondNodeModel
  })

  // 并行网关 - 菱形
  lf.register({
    type: 'parallel',
    view: DiamondNode,
    model: DiamondNodeModel
  })

  // 结束节点 - 圆形
  lf.register({
    type: 'end',
    view: CircleNode,
    model: CircleNodeModel
  })

  // 设置节点样式
  lf.setTheme({
    circle: nodeStyleConfig.start,
    rect: nodeStyleConfig.approval,
    diamond: nodeStyleConfig.condition
  })
}
