package com.demand.system.module.user.service;

import com.demand.system.common.result.PageResult;
import com.demand.system.module.user.dto.UserCreateDTO;
import com.demand.system.module.user.dto.UserQueryDTO;
import com.demand.system.module.user.dto.UserUpdateDTO;
import com.demand.system.module.user.dto.UserVO;
import com.demand.system.module.user.dto.RosterExportLogVO;
import com.demand.system.module.user.dto.RosterImportResultVO;
import com.demand.system.module.user.dto.RosterStatsVO;
import com.demand.system.module.user.entity.RosterExportLog;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface UserService {

    PageResult<UserVO> list(UserQueryDTO query);

    /** 花名册统计板（在职/员工类型/用工状态分布），基于当前查询的组织范围 */
    RosterStatsVO rosterStats(UserQueryDTO query);

    /** Excel 导入花名册（模板列：姓名/手机号/邮箱/员工类型/用工状态/入职日期） */
    RosterImportResultVO importRoster(MultipartFile file, Long orgId);

    /** 按当前筛选导出花名册 XLSX，写入导出历史并返回历史条目 */
    RosterExportLogVO exportRoster(UserQueryDTO query);

    /** 导出历史（分页，倒序） */
    PageResult<RosterExportLogVO> listExportHistory(int pageNum, int pageSize);

    /** 读取导出历史内容（含文件字节），供下载 */
    RosterExportLog getExportLog(Long id);

    /** 删除导出历史（连带清除文件内容） */
    void deleteExportLog(Long id);

    /** 花名册导入模板（XLSX 字节） */
    byte[] buildImportTemplate();

    List<Map<String, Object>> listActiveUsers();

    UserVO getById(Long id);

    Long create(UserCreateDTO dto);

    void update(UserUpdateDTO dto);

    void delete(Long id);

    /**
     * 批量启用/停用。
     *
     * @param ids    目标用户ID集合
     * @param status 目标状态：active=启用, inactive=停用
     * @return 实际生效的用户数
     */
    int batchUpdateStatus(List<Long> ids, String status);

    /**
     * 批量删除。
     *
     * @param ids 目标用户ID集合
     * @return 实际删除的用户数
     */
    int batchDelete(List<Long> ids);

    boolean resetInitialPassword(Long id);

    void assignRoles(Long userId, List<Long> roleIds);

    List<Long> getUserRoleIds(Long userId);
}
