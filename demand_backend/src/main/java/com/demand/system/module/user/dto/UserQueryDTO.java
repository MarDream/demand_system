package com.demand.system.module.user.dto;

public class UserQueryDTO {

    private String username;

    private String realName;

    private String status;

    private String employeeType;

    private String workStatus;

    private String hireDateFrom;

    private String hireDateTo;

    private Long regionId;

    private Long departmentId;

    private Long orgId;

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
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

    public String getHireDateFrom() {
        return hireDateFrom;
    }

    public void setHireDateFrom(String hireDateFrom) {
        this.hireDateFrom = hireDateFrom;
    }

    public String getHireDateTo() {
        return hireDateTo;
    }

    public void setHireDateTo(String hireDateTo) {
        this.hireDateTo = hireDateTo;
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

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
