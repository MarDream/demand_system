import LogicFlow from '@logicflow/core'
import { CircleNode, CircleNodeModel, RectNode, RectNodeModel, DiamondNode, DiamondNodeModel } from '@logicflow/core'

// 节点样式配置
export const nodeStyleConfig = {
  start: {
    fill: '#67c23a',
    stroke: '#67c23a',
    strokeWidth: 2,
    fillOpacity: 0.1,
    r: 40
  },
  approval: {
    fill: '#409eff',
    stroke: '#409eff',
    strokeWidth: 2,
    fillOpacity: 0.1,
    width: 120,
    height: 60,
    rx: 5,
    ry: 5
  },
  cc: {
    fill: '#e6a23c',
    stroke: '#e6a23c',
    strokeWidth: 2,
    fillOpacity: 0.1,
    width: 120,
    height: 60,
    rx: 5,
    ry: 5
  },
  condition: {
    fill: '#f56c6c',
    stroke: '#f56c6c',
    strokeWidth: 2,
    fillOpacity: 0.1,
    width: 80,
    height: 80
  },
  end: {
    fill: '#909399',
    stroke: '#909399',
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
