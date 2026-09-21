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

/**
 * 网关（条件/并行）菱形模型：
 * 1. 锚点只有左右顶点 —— 连线必然水平进出网关，不再出现从顶部扎入的台阶式走线（BPMN 惯例）。
 * 2. 尺寸随文字自适应 —— 80×80 装不下「多角色并行评审」这类长标签，居中文字会溢出被连线穿过。
 *    加载场景由渲染数据直接携带 width/height；拖入/改名场景走官方 resize()。
 */
class GatewayDiamondModel extends DiamondNodeModel {

  updateText(value: any) {
    super.updateText(value)
    this.applyTextSize(typeof value === 'string' ? value : this.text?.value)
  }

  getDefaultAnchor() {
    return [
      { x: this.x - this.width / 2, y: this.y, id: `${this.id}_left` },
      { x: this.x + this.width / 2, y: this.y, id: `${this.id}_right` },
    ]
  }

  private applyTextSize(text?: string) {
    const label = (text ?? '').trim()
    const size = label
      ? { width: Math.max(80, Math.ceil(label.length * 14 / 0.66) + 40), height: 90 }
      : { width: 80, height: 80 }
    if (this.width === size.width && this.height === size.height) return
    try {
      // LF2 的 width/height 是 MobX computed，运行时改尺寸必须走官方 resize()
      ;(this as any).resize({ ...size, deltaX: 0, deltaY: 0 })
    } catch {
      // resize 在个别状态下不可用时退回直接赋值
      const self = this as any
      self.width = size.width
      self.height = size.height
    }
    const self = this as any
    self.anchors = self.getDefaultAnchor()
  }
}

/**
 * 审批/抄送矩形模型：锚点只有左右中点 —— 出线/入线全部水平，消除顶部/底部进出造成的台阶。
 */
class FlowRectModel extends RectNodeModel {

  getDefaultAnchor() {
    return [
      { x: this.x - this.width / 2, y: this.y, id: `${this.id}_left` },
      { x: this.x + this.width / 2, y: this.y, id: `${this.id}_right` },
    ]
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
    model: FlowRectModel
  })

  // 抄送节点 - 矩形
  lf.register({
    type: 'cc',
    view: RectNode,
    model: FlowRectModel
  })

  // 条件节点 - 菱形
  lf.register({
    type: 'condition',
    view: DiamondNode,
    model: GatewayDiamondModel
  })

  // 并行网关 - 菱形
  lf.register({
    type: 'parallel',
    view: DiamondNode,
    model: GatewayDiamondModel
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
