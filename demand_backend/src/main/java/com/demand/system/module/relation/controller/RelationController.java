package com.demand.system.module.relation.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.relation.dto.RelationCreateDTO;
import com.demand.system.module.relation.dto.RelationVO;
import com.demand.system.module.relation.service.RelationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/requirements")
@RequiredArgsConstructor
public class RelationController {

    private final RelationService relationService;

    @GetMapping("/{id}/relations")
    public Result<List<RelationVO>> listRelations(@PathVariable Long id) {
        return Result.success(relationService.listByRequirement(id));
    }

    @PostMapping("/{id}/relations")
    public Result<Void> createRelation(@PathVariable Long id, @Valid @RequestBody RelationCreateDTO dto) {
        relationService.create(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}/relations/{relId}")
    public Result<Void> deleteRelation(@PathVariable Long id, @PathVariable Long relId) {
        relationService.delete(relId);
        return Result.success();
    }
}
