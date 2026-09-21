package com.demand.system.module.hr.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.hr.dto.HrRecordCreateDTO;
import com.demand.system.module.hr.dto.HrRecordQueryDTO;
import com.demand.system.module.hr.dto.HrRecordUpdateDTO;
import com.demand.system.module.hr.dto.HrRecordVO;
import com.demand.system.module.hr.service.HrRecordService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 人事事件台账（入职/新人成长/转正/异动/离职/合同/退休/员工关怀/用工安全）
 */
@RestController
@RequestMapping("/api/v1/hr/records")
public class HrRecordController {

    private final HrRecordService hrRecordService;

    public HrRecordController(HrRecordService hrRecordService) {
        this.hrRecordService = hrRecordService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<HrRecordVO>> page(HrRecordQueryDTO query) {
        return Result.success(hrRecordService.page(query));
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> summary(@RequestParam(required = false) String recordType) {
        return Result.success(hrRecordService.summary(recordType));
    }

    /** 场景统计：待入职/试用期/待离职/未完善手机号/未签合同/本月生日 */
    @GetMapping("/overview")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> overview() {
        return Result.success(hrRecordService.overview());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('button:user:create')")
    public Result<HrRecordVO> create(@Valid @RequestBody HrRecordCreateDTO dto) {
        return Result.success(hrRecordService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('button:user:update') or hasAuthority('button:user:create')")
    public Result<HrRecordVO> update(@PathVariable Long id, @RequestBody HrRecordUpdateDTO dto) {
        return Result.success(hrRecordService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('button:user:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        hrRecordService.delete(id);
        return Result.success(null);
    }
}
