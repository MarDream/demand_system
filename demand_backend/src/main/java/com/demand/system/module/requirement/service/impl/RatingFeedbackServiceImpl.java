package com.demand.system.module.requirement.service.impl;

import com.demand.system.module.notification.service.NotificationService;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.entity.RequirementApprovalEvaluation;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.requirement.service.RatingFeedbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 评分反馈实现
 * 评分后立即触发：低分告警（推送通知/站内信）
 */
@Service
public class RatingFeedbackServiceImpl implements RatingFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(RatingFeedbackServiceImpl.class);

    @Autowired
    private RequirementMapper requirementMapper;

    @Autowired
    private NotificationService notificationService;

    /**
     * 低分告警阈值（可配置，默认 3 星）
     */
    @Value("${demand.rating.low-threshold:3}")
    private Integer lowThreshold;

    @Override
    public void onEvaluationCreated(RequirementApprovalEvaluation evaluation) {
        if (evaluation == null) return;
        Integer rating = evaluation.getRating();
        if (rating == null) return;
        if (!isLowRating(rating)) return;

        Requirement requirement = requirementMapper.selectById(evaluation.getRequirementId());
        if (requirement == null) return;

        log.warn("[低分告警] 需求={} 节点={} 评分={} 评价={}",
                requirement.getRequirementNo(), evaluation.getNodeName(), rating, evaluation.getContent());

        // 通知接收人：需求创建人 + 负责人（去重，排除评价人自身）
        Set<Long> recipients = new LinkedHashSet<>();
        if (requirement.getCreatorId() != null) recipients.add(requirement.getCreatorId());
        if (requirement.getAssigneeId() != null) recipients.add(requirement.getAssigneeId());
        recipients.remove(evaluation.getEvaluatorId());

        String title = String.format("低分评分提醒：%s", requirement.getRequirementNo());
        String content = String.format("需求「%s」在节点「%s」被评 %d 星，请关注改进。",
                requirement.getTitle(), evaluation.getNodeName(), rating);

        for (Long userId : recipients) {
            try {
                notificationService.sendNotification(userId, title, content, "LOW_RATING_ALERT", requirement.getId());
            } catch (Exception ex) {
                log.warn("低分告警通知发送失败 userId={} 需求={}: {}", userId, requirement.getRequirementNo(), ex.getMessage());
            }
        }
    }

    @Override
    public boolean isLowRating(Integer rating) {
        return rating != null && rating < lowThreshold;
    }
}
