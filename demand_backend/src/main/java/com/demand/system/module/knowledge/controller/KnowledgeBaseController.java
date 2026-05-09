package com.demand.system.module.knowledge.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.knowledge.dto.KnowledgeBaseCreateDTO;
import com.demand.system.module.knowledge.dto.KnowledgeBaseUpdateDTO;
import com.demand.system.module.knowledge.dto.KnowledgeBaseVO;
import com.demand.system.module.knowledge.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge/bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping
    public Result<KnowledgeBaseVO> create(@Valid @RequestBody KnowledgeBaseCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        KnowledgeBaseVO vo = knowledgeBaseService.create(dto, userId);
        return Result.success(vo);
    }

    @GetMapping
    public Result<PageResult<KnowledgeBaseVO>> list(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResult<KnowledgeBaseVO> result = knowledgeBaseService.list(name, pageNum, pageSize);
        return Result.success(result);
    }

    @GetMapping("/all")
    public Result<List<KnowledgeBaseVO>> listAll() {
        List<KnowledgeBaseVO> list = knowledgeBaseService.listAll();
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<KnowledgeBaseVO> getById(@PathVariable Long id) {
        KnowledgeBaseVO vo = knowledgeBaseService.getById(id);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    public Result<KnowledgeBaseVO> update(@PathVariable Long id, @Valid @RequestBody KnowledgeBaseUpdateDTO dto) {
        KnowledgeBaseVO vo = knowledgeBaseService.update(id, dto);
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return Result.success();
    }
}
