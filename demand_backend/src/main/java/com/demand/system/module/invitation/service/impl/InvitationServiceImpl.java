package com.demand.system.module.invitation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.PageResult;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.invitation.dto.*;
import com.demand.system.module.invitation.entity.SysInvitation;
import com.demand.system.module.invitation.entity.SysJoinRequest;
import com.demand.system.module.invitation.mapper.SysInvitationMapper;
import com.demand.system.module.invitation.mapper.SysJoinRequestMapper;
import com.demand.system.module.invitation.service.InvitationService;
import com.demand.system.module.organization.dto.SysOrgVO;
import com.demand.system.module.organization.service.SysOrgService;
import com.demand.system.module.rbac.entity.Role;
import com.demand.system.module.rbac.mapper.RoleMapper;
import com.demand.system.module.user.dto.UserCreateDTO;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import com.demand.system.module.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class InvitationServiceImpl implements InvitationService {

    /** 邀请码字符集：去掉了 0/O/1/I 等易混淆字符，方便口头传递 */
    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private static final int CODE_LENGTH = 10;

    /** 邀请链接默认有效期（天） */
    private static final int DEFAULT_EXPIRE_DAYS = 7;

    private static final int MAX_BATCH_SIZE = 200;

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private static final Pattern USERNAME_SAFE_PATTERN = Pattern.compile("[^a-zA-Z0-9_]");

    private final SysInvitationMapper invitationMapper;
    private final SysJoinRequestMapper joinRequestMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final SysOrgService sysOrgService;
    private final UserService userService;
    private final SecureRandom random = new SecureRandom();

    public InvitationServiceImpl(SysInvitationMapper invitationMapper,
                                 SysJoinRequestMapper joinRequestMapper,
                                 UserMapper userMapper,
                                 RoleMapper roleMapper,
                                 SysOrgService sysOrgService,
                                 UserService userService) {
        this.invitationMapper = invitationMapper;
        this.joinRequestMapper = joinRequestMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.sysOrgService = sysOrgService;
        this.userService = userService;
    }

    // ==================== 管理员侧 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvitationVO createLinkInvite(InvitationLinkCreateDTO dto) {
        SysInvitation inv = new SysInvitation();
        inv.setInviteCode(generateUniqueCode());
        inv.setInviteType(SysInvitation.TYPE_LINK);
        inv.setOrgId(dto.getOrgId());
        inv.setRoleIds(joinIds(dto.getRoleIds()));
        inv.setStatus(SysInvitation.STATUS_PENDING);
        inv.setMaxUses(normalizeMaxUses(dto.getMaxUses()));
        inv.setUsedCount(0);
        inv.setExpiresAt(resolveExpiresAt(dto.getExpireDays(), Boolean.TRUE.equals(dto.getNeverExpire())));
        inv.setInvitedBy(SecurityUtils.getCurrentUserId());
        inv.setRemark(dto.getRemark());
        invitationMapper.insert(inv);
        return toInvitationVO(inv);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchInviteResultVO batchInvite(BatchInviteDTO dto) {
        if (dto.getMembers() == null || dto.getMembers().isEmpty()) {
            throw new BusinessException("请至少填写一位被邀请人");
        }
        if (dto.getMembers().size() > MAX_BATCH_SIZE) {
            throw new BusinessException("单次批量邀请最多 " + MAX_BATCH_SIZE + " 人");
        }

        BatchInviteResultVO result = new BatchInviteResultVO();
        LocalDateTime expiresAt = resolveExpiresAt(dto.getExpireDays(), false);
        String roleIds = joinIds(dto.getRoleIds());
        Long currentUserId = SecurityUtils.getCurrentUserId();
        // 同批次内去重，避免一次粘贴里重复的行各建一条邀请
        Set<String> seenInBatch = new HashSet<>();

        for (BatchInviteDTO.Member member : dto.getMembers()) {
            String name = trimToNull(member.getName());
            String phone = trimToNull(member.getPhone());
            String email = trimToNull(member.getEmail());
            String target = phone != null ? phone : email;

            if (name == null) {
                result.addSkipped(target, target, "姓名为空");
                continue;
            }
            if (target == null) {
                result.addSkipped(name, null, "手机号与邮箱至少填写一个");
                continue;
            }
            if (phone != null && !PHONE_PATTERN.matcher(phone).matches()) {
                result.addSkipped(name, target, "手机号格式不正确");
                continue;
            }
            if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
                result.addSkipped(name, target, "邮箱格式不正确");
                continue;
            }
            if (!seenInBatch.add(target.toLowerCase(Locale.ROOT))) {
                result.addSkipped(name, target, "与本批次中其他行重复");
                continue;
            }
            if (existsUserByPhoneOrEmail(phone, email)) {
                result.addSkipped(name, target, "该手机号或邮箱已是系统成员");
                continue;
            }
            if (existsPendingInvitation(phone, email)) {
                result.addSkipped(name, target, "已存在待接受的邀请");
                continue;
            }

            SysInvitation inv = new SysInvitation();
            inv.setInviteCode(generateUniqueCode());
            inv.setInviteType(SysInvitation.TYPE_BATCH);
            inv.setTarget(target);
            inv.setTargetName(name);
            inv.setOrgId(dto.getOrgId());
            inv.setRoleIds(roleIds);
            inv.setStatus(SysInvitation.STATUS_PENDING);
            // 批量邀请是一人一码，用过即失效
            inv.setMaxUses(1);
            inv.setUsedCount(0);
            inv.setExpiresAt(expiresAt);
            inv.setInvitedBy(currentUserId);
            inv.setRemark(dto.getRemark());
            invitationMapper.insert(inv);
            result.setSuccessCount(result.getSuccessCount() + 1);
        }
        return result;
    }

    @Override
    public PageResult<InvitationVO> listInvitations(InvitationQueryDTO query) {
        Page<SysInvitation> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysInvitation> wrapper = new LambdaQueryWrapper<>();

        if (isNotBlank(query.getInviteType())) {
            wrapper.eq(SysInvitation::getInviteType, query.getInviteType().trim());
        }
        applyInvitationStatusFilter(wrapper, query.getStatus());
        if (isNotBlank(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(SysInvitation::getTargetName, kw)
                    .or().like(SysInvitation::getTarget, kw)
                    .or().like(SysInvitation::getInviteCode, kw));
        }
        wrapper.orderByDesc(SysInvitation::getCreatedAt).orderByDesc(SysInvitation::getId);

        Page<SysInvitation> result = invitationMapper.selectPage(page, wrapper);
        List<InvitationVO> list = result.getRecords().stream()
                .map(this::toInvitationVO)
                .collect(Collectors.toList());
        return new PageResult<>(list, result.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvitationVO revokeInvitation(Long id) {
        SysInvitation inv = requireInvitation(id);
        if (!SysInvitation.STATUS_PENDING.equals(inv.getStatus())) {
            throw new BusinessException("该邀请已结束，无法撤回");
        }
        inv.setStatus(SysInvitation.STATUS_REVOKED);
        invitationMapper.updateById(inv);
        return toInvitationVO(inv);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvitationVO resendInvitation(Long id) {
        SysInvitation inv = requireInvitation(id);
        if (SysInvitation.STATUS_ACCEPTED.equals(inv.getStatus())) {
            throw new BusinessException("该邀请已被接受，无法重发");
        }
        // 重发 = 换一张新的票：旧码立即失效，次数与有效期重置
        inv.setInviteCode(generateUniqueCode());
        inv.setStatus(SysInvitation.STATUS_PENDING);
        inv.setUsedCount(0);
        inv.setExpiresAt(resolveExpiresAt(null, false));
        invitationMapper.updateById(inv);
        return toInvitationVO(inv);
    }

    @Override
    public PageResult<JoinRequestVO> listJoinRequests(JoinRequestQueryDTO query) {
        Page<SysJoinRequest> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysJoinRequest> wrapper = new LambdaQueryWrapper<>();

        if (isNotBlank(query.getStatus())) {
            wrapper.eq(SysJoinRequest::getStatus, query.getStatus().trim());
        }
        if (isNotBlank(query.getSource())) {
            wrapper.eq(SysJoinRequest::getSource, query.getSource().trim());
        }
        if (isNotBlank(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(SysJoinRequest::getApplicantName, kw)
                    .or().like(SysJoinRequest::getApplicantPhone, kw)
                    .or().like(SysJoinRequest::getApplicantEmail, kw));
        }
        wrapper.orderByDesc(SysJoinRequest::getCreatedAt).orderByDesc(SysJoinRequest::getId);

        Page<SysJoinRequest> result = joinRequestMapper.selectPage(page, wrapper);
        List<JoinRequestVO> list = result.getRecords().stream()
                .map(this::toJoinRequestVO)
                .collect(Collectors.toList());
        return new PageResult<>(list, result.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveJoinRequest(Long id, ReviewJoinRequestDTO dto) {
        SysJoinRequest request = requireJoinRequest(id);
        if (!SysJoinRequest.STATUS_PENDING.equals(request.getStatus())) {
            throw new BusinessException("该申请已处理，无法重复审批");
        }

        Long orgId = dto != null && dto.getOrgId() != null ? dto.getOrgId() : request.getOrgId();
        if (orgId == null) {
            throw new BusinessException("请先指定申请人所属组织");
        }
        List<Long> roleIds = dto != null && dto.getRoleIds() != null
                ? dto.getRoleIds()
                : parseIds(request.getRoleIds());

        if (existsUserByPhoneOrEmail(request.getApplicantPhone(), request.getApplicantEmail())) {
            throw new BusinessException("该手机号或邮箱已是系统成员，无需重复添加");
        }

        UserCreateDTO create = new UserCreateDTO();
        create.setRealName(request.getApplicantName());
        create.setPhone(request.getApplicantPhone());
        create.setEmail(request.getApplicantEmail());
        create.setOrgId(orgId);
        create.setUsername(generateUsername(request.getApplicantName(),
                request.getApplicantEmail(), request.getApplicantPhone()));
        Long userId = userService.create(create);

        if (!roleIds.isEmpty()) {
            userService.assignRoles(userId, roleIds);
        }

        request.setStatus(SysJoinRequest.STATUS_APPROVED);
        request.setUserId(userId);
        request.setOrgId(orgId);
        request.setRoleIds(joinIds(roleIds));
        request.setReviewedBy(SecurityUtils.getCurrentUserId());
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewRemark(dto != null ? dto.getReviewRemark() : null);
        joinRequestMapper.updateById(request);

        // 同步回写来源邀请，让「邀请记录」页能看到已接受
        if (request.getInvitationId() != null) {
            SysInvitation inv = invitationMapper.selectById(request.getInvitationId());
            if (inv != null) {
                inv.setStatus(SysInvitation.STATUS_ACCEPTED);
                inv.setAcceptedBy(userId);
                inv.setAcceptedAt(LocalDateTime.now());
                invitationMapper.updateById(inv);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectJoinRequest(Long id, ReviewJoinRequestDTO dto) {
        SysJoinRequest request = requireJoinRequest(id);
        if (!SysJoinRequest.STATUS_PENDING.equals(request.getStatus())) {
            throw new BusinessException("该申请已处理，无法重复审批");
        }
        request.setStatus(SysJoinRequest.STATUS_REJECTED);
        request.setReviewedBy(SecurityUtils.getCurrentUserId());
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewRemark(dto != null ? dto.getReviewRemark() : null);
        joinRequestMapper.updateById(request);
    }

    // ==================== 匿名侧 ====================

    @Override
    public InviteInfoVO getInviteInfo(String code) {
        SysInvitation inv = findByCode(code);
        InviteInfoVO vo = new InviteInfoVO();
        vo.setInviteCode(inv.getInviteCode());
        vo.setInviteType(inv.getInviteType());
        vo.setTargetName(inv.getTargetName());
        vo.setExpiresAt(inv.getExpiresAt());
        vo.setInviterName(userNameOf(inv.getInvitedBy()));
        vo.setOrgName(orgNameOf(inv.getOrgId()));
        String invalidReason = validateUsable(inv);
        vo.setValid(invalidReason == null);
        vo.setInvalidReason(invalidReason);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitInviteApplication(String code, InviteApplyDTO dto) {
        SysInvitation inv = findByCode(code);
        String invalidReason = validateUsable(inv);
        if (invalidReason != null) {
            throw new BusinessException(invalidReason);
        }

        String phone = dto.getPhone().trim();
        String email = dto.getEmail().trim();

        if (existsUserByPhoneOrEmail(phone, email)) {
            throw new BusinessException("该手机号或邮箱已是系统成员，请直接登录");
        }
        Long duplicated = joinRequestMapper.selectCount(new LambdaQueryWrapper<SysJoinRequest>()
                .eq(SysJoinRequest::getStatus, SysJoinRequest.STATUS_PENDING)
                .eq(SysJoinRequest::getApplicantPhone, phone));
        if (duplicated != null && duplicated > 0) {
            throw new BusinessException("你已提交过申请，请耐心等待管理员审批");
        }

        SysJoinRequest request = new SysJoinRequest();
        request.setInvitationId(inv.getId());
        request.setApplicantName(dto.getName().trim());
        request.setApplicantPhone(phone);
        request.setApplicantEmail(email);
        request.setOrgId(inv.getOrgId());
        request.setRoleIds(inv.getRoleIds());
        request.setSource(SysInvitation.TYPE_BATCH.equals(inv.getInviteType())
                ? SysJoinRequest.SOURCE_BATCH
                : SysJoinRequest.SOURCE_LINK);
        request.setStatus(SysJoinRequest.STATUS_PENDING);
        request.setApplyRemark(dto.getRemark());
        joinRequestMapper.insert(request);

        inv.setUsedCount((inv.getUsedCount() == null ? 0 : inv.getUsedCount()) + 1);
        invitationMapper.updateById(inv);
    }

    // ==================== 内部工具 ====================

    private void applyInvitationStatusFilter(LambdaQueryWrapper<SysInvitation> wrapper, String status) {
        if (!isNotBlank(status)) {
            return;
        }
        String value = status.trim();
        LocalDateTime now = LocalDateTime.now();
        if (SysInvitation.STATUS_EXPIRED.equals(value)) {
            // 「已过期」不是落库状态，而是 pending + 过期时间已到
            wrapper.eq(SysInvitation::getStatus, SysInvitation.STATUS_PENDING)
                    .isNotNull(SysInvitation::getExpiresAt)
                    .lt(SysInvitation::getExpiresAt, now);
        } else if (SysInvitation.STATUS_PENDING.equals(value)) {
            // 待接受里要排除掉已经过期但没刷状态的
            wrapper.eq(SysInvitation::getStatus, SysInvitation.STATUS_PENDING)
                    .and(w -> w.isNull(SysInvitation::getExpiresAt)
                            .or().ge(SysInvitation::getExpiresAt, now));
        } else {
            wrapper.eq(SysInvitation::getStatus, value);
        }
    }

    /**
     * 邀请是否还可用。返回 null 表示可用，否则返回可直接展示给用户的原因。
     */
    private String validateUsable(SysInvitation inv) {
        if (SysInvitation.STATUS_REVOKED.equals(inv.getStatus())) {
            return "该邀请链接已被撤回";
        }
        if (inv.isExpired()) {
            return "该邀请链接已过期，请联系管理员重新发送";
        }
        int maxUses = inv.getMaxUses() == null ? 0 : inv.getMaxUses();
        int usedCount = inv.getUsedCount() == null ? 0 : inv.getUsedCount();
        if (maxUses > 0 && usedCount >= maxUses) {
            return "该邀请链接已被使用";
        }
        return null;
    }

    private SysInvitation requireInvitation(Long id) {
        SysInvitation inv = id == null ? null : invitationMapper.selectById(id);
        if (inv == null) {
            throw new BusinessException("邀请记录不存在");
        }
        return inv;
    }

    private SysJoinRequest requireJoinRequest(Long id) {
        SysJoinRequest request = id == null ? null : joinRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException("申请记录不存在");
        }
        return request;
    }

    private SysInvitation findByCode(String code) {
        if (!isNotBlank(code)) {
            throw new BusinessException("邀请链接无效");
        }
        SysInvitation inv = invitationMapper.selectOne(new LambdaQueryWrapper<SysInvitation>()
                .eq(SysInvitation::getInviteCode, code.trim()));
        if (inv == null) {
            throw new BusinessException("邀请链接无效或已被删除");
        }
        return inv;
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS[random.nextInt(CODE_CHARS.length)]);
            }
            String code = sb.toString();
            Long exists = invitationMapper.selectCount(new LambdaQueryWrapper<SysInvitation>()
                    .eq(SysInvitation::getInviteCode, code));
            if (exists == null || exists == 0) {
                return code;
            }
        }
        throw new BusinessException("邀请码生成失败，请重试");
    }

    /**
     * 审批通过时自动生成账号名：
     * 优先取邮箱前缀，退化到「u + 手机号后 8 位」，最后按需追加序号保证唯一。
     */
    private String generateUsername(String realName, String email, String phone) {
        String base = "";
        if (isNotBlank(email) && email.contains("@")) {
            base = USERNAME_SAFE_PATTERN.matcher(email.substring(0, email.indexOf('@'))).replaceAll("");
        }
        if (base.length() < 3 && isNotBlank(phone)) {
            String digits = phone.replaceAll("\\D", "");
            base = "u" + (digits.length() > 8 ? digits.substring(digits.length() - 8) : digits);
        }
        if (base.length() < 3 && isNotBlank(realName)) {
            base = "u" + USERNAME_SAFE_PATTERN.matcher(realName).replaceAll("");
        }
        base = base.toLowerCase(Locale.ROOT);
        if (base.length() < 3) {
            base = "user" + (System.currentTimeMillis() % 10000);
        }
        if (base.length() > 20) {
            base = base.substring(0, 20);
        }

        String candidate = base;
        for (int suffix = 1; suffix < 10000; suffix++) {
            Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, candidate));
            if (exists == null || exists == 0) {
                return candidate;
            }
            String suffixText = String.valueOf(suffix);
            candidate = base.length() + suffixText.length() > 20
                    ? base.substring(0, 20 - suffixText.length()) + suffixText
                    : base + suffixText;
        }
        throw new BusinessException("无法生成唯一账号名，请手动添加该成员");
    }

    private boolean existsUserByPhoneOrEmail(String phone, String email) {
        if (!isNotBlank(phone) && !isNotBlank(email)) {
            return false;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (isNotBlank(phone) && isNotBlank(email)) {
            wrapper.and(w -> w.eq(User::getPhone, phone).or().eq(User::getEmail, email));
        } else if (isNotBlank(phone)) {
            wrapper.eq(User::getPhone, phone);
        } else {
            wrapper.eq(User::getEmail, email);
        }
        Long count = userMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    private boolean existsPendingInvitation(String phone, String email) {
        if (!isNotBlank(phone) && !isNotBlank(email)) {
            return false;
        }
        LambdaQueryWrapper<SysInvitation> wrapper = new LambdaQueryWrapper<SysInvitation>()
                .eq(SysInvitation::getStatus, SysInvitation.STATUS_PENDING);
        if (isNotBlank(phone) && isNotBlank(email)) {
            wrapper.and(w -> w.eq(SysInvitation::getTarget, phone).or().eq(SysInvitation::getTarget, email));
        } else if (isNotBlank(phone)) {
            wrapper.eq(SysInvitation::getTarget, phone);
        } else {
            wrapper.eq(SysInvitation::getTarget, email);
        }
        Long count = invitationMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    private LocalDateTime resolveExpiresAt(Integer expireDays, boolean neverExpire) {
        if (neverExpire) {
            return null;
        }
        int days = expireDays == null || expireDays <= 0 ? DEFAULT_EXPIRE_DAYS : expireDays;
        return LocalDateTime.now().plusDays(days);
    }

    private int normalizeMaxUses(Integer maxUses) {
        if (maxUses == null || maxUses < 0) {
            return 0;
        }
        return maxUses;
    }

    private InvitationVO toInvitationVO(SysInvitation inv) {
        InvitationVO vo = new InvitationVO();
        vo.setId(inv.getId());
        vo.setInviteCode(inv.getInviteCode());
        vo.setInviteType(inv.getInviteType());
        vo.setInviteTypeText(invitationTypeText(inv.getInviteType()));
        vo.setTarget(inv.getTarget());
        vo.setTargetName(inv.getTargetName());
        vo.setOrgId(inv.getOrgId());
        vo.setOrgName(orgNameOf(inv.getOrgId()));
        List<Long> roleIds = parseIds(inv.getRoleIds());
        vo.setRoleIds(roleIds);
        vo.setRoleNames(roleNamesOf(roleIds));
        String effectiveStatus = effectiveInvitationStatus(inv);
        vo.setStatus(effectiveStatus);
        vo.setStatusText(invitationStatusText(effectiveStatus));
        vo.setMaxUses(inv.getMaxUses());
        vo.setUsedCount(inv.getUsedCount());
        vo.setExpiresAt(inv.getExpiresAt());
        vo.setInvitedBy(inv.getInvitedBy());
        vo.setInviterName(userNameOf(inv.getInvitedBy()));
        vo.setAcceptedBy(inv.getAcceptedBy());
        vo.setAcceptedAt(inv.getAcceptedAt());
        vo.setRemark(inv.getRemark());
        vo.setCreatedAt(inv.getCreatedAt());
        return vo;
    }

    private JoinRequestVO toJoinRequestVO(SysJoinRequest request) {
        JoinRequestVO vo = new JoinRequestVO();
        vo.setId(request.getId());
        vo.setInvitationId(request.getInvitationId());
        vo.setUserId(request.getUserId());
        vo.setApplicantName(request.getApplicantName());
        vo.setApplicantPhone(request.getApplicantPhone());
        vo.setApplicantEmail(request.getApplicantEmail());
        vo.setOrgId(request.getOrgId());
        vo.setOrgName(orgNameOf(request.getOrgId()));
        List<Long> roleIds = parseIds(request.getRoleIds());
        vo.setRoleIds(roleIds);
        vo.setRoleNames(roleNamesOf(roleIds));
        vo.setSource(request.getSource());
        vo.setSourceText(sourceText(request.getSource()));
        vo.setStatus(request.getStatus());
        vo.setStatusText(joinStatusText(request.getStatus()));
        vo.setApplyRemark(request.getApplyRemark());
        vo.setReviewedBy(request.getReviewedBy());
        vo.setReviewerName(userNameOf(request.getReviewedBy()));
        vo.setReviewedAt(request.getReviewedAt());
        vo.setReviewRemark(request.getReviewRemark());
        vo.setCreatedAt(request.getCreatedAt());

        if (request.getInvitationId() != null) {
            SysInvitation inv = invitationMapper.selectById(request.getInvitationId());
            if (inv != null) {
                vo.setInviteCode(inv.getInviteCode());
            }
        }
        if (request.getUserId() != null) {
            User user = userMapper.selectById(request.getUserId());
            if (user != null) {
                vo.setUsername(user.getUsername());
            }
        }
        return vo;
    }

    /** 落库状态是 pending 但时间已过，对外一律呈现为 expired */
    private String effectiveInvitationStatus(SysInvitation inv) {
        if (SysInvitation.STATUS_PENDING.equals(inv.getStatus()) && inv.isExpired()) {
            return SysInvitation.STATUS_EXPIRED;
        }
        return inv.getStatus();
    }

    private String invitationTypeText(String type) {
        if (SysInvitation.TYPE_LINK.equals(type)) {
            return "链接邀请";
        }
        if (SysInvitation.TYPE_BATCH.equals(type)) {
            return "批量邀请";
        }
        return type;
    }

    private String invitationStatusText(String status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case SysInvitation.STATUS_PENDING -> "待接受";
            case SysInvitation.STATUS_ACCEPTED -> "已接受";
            case SysInvitation.STATUS_EXPIRED -> "已过期";
            case SysInvitation.STATUS_REVOKED -> "已撤回";
            default -> status;
        };
    }

    private String sourceText(String source) {
        if (source == null) {
            return "-";
        }
        return switch (source) {
            case SysJoinRequest.SOURCE_LINK -> "邀请链接";
            case SysJoinRequest.SOURCE_BATCH -> "批量邀请";
            case SysJoinRequest.SOURCE_ADMIN -> "管理员添加";
            case SysJoinRequest.SOURCE_SELF -> "自助申请";
            default -> source;
        };
    }

    private String joinStatusText(String status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case SysJoinRequest.STATUS_PENDING -> "待处理";
            case SysJoinRequest.STATUS_APPROVED -> "已通过";
            case SysJoinRequest.STATUS_REJECTED -> "已拒绝";
            default -> status;
        };
    }

    private String orgNameOf(Long orgId) {
        if (orgId == null) {
            return null;
        }
        SysOrgVO org = sysOrgService.getDetail(orgId);
        return org != null ? org.getName() : null;
    }

    private String userNameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return isNotBlank(user.getRealName()) ? user.getRealName() : user.getUsername();
    }

    private List<String> roleNamesOf(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .map(Role::getName)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private List<Long> parseIds(String csv) {
        if (!isNotBlank(csv)) {
            return new ArrayList<>();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // 脏数据直接跳过，不影响其余角色
            }
        }
        return ids;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
