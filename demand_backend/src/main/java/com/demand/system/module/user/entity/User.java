package com.demand.system.module.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@TableName(value = "users", autoResultMap = true)
public class User {

    /** 用户状态：激活 */
    public static final String STATUS_ACTIVE = "active";

    /** 用户状态：未激活 */
    public static final String STATUS_INACTIVE = "inactive";

    /** 员工类型：全职 */
    public static final String EMPLOYEE_TYPE_FULL_TIME = "full_time";
    /** 员工类型：兼职 */
    public static final String EMPLOYEE_TYPE_PART_TIME = "part_time";
    /** 员工类型：实习 */
    public static final String EMPLOYEE_TYPE_INTERN = "intern";
    /** 员工类型：劳务派遣 */
    public static final String EMPLOYEE_TYPE_DISPATCH = "dispatch";
    /** 员工类型：其他 */
    public static final String EMPLOYEE_TYPE_OTHER = "other";

    /** 用工状态：试用期 */
    public static final String WORK_STATUS_PROBATION = "probation";
    /** 用工状态：已转正 */
    public static final String WORK_STATUS_CONFIRMED = "confirmed";
    /** 用工状态：待离职 */
    public static final String WORK_STATUS_PENDING_RESIGN = "pending_resign";
    /** 用工状态：已离职 */
    public static final String WORK_STATUS_RESIGNED = "resigned";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String realName;

    private String email;

    private String phone;

    private String avatar;

    private Long regionId;

    private Long departmentId;

    private Long orgId;

    private String jobNumber;

    private String status;

    /** 员工类型(全职/兼职/实习/劳务派遣/其他) */
    private String employeeType;

    /** 用工状态(试用期/已转正/待离职/已离职) */
    private String workStatus;

    /** 入职日期 */
    private LocalDate hireDate;

    /** 生日 */
    private LocalDate birthday;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public Long getRegionId() {
        return regionId;
    }

    public void setRegionId(Long regionId) {
        this.regionId = regionId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public String getJobNumber() {
        return jobNumber;
    }

    public void setJobNumber(String jobNumber) {
        this.jobNumber = jobNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEmployeeType() {
        return employeeType;
    }

    public void setEmployeeType(String employeeType) {
        this.employeeType = employeeType;
    }

    public String getWorkStatus() {
        return workStatus;
    }

    public void setWorkStatus(String workStatus) {
        this.workStatus = workStatus;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public void setBirthday(LocalDate birthday) {
        this.birthday = birthday;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
