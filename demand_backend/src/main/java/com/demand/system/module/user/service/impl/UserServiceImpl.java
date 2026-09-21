package com.demand.system.module.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.PageResult;
import com.demand.system.module.auth.service.EmailService;
import com.demand.system.module.user.dto.RosterExportLogVO;
import com.demand.system.module.user.dto.RosterImportResultVO;
import com.demand.system.module.user.dto.RosterStatsVO;
import com.demand.system.module.user.dto.UserCreateDTO;
import com.demand.system.module.user.dto.UserQueryDTO;
import com.demand.system.module.user.dto.UserUpdateDTO;
import com.demand.system.module.user.dto.UserVO;
import com.demand.system.module.user.entity.RosterExportLog;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.RosterExportLogMapper;
import com.demand.system.module.user.mapper.UserMapper;
import com.demand.system.module.user.service.UserService;
import com.demand.system.module.organization.dto.SysOrgVO;
import com.demand.system.module.organization.service.SysOrgService;
import com.demand.system.module.rbac.entity.Role;
import com.demand.system.module.rbac.entity.UserRole;
import com.demand.system.module.rbac.mapper.RoleMapper;
import com.demand.system.module.rbac.mapper.UserRoleMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.demand.system.module.auth.security.SecurityUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    /** 主管理员账号ID（前端在成员列表里以 id=1 标记「主管理员」） */
    private static final long PRIMARY_ADMIN_ID = 1L;

    private final UserMapper userMapper;
    private final RosterExportLogMapper rosterExportLogMapper;
    private final SysOrgService sysOrgService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    public UserServiceImpl(UserMapper userMapper, RosterExportLogMapper rosterExportLogMapper, SysOrgService sysOrgService, PasswordEncoder passwordEncoder, EmailService emailService, UserRoleMapper userRoleMapper, RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.rosterExportLogMapper = rosterExportLogMapper;
        this.sysOrgService = sysOrgService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public PageResult<UserVO> list(UserQueryDTO query) {
        Page<User> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<User> userPage = userMapper.selectPage(page, buildListWrapper(query));

        List<UserVO> voList = userPage.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(voList, userPage.getTotal(), query.getPageNum(), query.getPageSize());
    }

    /**
     * 列表/导出/统计共用的查询构造：关键字、状态、员工类型、用工状态、入职时间范围 + 组织范围权限
     */
    private LambdaQueryWrapper<User> buildListWrapper(UserQueryDTO query) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(query.getUsername() != null, User::getUsername, query.getUsername())
                .like(query.getRealName() != null, User::getRealName, query.getRealName())
                .eq(query.getStatus() != null, User::getStatus, query.getStatus())
                .eq(query.getEmployeeType() != null && !query.getEmployeeType().isBlank(),
                        User::getEmployeeType, query.getEmployeeType())
                .eq(query.getWorkStatus() != null && !query.getWorkStatus().isBlank(),
                        User::getWorkStatus, query.getWorkStatus())
                .ge(query.getHireDateFrom() != null && !query.getHireDateFrom().isBlank(),
                        User::getHireDate, query.getHireDateFrom())
                .le(query.getHireDateTo() != null && !query.getHireDateTo().isBlank(),
                        User::getHireDate, query.getHireDateTo())
                .orderByDesc(User::getCreatedAt);

        // 确定有效的 orgId 过滤范围
        Long effectiveOrgId = resolveEffectiveOrgId(query.getOrgId());

        if (effectiveOrgId != null) {
            List<Long> orgIds = sysOrgService.getDescendantIds(effectiveOrgId);
            orgIds.add(effectiveOrgId);
            // 同时包含无组织归属的超级管理员用户（已绑定组织的超管不兜底，避免泄漏到无关组织列表）
            Set<Long> superAdminIds = filterUnassignedSuperAdminIds(collectSuperAdminUserIds());
            if (!superAdminIds.isEmpty()) {
                wrapper.and(w -> w.in(User::getOrgId, orgIds)
                        .or().in(User::getId, superAdminIds));
            } else {
                wrapper.in(User::getOrgId, orgIds);
            }
        } else if (!SecurityUtils.isSuperAdmin()) {
            // 非超管且无 orgId 归属：只能看到自己
            Long currentUserId = SecurityUtils.getCurrentUserId();
            wrapper.eq(User::getId, currentUserId);
        } else {
            // 超管无 orgId 过滤，可查看所有用户
            // Fallback to old fields for backward compatibility
            if (query.getRegionId() != null) {
                List<Long> orgIds = sysOrgService.getDescendantIds(query.getRegionId());
                orgIds.add(query.getRegionId());
                wrapper.and(w -> w.in(User::getOrgId, orgIds).or().in(User::getRegionId, orgIds));
            }
            if (query.getDepartmentId() != null) {
                List<Long> orgIds = sysOrgService.getDescendantIds(query.getDepartmentId());
                orgIds.add(query.getDepartmentId());
                wrapper.and(w -> w.in(User::getOrgId, orgIds).or().in(User::getDepartmentId, orgIds));
            }
        }
        return wrapper;
    }

    @Override
    public RosterStatsVO rosterStats(UserQueryDTO query) {
        List<User> users = userMapper.selectList(buildListWrapper(query));
        RosterStatsVO vo = new RosterStatsVO();
        for (User user : users) {
            boolean isActive = User.STATUS_ACTIVE.equals(user.getStatus())
                    && !User.WORK_STATUS_RESIGNED.equals(user.getWorkStatus());
            if (isActive) {
                vo.setActive(vo.getActive() + 1);
                String ws = user.getWorkStatus();
                if (User.WORK_STATUS_PROBATION.equals(ws)) {
                    vo.setProbation(vo.getProbation() + 1);
                } else if (User.WORK_STATUS_CONFIRMED.equals(ws)) {
                    vo.setConfirmed(vo.getConfirmed() + 1);
                } else if (User.WORK_STATUS_PENDING_RESIGN.equals(ws)) {
                    vo.setPendingResign(vo.getPendingResign() + 1);
                }
                String et = user.getEmployeeType();
                if (User.EMPLOYEE_TYPE_PART_TIME.equals(et)) {
                    vo.setPartTime(vo.getPartTime() + 1);
                } else if (User.EMPLOYEE_TYPE_INTERN.equals(et)) {
                    vo.setIntern(vo.getIntern() + 1);
                } else if (User.EMPLOYEE_TYPE_DISPATCH.equals(et)) {
                    vo.setDispatch(vo.getDispatch() + 1);
                } else if (User.EMPLOYEE_TYPE_OTHER.equals(et)) {
                    vo.setOther(vo.getOther() + 1);
                } else {
                    vo.setFullTime(vo.getFullTime() + 1);
                }
            } else if (User.WORK_STATUS_RESIGNED.equals(user.getWorkStatus())) {
                vo.setResigned(vo.getResigned() + 1);
            } else if (User.STATUS_INACTIVE.equals(user.getStatus())) {
                // 待入职：账号未激活且未离职（邀请后尚未办理入职）
                vo.setInactive(vo.getInactive() + 1);
            }
        }
        return vo;
    }

    @Override
    public RosterImportResultVO importRoster(MultipartFile file, Long orgId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要导入的文件");
        }
        RosterImportResultVO result = new RosterImportResultVO();
        try (var workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                int rowNo = i + 1;
                String realName = readCellString(row, 0);
                String phone = readCellString(row, 1);
                String email = readCellString(row, 2);
                String employeeTypeText = readCellString(row, 3);
                String workStatusText = readCellString(row, 4);
                String hireDateText = readCellString(row, 5);
                String birthdayText = readCellString(row, 6);
                try {
                    if (realName == null || realName.isBlank()) {
                        throw new BusinessException("姓名不能为空");
                    }
                    if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
                        throw new BusinessException("手机号缺失或格式不正确");
                    }
                    String username = "p" + phone;
                    if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0) {
                        throw new BusinessException("该手机号已存在（用户名冲突）");
                    }
                    User user = new User();
                    user.setUsername(username);
                    user.setRealName(realName.trim());
                    user.setPhone(phone.trim());
                    user.setEmail(email != null && !email.isBlank() ? email.trim() : null);
                    user.setEmployeeType(mapEmployeeType(employeeTypeText));
                    user.setWorkStatus(mapWorkStatus(workStatusText));
                    user.setHireDate(parseHireDate(hireDateText));
                    user.setBirthday(birthdayText == null || birthdayText.isBlank() ? null : parseDate(birthdayText));
                    if (orgId != null) {
                        user.setOrgId(orgId);
                        deriveOrgFields(user, orgId);
                    }
                    user.setPassword(passwordEncoder.encode(buildInitialPassword(username, phone, null)));
                    user.setStatus(User.STATUS_ACTIVE);
                    user.setJobNumber(generateJobNumber());
                    userMapper.insert(user);
                    if (user.getEmail() != null) {
                        // 邮箱缺失时不发邮件，管理员可稍后通过「邀请认证」补发
                        emailService.sendInitialPasswordEmail(user.getEmail(), username, buildInitialPassword(username, phone, null));
                    }
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } catch (Exception rowEx) {
                    result.setFailCount(result.getFailCount() + 1);
                    result.getFailures().add("第 " + rowNo + " 行（" + (realName == null ? "未填写" : realName) + "）：" + rowEx.getMessage());
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BusinessException("文件解析失败，请使用下载的导入模板填写后上传");
        }
        if (result.getSuccessCount() == 0 && result.getFailCount() == 0) {
            throw new BusinessException("未读取到数据行，请检查模板");
        }
        return result;
    }

    @Override
    public RosterExportLogVO exportRoster(UserQueryDTO query) {
        List<User> users = userMapper.selectList(buildListWrapper(query));
        List<UserVO> voList = users.stream().map(this::toVO).collect(Collectors.toList());

        String fileName = "花名册_" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".xlsx";
        byte[] bytes = buildRosterWorkbook(voList);

        RosterExportLog log = new RosterExportLog();
        log.setOperatorId(SecurityUtils.getCurrentUserId());
        log.setFileName(fileName);
        log.setTotal(voList.size());
        log.setCreatedAt(LocalDateTime.now());
        try {
            log.setFilterJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(query));
        } catch (Exception ignored) {
            log.setFilterJson(null);
        }
        log.setFileContent(Base64.getEncoder().encodeToString(bytes));
        rosterExportLogMapper.insert(log);

        RosterExportLogVO vo = new RosterExportLogVO();
        vo.setId(log.getId());
        vo.setFileName(fileName);
        vo.setTotal(log.getTotal());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }

    @Override
    public PageResult<RosterExportLogVO> listExportHistory(int pageNum, int pageSize) {
        Page<RosterExportLog> page = new Page<>(pageNum, pageSize);
        Page<RosterExportLog> result = rosterExportLogMapper.selectPage(page,
                new LambdaQueryWrapper<RosterExportLog>().orderByDesc(RosterExportLog::getId));
        List<RosterExportLogVO> voList = result.getRecords().stream().map(log -> {
            RosterExportLogVO vo = new RosterExportLogVO();
            vo.setId(log.getId());
            vo.setFileName(log.getFileName());
            vo.setTotal(log.getTotal());
            vo.setCreatedAt(log.getCreatedAt());
            if (log.getOperatorId() != null) {
                User operator = userMapper.selectById(log.getOperatorId());
                if (operator != null) {
                    vo.setOperatorName(operator.getRealName());
                }
            }
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(voList, result.getTotal(), pageNum, pageSize);
    }

    @Override
    public RosterExportLog getExportLog(Long id) {
        RosterExportLog log = rosterExportLogMapper.selectById(id);
        if (log == null) {
            throw new BusinessException("导出记录不存在");
        }
        return log;
    }

    @Override
    public void deleteExportLog(Long id) {
        if (rosterExportLogMapper.selectById(id) == null) {
            throw new BusinessException("导出记录不存在");
        }
        rosterExportLogMapper.deleteById(id);
    }

    @Override
    public byte[] buildImportTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("花名册导入模板");
            String[] headers = {"姓名", "手机号", "邮箱", "员工类型", "用工状态", "入职日期", "生日"};
            String[] example = {"张三", "13800138000", "zhangsan@example.com", "全职", "已转正", "2026-01-01", "1995-06-15"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
                sheet.setColumnWidth(i, 16 * 256);
            }
            Row sample = sheet.createRow(1);
            for (int i = 0; i < example.length; i++) {
                sample.createCell(i).setCellValue(example[i]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("模板生成失败");
        }
    }

    private byte[] buildRosterWorkbook(List<UserVO> users) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("花名册");
            String[][] columns = {
                    {"工号", "10"}, {"姓名", "14"}, {"部门", "20"}, {"角色", "18"},
                    {"手机号", "16"}, {"邮箱", "24"}, {"员工类型", "12"}, {"用工状态", "12"},
                    {"入职日期", "14"}, {"生日", "14"}, {"账号状态", "10"}, {"创建时间", "20"}
            };
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i][0]);
                sheet.setColumnWidth(i, Integer.parseInt(columns[i][1]) * 256);
            }
            DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            int rowIdx = 0;
            for (UserVO user : users) {
                Row row = sheet.createRow(++rowIdx);
                row.createCell(0).setCellValue(user.getJobNumber() == null ? "" : user.getJobNumber());
                row.createCell(1).setCellValue(user.getRealName() == null ? "" : user.getRealName());
                row.createCell(2).setCellValue(user.getRegionPath() == null ? "" : user.getRegionPath());
                row.createCell(3).setCellValue(user.getSystemRole() == null ? "" : user.getSystemRole());
                row.createCell(4).setCellValue(user.getPhone() == null ? "" : user.getPhone());
                row.createCell(5).setCellValue(user.getEmail() == null ? "" : user.getEmail());
                row.createCell(6).setCellValue(employeeTypeLabel(user.getEmployeeType()));
                row.createCell(7).setCellValue(workStatusLabel(user.getWorkStatus()));
                row.createCell(8).setCellValue(user.getHireDate() == null ? "" : user.getHireDate().toString());
                row.createCell(9).setCellValue(user.getBirthday() == null ? "" : user.getBirthday().toString());
                row.createCell(10).setCellValue(User.STATUS_ACTIVE.equals(user.getStatus()) ? "启用" : "停用");
                row.createCell(11).setCellValue(user.getCreatedAt() == null ? "" : user.getCreatedAt().format(dateTime));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("花名册导出失败");
        }
    }

    private String readCellString(Row row, int col) {
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double v = cell.getNumericCellValue();
                yield (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }

    private String mapEmployeeType(String text) {
        if (text == null || text.isBlank() || text.contains("全职")) {
            return User.EMPLOYEE_TYPE_FULL_TIME;
        }
        if (text.contains("兼职")) {
            return User.EMPLOYEE_TYPE_PART_TIME;
        }
        if (text.contains("实习")) {
            return User.EMPLOYEE_TYPE_INTERN;
        }
        if (text.contains("劳务") || text.contains("派遣")) {
            return User.EMPLOYEE_TYPE_DISPATCH;
        }
        return User.EMPLOYEE_TYPE_OTHER;
    }

    private String mapWorkStatus(String text) {
        if (text == null || text.isBlank() || text.contains("转正") || text.contains("正式")) {
            return User.WORK_STATUS_CONFIRMED;
        }
        if (text.contains("试用")) {
            return User.WORK_STATUS_PROBATION;
        }
        if (text.contains("待离职")) {
            return User.WORK_STATUS_PENDING_RESIGN;
        }
        if (text.contains("离职")) {
            return User.WORK_STATUS_RESIGNED;
        }
        return User.WORK_STATUS_CONFIRMED;
    }

    private LocalDate parseHireDate(String text) {
        if (text == null || text.isBlank()) {
            return LocalDate.now();
        }
        return parseDate(text);
    }

    private LocalDate parseDate(String text) {
        String value = text.trim().replace("/", "-").replace(".", "-");
        return LocalDate.parse(value.length() == 10 ? value : value.substring(0, Math.min(10, value.length())));
    }

    private String employeeTypeLabel(String code) {
        if (code == null) {
            return "";
        }
        return switch (code) {
            case User.EMPLOYEE_TYPE_FULL_TIME -> "全职";
            case User.EMPLOYEE_TYPE_PART_TIME -> "兼职";
            case User.EMPLOYEE_TYPE_INTERN -> "实习";
            case User.EMPLOYEE_TYPE_DISPATCH -> "劳务派遣";
            case User.EMPLOYEE_TYPE_OTHER -> "其他";
            default -> code;
        };
    }

    private String workStatusLabel(String code) {
        if (code == null) {
            return "";
        }
        return switch (code) {
            case User.WORK_STATUS_PROBATION -> "试用期";
            case User.WORK_STATUS_CONFIRMED -> "已转正";
            case User.WORK_STATUS_PENDING_RESIGN -> "待离职";
            case User.WORK_STATUS_RESIGNED -> "已离职";
            default -> code;
        };
    }

    @Override
    public List<Map<String, Object>> listActiveUsers() {
        LambdaQueryWrapper<User> baseWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getStatus, User.STATUS_ACTIVE)
                .select(User::getId, User::getUsername, User::getRealName, User::getAvatar, User::getOrgId)
                .orderByAsc(User::getUsername);

        // 非超管按组织范围过滤
        if (!SecurityUtils.isSuperAdmin()) {
            Long currentUserOrgId = getCurrentUserOrgId();
            if (currentUserOrgId != null) {
                List<Long> orgIds = sysOrgService.getDescendantIds(currentUserOrgId);
                orgIds.add(currentUserOrgId);
                baseWrapper.and(w -> w.in(User::getOrgId, orgIds));
            } else {
                // 无组织归属的非超管只能看到自己
                Long currentUserId = SecurityUtils.getCurrentUserId();
                baseWrapper.eq(User::getId, currentUserId);
                return userMapper.selectList(baseWrapper).stream()
                        .map(this::toActiveUserMap)
                        .toList();
            }
        } else {
            // 超管：拉取所有有组织或超级管理员角色的活跃用户
            baseWrapper.and(w -> w.isNotNull(User::getOrgId)
                    .or().isNotNull(User::getRegionId)
                    .or().isNotNull(User::getDepartmentId));

            Set<Long> superAdminIds = collectSuperAdminUserIds();
            if (!superAdminIds.isEmpty()) {
                baseWrapper.or().in(User::getId, superAdminIds);
            }
        }

        return userMapper.selectList(baseWrapper).stream()
                .map(this::toActiveUserMap)
                .toList();
    }

    /**
     * 活跃用户的精简视图，供前端筛选框 / 成员选择器使用。
     * <p>
     * 带 avatar 是为了让选择器能直接渲染头像缩略图；realName 为空时回退 username，
     * 避免前端出现空标签。
     */
    private Map<String, Object> toActiveUserMap(User user) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", user.getId());
        m.put("username", user.getUsername());
        m.put("realName", user.getRealName());
        m.put("avatar", user.getAvatar());
        // 所属组织：人员字段「指定部门」范围过滤需要（org 树节点即部门）
        m.put("orgId", user.getOrgId());
        return m;
    }

    /**
     * 确定有效的 orgId 过滤范围。
     * <p>
     * - 超管：使用请求中指定的 orgId（可为 null 表示查全部）
     * - 非超管：如果请求指定了 orgId，校验是否在自己组织范围内；
     *           如果未指定，自动使用当前用户的 orgId
     * - 非超管且无组织归属：返回 null（调用方会限制为只能看到自己）
     */
    private Long resolveEffectiveOrgId(Long requestedOrgId) {
        if (SecurityUtils.isSuperAdmin()) {
            return requestedOrgId;
        }

        // 获取当前用户的 orgId
        Long currentUserOrgId = getCurrentUserOrgId();

        if (requestedOrgId == null) {
            // 非超管未指定 orgId，自动使用自己的 orgId
            return currentUserOrgId;
        }

        // 非超管指定了 orgId，校验是否在自己组织范围内
        if (currentUserOrgId == null) {
            // 当前用户无组织归属，不允许查看其他组织
            return null;
        }

        // 检查请求的 orgId 是否是当前用户组织或其子孙
        List<Long> allowedOrgIds = sysOrgService.getDescendantIds(currentUserOrgId);
        allowedOrgIds.add(currentUserOrgId);
        if (allowedOrgIds.contains(requestedOrgId)) {
            return requestedOrgId;
        }

        // 请求的 orgId 不在自己范围内，回退到自己的 orgId
        return currentUserOrgId;
    }

    /**
     * 获取当前登录用户的 orgId
     */
    private Long getCurrentUserOrgId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return null;
        User user = userMapper.selectById(userId);
        return user != null ? user.getOrgId() : null;
    }

    /** 收集所有 SUPER_ADMIN 角色关联的用户ID */
    private Set<Long> collectSuperAdminUserIds() {
        try {
            List<Role> superRoles = roleMapper.selectList(
                    new LambdaQueryWrapper<Role>().eq(Role::getCode, "SUPER_ADMIN"));
            if (superRoles == null || superRoles.isEmpty()) {
                return Set.of();
            }
            List<Long> roleIds = superRoles.stream()
                    .map(Role::getId)
                    .filter(Objects::nonNull)
                    .toList();
            if (roleIds.isEmpty()) {
                return Set.of();
            }
            List<UserRole> userRoles = userRoleMapper.selectList(
                    new LambdaQueryWrapper<UserRole>().in(UserRole::getRoleId, roleIds));
            if (userRoles == null) {
                return Set.of();
            }
            return userRoles.stream()
                    .map(UserRole::getUserId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            return Set.of();
        }
    }

    /**
     * 从给定的超管用户ID集合中筛选出真正"无组织归属"的用户ID。
     * <p>
     * 列表按组织筛选时，本应只展示该组织下的用户；但为避免未分配组织的超管
     * 在任何组织视图都看不到自己，特此将其兜底带出。已绑定组织的超管
     * （orgId/regionId/departmentId 任一非空）不参与兜底，避免其泄漏到
     * 无关组织列表中。
     */
    private Set<Long> filterUnassignedSuperAdminIds(Set<Long> superAdminIds) {
        if (superAdminIds == null || superAdminIds.isEmpty()) {
            return Set.of();
        }
        List<User> bound = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .in(User::getId, superAdminIds)
                        .nested(w -> w.isNotNull(User::getOrgId)
                                .or().isNotNull(User::getRegionId)
                                .or().isNotNull(User::getDepartmentId))
                        .select(User::getId));
        Set<Long> boundIds = bound.stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return superAdminIds.stream()
                .filter(id -> !boundIds.contains(id))
                .collect(Collectors.toSet());
    }

    @Override
    public UserVO getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return toVO(user);
    }

    @Override
    public Long create(UserCreateDTO dto) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, dto.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("用户名已存在");
        }

        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new BusinessException("邮箱不能为空");
        }
        if (dto.getPhone() == null || dto.getPhone().length() < 3) {
            throw new BusinessException("手机号不能为空且至少包含3位");
        }

        User user = new User();
        BeanUtil.copyProperties(dto, user);
        // Derive regionId/departmentId from orgId
        if (dto.getOrgId() != null) {
            deriveOrgFields(user, dto.getOrgId());
        }
        String initialPassword = buildInitialPassword(dto.getUsername(), dto.getPhone(), dto.getPassword());
        user.setPassword(passwordEncoder.encode(initialPassword));
        user.setStatus(User.STATUS_ACTIVE);
        user.setJobNumber(generateJobNumber());
        userMapper.insert(user);

        emailService.sendInitialPasswordEmail(user.getEmail(), user.getUsername(), initialPassword);

        return user.getId();
    }

    @Override
    public void update(UserUpdateDTO dto) {
        User user = userMapper.selectById(dto.getId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (dto.getRealName() != null) {
            user.setRealName(dto.getRealName());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
        }
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
        }
        if (dto.getEmployeeType() != null) {
            user.setEmployeeType(dto.getEmployeeType());
        }
        if (dto.getWorkStatus() != null) {
            user.setWorkStatus(dto.getWorkStatus());
        }
        if (dto.getHireDate() != null) {
            user.setHireDate(dto.getHireDate());
        }
        if (dto.getBirthday() != null) {
            user.setBirthday(dto.getBirthday());
        }
        if (dto.getRegionId() != null) {
            user.setRegionId(dto.getRegionId());
        }
        if (dto.getDepartmentId() != null) {
            user.setDepartmentId(dto.getDepartmentId());
        }
        if (dto.getOrgId() != null) {
            user.setOrgId(dto.getOrgId());
            deriveOrgFields(user, dto.getOrgId());
        }
        // 工号缺失时自动生成
        if (user.getJobNumber() == null || user.getJobNumber().isBlank()) {
            user.setJobNumber(generateJobNumber());
        }
        userMapper.updateById(user);
    }

    @Override
    public void delete(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        removeUser(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateStatus(List<Long> ids, String status) {
        List<Long> targets = normalizeBatchIds(ids);
        String normalizedStatus = normalizeStatus(status);

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId != null && targets.contains(currentUserId)) {
            throw new BusinessException("不能修改自己的账号状态，请由其他管理员操作");
        }
        if (User.STATUS_INACTIVE.equals(normalizedStatus) && targets.contains(PRIMARY_ADMIN_ID)) {
            throw new BusinessException("主管理员账号不可停用");
        }

        List<Long> existing = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .in(User::getId, targets)
                        .select(User::getId))
                .stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .toList();
        if (existing.isEmpty()) {
            throw new BusinessException("所选成员已不存在，请刷新列表后重试");
        }

        User patch = new User();
        patch.setStatus(normalizedStatus);
        return userMapper.update(patch, new LambdaQueryWrapper<User>().in(User::getId, existing));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<Long> ids) {
        List<Long> targets = normalizeBatchIds(ids);

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId != null && targets.contains(currentUserId)) {
            throw new BusinessException("不能删除自己的账号");
        }
        if (targets.contains(PRIMARY_ADMIN_ID)) {
            throw new BusinessException("主管理员账号不可删除");
        }

        int affected = 0;
        for (Long id : targets) {
            if (userMapper.selectById(id) == null) {
                continue;
            }
            removeUser(id);
            affected++;
        }
        if (affected == 0) {
            throw new BusinessException("所选成员已不存在，请刷新列表后重试");
        }
        return affected;
    }

    /**
     * 删除用户并清理其角色关系。
     * <p>
     * 角色关系一定要一起删：users 是物理删除，残留的 user_roles 行会在
     * 「按角色统计成员」等场景里被算进去，出现"幽灵成员"。
     */
    private void removeUser(Long id) {
        userMapper.deleteById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
    }

    private List<Long> normalizeBatchIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请先勾选要操作的成员");
        }
        List<Long> targets = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (targets.isEmpty()) {
            throw new BusinessException("请先勾选要操作的成员");
        }
        return targets;
    }

    /**
     * 归一化状态值。
     * <p>
     * 历史前端曾用 'disabled' 表示停用，而 users.status 是
     * ENUM('active','inactive')，直接写 'disabled' 在严格模式下会报
     * 数据截断错误。这里统一收口成 active / inactive，并兼容旧值。
     */
    private String normalizeStatus(String status) {
        if (User.STATUS_ACTIVE.equals(status)) {
            return User.STATUS_ACTIVE;
        }
        if (User.STATUS_INACTIVE.equals(status) || "disabled".equals(status)) {
            return User.STATUS_INACTIVE;
        }
        throw new BusinessException("不支持的账号状态");
    }

    @Override
    public boolean resetInitialPassword(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BusinessException("用户未配置邮箱，无法发送初始密码");
        }
        if (user.getPhone() == null || user.getPhone().length() < 3) {
            throw new BusinessException("用户手机号不足3位，无法生成初始密码");
        }

        String initialPassword = buildInitialPassword(user.getUsername(), user.getPhone(), null);
        user.setPassword(passwordEncoder.encode(initialPassword));
        userMapper.updateById(user);
        return emailService.sendInitialPasswordEmail(user.getEmail(), user.getUsername(), initialPassword);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        List<Long> normalizedRoleIds = roleIds == null
                ? List.of()
                : roleIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // 超级管理员角色保护：仅内建主管理员（id=1）可持有。
        // 其他用户请求携带时直接剔除；编辑 admin 时即使前端漏传也保留原有超管角色。
        Long superAdminRoleId = roleMapper.selectList(new LambdaQueryWrapper<Role>()
                        .eq(Role::getCode, "SUPER_ADMIN")
                        .select(Role::getId))
                .stream()
                .map(Role::getId)
                .findFirst()
                .orElse(null);
        if (superAdminRoleId != null) {
            if (Long.valueOf(PRIMARY_ADMIN_ID).equals(userId)) {
                if (!normalizedRoleIds.contains(superAdminRoleId)) {
                    List<Long> merged = new ArrayList<>(normalizedRoleIds);
                    merged.add(superAdminRoleId);
                    normalizedRoleIds = merged;
                }
            } else {
                normalizedRoleIds = normalizedRoleIds.stream()
                        .filter(id -> !superAdminRoleId.equals(id))
                        .toList();
            }
        }

        if (!normalizedRoleIds.isEmpty()) {
            List<Role> roles = roleMapper.selectBatchIds(normalizedRoleIds);
            if (roles.size() != normalizedRoleIds.size()) {
                throw new BusinessException("存在无效的角色");
            }
        }

        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId));

        if (normalizedRoleIds.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (Long roleId : normalizedRoleIds) {
            UserRole relation = new UserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            relation.setCreatedAt(now);
            userRoleMapper.insert(relation);
        }
    }

    @Override
    public List<Long> getUserRoleIds(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .orderByAsc(UserRole::getId))
                .stream()
                .map(UserRole::getRoleId)
                .filter(Objects::nonNull)
                .toList();
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setJobNumber(user.getJobNumber());
        vo.setStatus(user.getStatus());
        vo.setEmployeeType(user.getEmployeeType());
        vo.setWorkStatus(user.getWorkStatus());
        vo.setHireDate(user.getHireDate());
        vo.setBirthday(user.getBirthday());
        vo.setRegionId(user.getRegionId());
        vo.setDepartmentId(user.getDepartmentId());
        vo.setOrgId(user.getOrgId());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());

        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, user.getId()))
                .stream()
                .map(UserRole::getRoleId)
                .filter(Objects::nonNull)
                .toList();
        if (!roleIds.isEmpty()) {
            List<Role> userRoles = roleMapper.selectBatchIds(roleIds);
            String roleNames = userRoles.stream()
                    .map(Role::getName)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining(", "));
            vo.setSystemRole(roleNames.isEmpty() ? null : roleNames);
        }

        // Prefer orgId for display, fallback to regionId/departmentId
        Long displayOrgId = user.getOrgId() != null ? user.getOrgId() : user.getDepartmentId();
        if (displayOrgId == null) {
            displayOrgId = user.getRegionId();
        }

        if (displayOrgId != null) {
            SysOrgVO org = sysOrgService.getDetail(displayOrgId);
            if (org != null && org.getPath() != null) {
                String chainPath = buildOrgPath(org.getPath());
                vo.setRegionPath(chainPath);
            }
        }

        // Fill region name from regionId
        if (user.getRegionId() != null) {
            SysOrgVO region = sysOrgService.getDetail(user.getRegionId());
            if (region != null) {
                vo.setRegionName(region.getName());
            }
        }

        // Fill department name from departmentId
        if (user.getDepartmentId() != null) {
            SysOrgVO dept = sysOrgService.getDetail(user.getDepartmentId());
            if (dept != null) {
                vo.setDepartmentName(dept.getName());
            }
        }

        return vo;
    }

    /**
     * Derive regionId and departmentId from the org the user belongs to.
     * region/company/bureau -> regionId, department/group -> departmentId
     * Walks up the tree to fill both fields.
     */
    private void deriveOrgFields(User user, Long orgId) {
        user.setOrgId(orgId);
        SysOrgVO org = sysOrgService.getDetail(orgId);
        if (org == null) return;

        String orgType = org.getOrgType();
        if ("region".equals(orgType) || "company".equals(orgType) || "bureau".equals(orgType)) {
            user.setRegionId(orgId);
        } else if ("department".equals(orgType) || "group".equals(orgType)) {
            user.setDepartmentId(orgId);
            // Walk up to find regionId
            if (org.getPath() != null) {
                String[] ids = org.getPath().split("/");
                for (String idStr : ids) {
                    if (idStr.isBlank()) continue;
                    SysOrgVO ancestor = sysOrgService.getDetail(Long.parseLong(idStr));
                    if (ancestor != null && ("region".equals(ancestor.getOrgType())
                            || "company".equals(ancestor.getOrgType())
                            || "bureau".equals(ancestor.getOrgType()))) {
                        user.setRegionId(ancestor.getId());
                        break;
                    }
                }
            }
        }
    }

    /**
     * 根据物化路径构建显示路径，如 "/1/5/8/" -> "东莞市 > 开普云科技 > 研发中心"
     */
    private String buildOrgPath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String[] ids = path.split("/");
        List<String> names = new ArrayList<>();
        for (String idStr : ids) {
            if (idStr.isBlank()) {
                continue;
            }
            SysOrgVO org = sysOrgService.getDetail(Long.parseLong(idStr));
            if (org != null) {
                names.add(org.getName());
            }
        }
        return String.join(" > ", names);
    }

    private String buildInitialPassword(String username, String phone, String fallbackPassword) {
        if (fallbackPassword != null && !fallbackPassword.isBlank()) {
            return fallbackPassword;
        }
        if (phone == null || phone.length() < 3) {
            throw new BusinessException("手机号不足3位，无法生成初始密码");
        }
        return username + phone.substring(phone.length() - 3);
    }

    /**
     * 自动生成工号：A001~A999, B001~B999, ... Z001~Z999, AA001~AA999 ...
     */
    private String generateJobNumber() {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(User::getJobNumber)
                .orderByDesc(User::getJobNumber)
                .last("LIMIT 1");
        User maxUser = userMapper.selectOne(wrapper);
        if (maxUser == null || maxUser.getJobNumber() == null) {
            return "A001";
        }
        return incrementJobNumber(maxUser.getJobNumber());
    }

    private String incrementJobNumber(String current) {
        // Split letter prefix and number suffix: "A001" -> prefix="A", num=1
        int splitIdx = 0;
        while (splitIdx < current.length() && !Character.isDigit(current.charAt(splitIdx))) {
            splitIdx++;
        }
        if (splitIdx == 0 || splitIdx == current.length()) {
            return "A001";
        }
        String prefix = current.substring(0, splitIdx);
        int num;
        try {
            num = Integer.parseInt(current.substring(splitIdx));
        } catch (NumberFormatException e) {
            return "A001";
        }

        if (num < 999) {
            return prefix + String.format("%03d", num + 1);
        }
        // num == 999, advance letter: A -> B, ... Z -> AA
        return incrementLetterPrefix(prefix) + "001";
    }

    private String incrementLetterPrefix(String prefix) {
        char[] chars = prefix.toCharArray();
        int i = chars.length - 1;
        while (i >= 0) {
            if (chars[i] < 'Z') {
                chars[i]++;
                return new String(chars);
            }
            chars[i] = 'A';
            i--;
        }
        // All Z's, add one more A: Z -> AA, ZZ -> AAA
        return "A" + new String(chars);
    }
}
