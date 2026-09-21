/**
 * ECharts 实例注册表：按组件 id 存放 vue-echarts 实例引用，
 * 供「导出为图片」等跨层操作获取画布。
 */
const registry = new Map<string, any>()

export const chartRegistry = {
  set(key: string | number, instance: any) {
    if (instance) registry.set(String(key), instance)
    else registry.delete(String(key))
  },
  get(key: string | number): any {
    return registry.get(String(key))
  },
  delete(key: string | number) {
    registry.delete(String(key))
  },
}
