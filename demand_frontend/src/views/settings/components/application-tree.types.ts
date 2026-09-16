/**
 * 「模型应用」页左侧功能点目录树的选中态。
 *
 * 选中态由父组件持有（单一数据源），树组件只负责渲染高亮与派发事件，
 * 右侧配置区据此决定展示哪些功能点。
 */
export interface ApplicationTreeSelection {
  /** all=全部应用（平铺视图）；ungrouped=某层级下的未分组；group=真实分组；application=具体功能点 */
  type: 'all' | 'ungrouped' | 'group' | 'application'
  /** group / ungrouped 时的分组ID；ungrouped 为父级分组ID（根层级为 null） */
  groupId: number | null
  /** application 时的功能点编码 */
  code: string | null
}
