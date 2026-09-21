package com.demand.system.module.user.dto;

/**
 * 花名册统计（参考钉钉花名册统计板：在职/员工类型/用工状态分布）
 */
public class RosterStatsVO {

    /** 在职员工数（active 且未离职） */
    private long active;

    /** 待入职（未激活账号） */
    private long inactive;

    /** 试用期 */
    private long probation;

    /** 已转正 */
    private long confirmed;

    /** 待离职 */
    private long pendingResign;

    /** 已离职 */
    private long resigned;

    /** 全职 */
    private long fullTime;

    /** 兼职 */
    private long partTime;

    /** 实习 */
    private long intern;

    /** 劳务派遣 */
    private long dispatch;

    /** 其他类型 */
    private long other;

    public long getActive() {
        return active;
    }

    public void setActive(long active) {
        this.active = active;
    }

    public long getInactive() {
        return inactive;
    }

    public void setInactive(long inactive) {
        this.inactive = inactive;
    }

    public long getProbation() {
        return probation;
    }

    public void setProbation(long probation) {
        this.probation = probation;
    }

    public long getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(long confirmed) {
        this.confirmed = confirmed;
    }

    public long getPendingResign() {
        return pendingResign;
    }

    public void setPendingResign(long pendingResign) {
        this.pendingResign = pendingResign;
    }

    public long getResigned() {
        return resigned;
    }

    public void setResigned(long resigned) {
        this.resigned = resigned;
    }

    public long getFullTime() {
        return fullTime;
    }

    public void setFullTime(long fullTime) {
        this.fullTime = fullTime;
    }

    public long getPartTime() {
        return partTime;
    }

    public void setPartTime(long partTime) {
        this.partTime = partTime;
    }

    public long getIntern() {
        return intern;
    }

    public void setIntern(long intern) {
        this.intern = intern;
    }

    public long getDispatch() {
        return dispatch;
    }

    public void setDispatch(long dispatch) {
        this.dispatch = dispatch;
    }

    public long getOther() {
        return other;
    }

    public void setOther(long other) {
        this.other = other;
    }
}
