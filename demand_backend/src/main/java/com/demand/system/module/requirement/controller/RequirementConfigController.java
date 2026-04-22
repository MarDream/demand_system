package com.demand.system.module.requirement.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.requirement.entity.PriorityConfig;
import com.demand.system.module.requirement.entity.RequirementTypeConfig;
import com.demand.system.module.requirement.service.RequirementConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/requirement-config")
@RequiredArgsConstructor
public class RequirementConfigController {

    private final RequirementConfigService configService;

    @GetMapping("/types")
    public Result<List<RequirementTypeConfig>> listTypes() {
        return configService.listTypes();
    }

    @PostMapping("/types")
    public Result<Void> createType(@Valid @RequestBody RequirementTypeConfig type) {
        return configService.createType(type);
    }

    @PutMapping("/types/{id}")
    public Result<Void> updateType(@PathVariable Long id, @Valid @RequestBody RequirementTypeConfig type) {
        type.setId(id);
        return configService.updateType(type);
    }

    @DeleteMapping("/types/{id}")
    public Result<Void> deleteType(@PathVariable Long id) {
        return configService.deleteType(id);
    }

    @GetMapping("/priorities")
    public Result<List<PriorityConfig>> listPriorities() {
        return configService.listPriorities();
    }

    @PostMapping("/priorities")
    public Result<Void> createPriority(@Valid @RequestBody PriorityConfig priority) {
        return configService.createPriority(priority);
    }

    @PutMapping("/priorities/{id}")
    public Result<Void> updatePriority(@PathVariable Long id, @Valid @RequestBody PriorityConfig priority) {
        priority.setId(id);
        return configService.updatePriority(priority);
    }

    @DeleteMapping("/priorities/{id}")
    public Result<Void> deletePriority(@PathVariable Long id) {
        return configService.deletePriority(id);
    }
}