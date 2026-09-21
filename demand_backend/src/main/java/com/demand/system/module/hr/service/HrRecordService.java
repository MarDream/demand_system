package com.demand.system.module.hr.service;

import com.demand.system.common.result.PageResult;
import com.demand.system.module.hr.dto.HrRecordCreateDTO;
import com.demand.system.module.hr.dto.HrRecordQueryDTO;
import com.demand.system.module.hr.dto.HrRecordUpdateDTO;
import com.demand.system.module.hr.dto.HrRecordVO;

import java.util.Map;

public interface HrRecordService {

    PageResult<HrRecordVO> page(HrRecordQueryDTO query);

    HrRecordVO create(HrRecordCreateDTO dto);

    HrRecordVO update(Long id, HrRecordUpdateDTO dto);

    void delete(Long id);

    /** 各事件类型的统计：processing/done/cancelled 基础计数 + 合同到期提醒等扩展计数 */
    Map<String, Object> summary(String recordType);

    /**
     * 人事模块场景统计（对齐钉钉各模块首屏数字）：
     * 待入职/试用期/待离职员工数、未完善手机号（用工安全）、未签合同、本月生日员工列表
     */
    Map<String, Object> overview();
}
