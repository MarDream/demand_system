/**
 * 组织架构管理 API
 * 重新导出 user.ts 中的组织架构相关接口，提供更清晰的语义
 */
import {
  getRegionTree,
  getDepartmentTree,
  getPositionList,
  createRegion,
  updateRegion,
  deleteRegion,
  createDepartment,
  updateDepartment,
  deleteDepartment,
  createPosition,
  updatePosition,
  deletePosition,
} from './user'

// 区域管理
export {
  getRegionTree,
  createRegion,
  updateRegion,
  deleteRegion,
}

// 部门管理
export {
  getDepartmentTree,
  createDepartment,
  updateDepartment,
  deleteDepartment,
}

// 岗位管理
export {
  getPositionList,
  createPosition,
  updatePosition,
  deletePosition,
}
