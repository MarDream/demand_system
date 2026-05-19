package com.demand.system.module.project.controller;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.project.dto.ProjectCreateDTO;
import com.demand.system.module.project.dto.ProjectImportResultDTO;
import com.demand.system.module.project.dto.ProjectMemberAddDTO;
import com.demand.system.module.project.dto.ProjectUpdateDTO;
import com.demand.system.module.project.entity.Project;
import com.demand.system.module.project.entity.ProjectMember;
import com.demand.system.module.project.service.ProjectService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public Result<PageResult<Project>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResult<Project> result = projectService.list(name, status, pageNum, pageSize);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<Project> getById(@PathVariable Long id) {
        Project project = projectService.getById(id);
        return Result.success(project);
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody ProjectCreateDTO dto) {
        Long creatorId = SecurityUtils.getCurrentUserId();
        if (creatorId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        projectService.create(dto, creatorId);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProjectUpdateDTO dto) {
        dto.setId(id);
        projectService.update(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/members")
    public Result<List<ProjectMember>> getMembers(@PathVariable Long id) {
        List<ProjectMember> members = projectService.getMembers(id);
        return Result.success(members);
    }

    @PostMapping("/{id}/members")
    public Result<Void> addMember(@PathVariable Long id, @Valid @RequestBody ProjectMemberAddDTO dto) {
        projectService.addMember(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        projectService.removeMember(id, userId);
        return Result.success();
    }

    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("项目导入模板");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("项目名称");
            header.createCell(1).setCellValue("归属公司ID");
            header.createCell(2).setCellValue("归属团队");
            header.createCell(3).setCellValue("负责人用户ID");
            header.createCell(4).setCellValue("开始日期(yyyy-MM-dd)");
            header.createCell(5).setCellValue("截止日期(yyyy-MM-dd)");
            header.createCell(6).setCellValue("描述");

            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("示例项目");
            sample.createCell(1).setCellValue("1");
            sample.createCell(2).setCellValue("产品研发组");
            sample.createCell(3).setCellValue("1");
            sample.createCell(4).setCellValue("2026-05-10");
            sample.createCell(5).setCellValue("2026-06-30");
            sample.createCell(6).setCellValue("请按模板格式填写");

            for (int i = 0; i < 7; i++) {
                sheet.autoSizeColumn(i);
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader(
                    "Content-Disposition",
                    "attachment; filename*=UTF-8''" + URLEncoder.encode("项目导入模板.xlsx", StandardCharsets.UTF_8)
            );
            workbook.write(response.getOutputStream());
        }
    }

    @PostMapping("/import")
    public Result<ProjectImportResultDTO> importProjects(@RequestParam("file") MultipartFile file) throws IOException {
        Long creatorId = SecurityUtils.getCurrentUserId();
        if (creatorId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        int successCount = 0;
        int failCount = 0;
        List<ProjectImportResultDTO.FailureDetail> failures = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isRowEmpty(row, formatter)) {
                    continue;
                }
                try {
                    ProjectCreateDTO dto = new ProjectCreateDTO();
                    dto.setName(getCellString(row.getCell(0), formatter));
                    dto.setCompanyId(parseLongCell(row.getCell(1), formatter));
                    dto.setTeam(getCellString(row.getCell(2), formatter));
                    dto.setLeaderId(parseLongCell(row.getCell(3), formatter));
                    dto.setStartDate(parseDateCell(row.getCell(4), formatter));
                    dto.setEndDate(parseDateCell(row.getCell(5), formatter));
                    dto.setDescription(getCellString(row.getCell(6), formatter));
                    if (dto.getName() == null || dto.getName().isBlank()) {
                        throw new IllegalArgumentException("项目名称不能为空");
                    }
                    projectService.create(dto, creatorId);
                    successCount++;
                } catch (Exception ex) {
                    failCount++;
                    failures.add(ProjectImportResultDTO.FailureDetail.builder()
                            .rowNum(rowIndex + 1)
                            .projectName(getCellString(row.getCell(0), formatter))
                            .reason(resolveImportFailureReason(ex))
                            .build());
                }
            }
        }
        return Result.success(ProjectImportResultDTO.builder()
                .successCount(successCount)
                .failCount(failCount)
                .failures(failures)
                .build());
    }

    private boolean isRowEmpty(Row row, DataFormatter formatter) {
        for (int i = 0; i <= 6; i++) {
            if (!getCellString(row.getCell(i), formatter).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String getCellString(Cell cell, DataFormatter formatter) {
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private Long parseLongCell(Cell cell, DataFormatter formatter) {
        String value = getCellString(cell, formatter);
        return value.isBlank() ? null : Long.parseLong(value);
    }

    private LocalDate parseDateCell(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }
        if (DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String value = getCellString(cell, formatter);
        return value.isBlank() ? null : LocalDate.parse(value);
    }

    private String resolveImportFailureReason(Exception ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? "导入失败，请检查数据格式" : message;
    }
}
