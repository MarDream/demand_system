package com.demand.system.module.hr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.PageResult;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.hr.dto.HrRecordCreateDTO;
import com.demand.system.module.hr.dto.HrRecordQueryDTO;
import com.demand.system.module.hr.dto.HrRecordUpdateDTO;
import com.demand.system.module.hr.dto.HrRecordVO;
import com.demand.system.module.hr.entity.HrEmployeeRecord;
import com.demand.system.module.hr.mapper.HrEmployeeRecordMapper;
import com.demand.system.module.hr.service.HrRecordService;
import com.demand.system.module.organization.dto.SysOrgVO;
import com.demand.system.module.organization.service.SysOrgService;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HrRecordServiceImpl implements HrRecordService {

    private static final Set<String> VALID_TYPES = Set.of(
            HrEmployeeRecord.TYPE_ONBOARDING, HrEmployeeRecord.TYPE_NEWCOMER,
            HrEmployeeRecord.TYPE_REGULARIZATION, HrEmployeeRecord.TYPE_TRANSFER,
            HrEmployeeRecord.TYPE_RESIGNATION, HrEmployeeRecord.TYPE_CONTRACT,
            HrEmployeeRecord.TYPE_RETIREMENT, HrEmployeeRecord.TYPE_CARE,
            HrEmployeeRecord.TYPE_SAFETY);

    private static final Set<String> VALID_STATUSES = Set.of(
            HrEmployeeRecord.STATUS_PROCESSING, HrEmployeeRecord.STATUS_DONE,
            HrEmployeeRecord.STATUS_CANCELLED);

    private final HrEmployeeRecordMapper recordMapper;
    private final UserMapper userMapper;
    private final SysOrgService sysOrgService;

    public HrRecordServiceImpl(HrEmployeeRecordMapper recordMapper, UserMapper userMapper, SysOrgService sysOrgService) {
        this.recordMapper = recordMapper;
        this.userMapper = userMapper;
        this.sysOrgService = sysOrgService;
    }

    @Override
    public PageResult<HrRecordVO> page(HrRecordQueryDTO query) {
        Page<HrEmployeeRecord> page = new Page<>(
                query.getPageNum() == null ? 1 : query.getPageNum(),
                query.getPageSize() == null ? 20 : query.getPageSize());

        LambdaQueryWrapper<HrEmployeeRecord> wrapper = new LambdaQueryWrapper<HrEmployeeRecord>()
                .eq(query.getRecordType() != null && !query.getRecordType().isBlank(),
                        HrEmployeeRecord::getRecordType, query.getRecordType())
                .eq(query.getStatus() != null && !query.getStatus().isBlank(),
                        HrEmployeeRecord::getStatus, query.getStatus())
                .eq(query.getUserId() != null, HrEmployeeRecord::getUserId, query.getUserId())
                .orderByDesc(HrEmployeeRecord::getCreatedAt);

        Page<HrEmployeeRecord> result = recordMapper.selectPage(page, wrapper);
        List<HrRecordVO> voList = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(voList, result.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrRecordVO create(HrRecordCreateDTO dto) {
        validateType(dto.getRecordType());
        User user = requireUser(dto.getUserId());
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            validateStatus(dto.getStatus());
        }

        HrEmployeeRecord record = new HrEmployeeRecord();
        record.setRecordType(dto.getRecordType());
        record.setUserId(dto.getUserId());
        record.setTitle(dto.getTitle());
        record.setDetail(dto.getDetail());
        record.setRecordDate(dto.getRecordDate());
        record.setStatus(dto.getStatus() == null || dto.getStatus().isBlank()
                ? HrEmployeeRecord.STATUS_PROCESSING : dto.getStatus());
        record.setOperatorId(SecurityUtils.getCurrentUserId());
        recordMapper.insert(record);

        // 已完成状态创建时同样触发员工状态联动（例如直接补录一条已完成的历史合同/转正）
        if (HrEmployeeRecord.STATUS_DONE.equals(record.getStatus())) {
            applyUserEffect(record, user);
        }
        return toVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrRecordVO update(Long id, HrRecordUpdateDTO dto) {
        HrEmployeeRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("人事记录不存在");
        }
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            validateStatus(dto.getStatus());
        }

        boolean becameDone = dto.getStatus() != null && HrEmployeeRecord.STATUS_DONE.equals(dto.getStatus())
                && !HrEmployeeRecord.STATUS_DONE.equals(record.getStatus());

        if (dto.getTitle() != null) {
            record.setTitle(dto.getTitle());
        }
        if (dto.getDetail() != null) {
            record.setDetail(dto.getDetail());
        }
        if (dto.getRecordDate() != null) {
            record.setRecordDate(dto.getRecordDate());
        }
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            record.setStatus(dto.getStatus());
        }
        recordMapper.updateById(record);

        // 办理完成 → 按事件类型联动员工档案（入职/转正/异动/离职/退休）
        if (becameDone) {
            applyUserEffect(record, requireUser(record.getUserId()));
        }
        return toVO(record);
    }

    @Override
    public void delete(Long id) {
        if (recordMapper.selectById(id) == null) {
            throw new BusinessException("人事记录不存在");
        }
        recordMapper.deleteById(id);
    }

    @Override
    public Map<String, Object> summary(String recordType) {
        List<HrEmployeeRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<HrEmployeeRecord>()
                        .eq(recordType != null && !recordType.isBlank(),
                                HrEmployeeRecord::getRecordType, recordType)
                        .select(HrEmployeeRecord::getId, HrEmployeeRecord::getStatus,
                                HrEmployeeRecord::getRecordDate));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", records.size());
        summary.put("processing", records.stream().filter(r -> HrEmployeeRecord.STATUS_PROCESSING.equals(r.getStatus())).count());
        summary.put("done", records.stream().filter(r -> HrEmployeeRecord.STATUS_DONE.equals(r.getStatus())).count());
        summary.put("cancelled", records.stream().filter(r -> HrEmployeeRecord.STATUS_CANCELLED.equals(r.getStatus())).count());

        // 合同管理扩展：30 天内到期 / 已到期未续签
        if (recordType == null || HrEmployeeRecord.TYPE_CONTRACT.equals(recordType)) {
            LocalDate today = LocalDate.now();
            summary.put("expiringSoon", records.stream()
                    .filter(r -> HrEmployeeRecord.STATUS_PROCESSING.equals(r.getStatus()))
                    .filter(r -> r.getRecordDate() != null && !r.getRecordDate().isBefore(today)
                            && r.getRecordDate().isBefore(today.plusDays(30)))
                    .count());
            summary.put("expired", records.stream()
                    .filter(r -> HrEmployeeRecord.STATUS_PROCESSING.equals(r.getStatus()))
                    .filter(r -> r.getRecordDate() != null && r.getRecordDate().isBefore(today))
                    .count());
        }
        return summary;
    }

    @Override
    public Map<String, Object> overview() {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().select(
                User::getId, User::getRealName, User::getStatus, User::getWorkStatus,
                User::getPhone, User::getBirthday));

        long probation = 0;
        long pendingResign = 0;
        long inactive = 0;
        long missingPhone = 0;
        List<Map<String, Object>> birthdayUsers = new ArrayList<>();
        LocalDate today = LocalDate.now();
        List<Long> activeIds = new ArrayList<>();

        for (User user : users) {
            boolean isActive = User.STATUS_ACTIVE.equals(user.getStatus())
                    && !User.WORK_STATUS_RESIGNED.equals(user.getWorkStatus());
            if (isActive) {
                activeIds.add(user.getId());
                if (User.WORK_STATUS_PROBATION.equals(user.getWorkStatus())) {
                    probation++;
                } else if (User.WORK_STATUS_PENDING_RESIGN.equals(user.getWorkStatus())) {
                    pendingResign++;
                }
                if (user.getPhone() == null || user.getPhone().isBlank()) {
                    missingPhone++;
                }
                if (user.getBirthday() != null && user.getBirthday().getMonthValue() == today.getMonthValue()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", user.getId());
                    item.put("realName", user.getRealName());
                    item.put("birthday", user.getBirthday().toString());
                    item.put("day", user.getBirthday().getDayOfMonth());
                    birthdayUsers.add(item);
                }
            } else if (User.STATUS_INACTIVE.equals(user.getStatus())
                    && !User.WORK_STATUS_RESIGNED.equals(user.getWorkStatus())) {
                inactive++;
            }
        }
        birthdayUsers.sort(Comparator.comparingInt(m -> (int) m.get("day")));

        // 未签合同：在职员工中没有任何生效/已完成合同登记的
        List<Long> contractUserIds = recordMapper.selectList(new LambdaQueryWrapper<HrEmployeeRecord>()
                        .eq(HrEmployeeRecord::getRecordType, HrEmployeeRecord.TYPE_CONTRACT)
                        .in(HrEmployeeRecord::getStatus, HrEmployeeRecord.STATUS_PROCESSING, HrEmployeeRecord.STATUS_DONE)
                        .select(HrEmployeeRecord::getUserId))
                .stream().map(HrEmployeeRecord::getUserId).distinct().collect(Collectors.toList());
        long noContract = activeIds.stream().filter(id -> !contractUserIds.contains(id)).count();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("probation", probation);
        overview.put("pendingResign", pendingResign);
        overview.put("inactive", inactive);
        overview.put("missingPhone", missingPhone);
        overview.put("noContract", noContract);
        overview.put("birthdayThisMonth", birthdayUsers.size());
        overview.put("birthdayUsers", birthdayUsers);
        return overview;
    }

    /**
     * 事件办结后的员工档案联动（对齐钉钉：入职→试用期、转正→正式、离职/退休→已离职、异动→换部门）
     */
    private void applyUserEffect(HrEmployeeRecord record, User user) {
        Map<String, Object> detail = record.getDetail() == null ? Map.of() : record.getDetail();
        switch (record.getRecordType()) {
            case HrEmployeeRecord.TYPE_ONBOARDING -> {
                user.setStatus(User.STATUS_ACTIVE);
                user.setWorkStatus(User.WORK_STATUS_PROBATION);
                if (user.getHireDate() == null) {
                    user.setHireDate(record.getRecordDate() != null ? record.getRecordDate() : LocalDate.now());
                }
                userMapper.updateById(user);
            }
            case HrEmployeeRecord.TYPE_REGULARIZATION -> {
                user.setWorkStatus(User.WORK_STATUS_CONFIRMED);
                userMapper.updateById(user);
            }
            case HrEmployeeRecord.TYPE_RESIGNATION, HrEmployeeRecord.TYPE_RETIREMENT -> {
                user.setWorkStatus(User.WORK_STATUS_RESIGNED);
                userMapper.updateById(user);
            }
            case HrEmployeeRecord.TYPE_TRANSFER -> {
                Object target = detail.get("newDepartmentId");
                Long deptId = target == null ? null : Long.valueOf(String.valueOf(target));
                if (deptId != null) {
                    user.setOrgId(deptId);
                    user.setDepartmentId(deptId);
                    userMapper.updateById(user);
                }
            }
            default -> {
                // newcomer/contract/care/safety 不改变员工档案
            }
        }
    }

    private void validateType(String type) {
        if (type == null || !VALID_TYPES.contains(type)) {
            throw new BusinessException("未知的人事事件类型: " + type);
        }
    }

    private void validateStatus(String status) {
        if (!VALID_STATUSES.contains(status)) {
            throw new BusinessException("未知的人事事件状态: " + status);
        }
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("员工不存在");
        }
        return user;
    }

    private HrRecordVO toVO(HrEmployeeRecord record) {
        HrRecordVO vo = new HrRecordVO();
        vo.setId(record.getId());
        vo.setRecordType(record.getRecordType());
        vo.setUserId(record.getUserId());
        vo.setTitle(record.getTitle());
        vo.setDetail(record.getDetail());
        vo.setRecordDate(record.getRecordDate());
        vo.setStatus(record.getStatus());
        vo.setCreatedAt(record.getCreatedAt());
        vo.setUpdatedAt(record.getUpdatedAt());

        User user = userMapper.selectById(record.getUserId());
        if (user != null) {
            vo.setUserName(user.getRealName());
            Long displayOrgId = user.getOrgId() != null ? user.getOrgId() : user.getDepartmentId();
            if (displayOrgId != null) {
                SysOrgVO org = sysOrgService.getDetail(displayOrgId);
                if (org != null) {
                    vo.setDepartmentName(org.getName());
                }
            }
        }
        if (record.getOperatorId() != null) {
            User operator = userMapper.selectById(record.getOperatorId());
            if (operator != null) {
                vo.setOperatorName(operator.getRealName());
            }
        }
        return vo;
    }
}
