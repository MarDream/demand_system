package com.demand.system.module.invitation.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量邀请结果：成功生成的邀请 + 被跳过的行及原因。
 * <p>
 * 逐行反馈是刻意的——批量导入类操作最怕"静默少建几条"，
 * 所以跳过的行必须把原因带回前端展示。
 */
public class BatchInviteResultVO {

    /** 成功创建的邀请数量 */
    private int successCount;

    /** 被跳过的行 */
    private List<Skipped> skipped = new ArrayList<>();

    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }

    public List<Skipped> getSkipped() { return skipped; }
    public void setSkipped(List<Skipped> skipped) { this.skipped = skipped; }

    public void addSkipped(String name, String target, String reason) {
        this.skipped.add(new Skipped(name, target, reason));
    }

    /** 被跳过的一行 */
    public static class Skipped {

        private String name;

        private String target;

        private String reason;

        public Skipped() {
        }

        public Skipped(String name, String target, String reason) {
            this.name = name;
            this.target = target;
            this.reason = reason;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
