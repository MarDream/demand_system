package com.demand.system.module.requirement.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 需求详情中归集的"流转上传附件"分组。
 * 同一个流转节点上上传的多个附件归为一组，并附带节点、操作人、操作时间，便于前端按节点统一展示。
 */
public class TransitionAttachmentGroupDTO {

    /** 流转记录 ID。 */
    private Long transitionId;

    /** 流转节点名（从哪个节点提交的）。 */
    private String nodeName;

    /** 操作类型：submit / approve / rollback / cancel / countersign。 */
    private String action;

    /** 操作人姓名。 */
    private String operatorName;

    /** 操作时间。 */
    private LocalDateTime operatedAt;

    /** 该节点上传的附件列表。 */
    private List<RequirementAttachmentDTO> attachments;

    public Long getTransitionId() {
        return transitionId;
    }

    public void setTransitionId(Long transitionId) {
        this.transitionId = transitionId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public LocalDateTime getOperatedAt() {
        return operatedAt;
    }

    public void setOperatedAt(LocalDateTime operatedAt) {
        this.operatedAt = operatedAt;
    }

    public List<RequirementAttachmentDTO> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<RequirementAttachmentDTO> attachments) {
        this.attachments = attachments;
    }
}