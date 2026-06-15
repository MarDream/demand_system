package com.demand.system.module.knowledge.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.knowledge.dto.KnowledgeBaseCreateDTO;
import com.demand.system.module.knowledge.dto.KnowledgeBaseMigrateDTO;
import com.demand.system.module.knowledge.dto.KnowledgeBaseUpdateDTO;
import com.demand.system.module.knowledge.dto.KnowledgeBaseVO;
import com.demand.system.module.knowledge.dto.KnowledgeMigrateResultVO;
import com.demand.system.module.knowledge.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge/bases")
public class KnowledgeBaseController {
    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:knowledge:create')")
    public Result<KnowledgeBaseVO> create(@Valid @RequestBody KnowledgeBaseCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        KnowledgeBaseVO vo = knowledgeBaseService.create(dto, userId);
        return Result.success(vo);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<KnowledgeBaseVO>> list(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResult<KnowledgeBaseVO> result = knowledgeBaseService.list(name, pageNum, pageSize);
        return Result.success(result);
    }

    @GetMapping("/all")
    @PreAuthorize("isAuthenticated()")
    public Result<List<KnowledgeBaseVO>> listAll() {
        List<KnowledgeBaseVO> list = knowledgeBaseService.listAll();
        return Result.success(list);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<KnowledgeBaseVO> getById(@PathVariable Long id) {
        KnowledgeBaseVO vo = knowledgeBaseService.getById(id);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:knowledge:update')")
    public Result<KnowledgeBaseVO> update(@PathVariable Long id, @Valid @RequestBody KnowledgeBaseUpdateDTO dto) {
        KnowledgeBaseVO vo = knowledgeBaseService.update(id, dto);
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:knowledge:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return Result.success();
    }

    /**
     * 将源知识库下的文档迁移到目标知识库。
     * 通常在删除前调用，保留数据的同时支持清理源知识库。
     */
    @PostMapping("/{id}/migrate")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:knowledge:migrate')")
    public Result<KnowledgeMigrateResultVO> migrateDocuments(
            @PathVariable Long id,
            @Valid @RequestBody KnowledgeBaseMigrateDTO dto) {
        return Result.success(knowledgeBaseService.migrateDocuments(id, dto));
    }
}
