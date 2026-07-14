package com.demand.system.module.bitable.service.impl;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.bitable.dto.*;
import com.demand.system.module.bitable.entity.*;
import com.demand.system.module.bitable.mapper.*;
import com.demand.system.module.bitable.service.BitableTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 多维表格模板库 Service 实现
 * <p>
 * 预设模板在内存中维护，无需数据库表。
 */
@Service
public class BitableTemplateServiceImpl implements BitableTemplateService {

    private static final List<BitableTemplateVO> TEMPLATES = List.of(
            new BitableTemplateVO("project_management", "项目管理", "任务名称、负责人、状态、优先级、日期、进度等", "FolderOpened", 8),
            new BitableTemplateVO("recruitment", "招聘管理", "候选人、岗位、面试状态、面试官、面试日期等", "UserFilled", 7),
            new BitableTemplateVO("crm", "客户管理", "客户名称、联系人、手机号、销售阶段、预计金额等", "OfficeBuilding", 7)
    );

    private final BitableBaseMapper baseMapper;
    private final BitableTableMapper tableMapper;
    private final BitableFieldMapper fieldMapper;
    private final BitableRecordMapper recordMapper;
    private final BitableCellMapper cellMapper;
    private final BitableViewMapper viewMapper;
    private final BitableBaseMemberMapper memberMapper;

    public BitableTemplateServiceImpl(BitableBaseMapper baseMapper,
                                       BitableTableMapper tableMapper,
                                       BitableFieldMapper fieldMapper,
                                       BitableRecordMapper recordMapper,
                                       BitableCellMapper cellMapper,
                                       BitableViewMapper viewMapper,
                                       BitableBaseMemberMapper memberMapper) {
        this.baseMapper = baseMapper;
        this.tableMapper = tableMapper;
        this.fieldMapper = fieldMapper;
        this.recordMapper = recordMapper;
        this.cellMapper = cellMapper;
        this.viewMapper = viewMapper;
        this.memberMapper = memberMapper;
    }

    @Override
    public List<BitableTemplateVO> listTemplates() {
        return TEMPLATES;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createBaseFromTemplate(String templateCode, Long userId) {
        BitableTemplateVO template = TEMPLATES.stream()
                .filter(t -> t.getCode().equals(templateCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException("模板不存在: " + templateCode));

        // 1. 创建 Base
        BitableBase base = new BitableBase();
        base.setName(template.getName());
        base.setDescription(template.getDescription());
        base.setCreatorId(userId);
        base.setIsTemplate(0);
        base.setSortOrder(0);
        baseMapper.insert(base);

        // 将创建者加入成员
        BitableBaseMember member = new BitableBaseMember();
        member.setBaseId(base.getId());
        member.setUserId(userId);
        member.setRole("owner");
        memberMapper.insert(member);

        // 2. 创建 Table（模板表名与模板名相同）
        BitableTable table = new BitableTable();
        table.setBaseId(base.getId());
        table.setName(template.getName());
        table.setSortOrder(0);
        tableMapper.insert(table);

        // 3. 构建字段定义
        List<FieldDef> fieldDefs = getFieldDefs(templateCode);

        // 4. 创建 Fields
        List<BitableField> fields = new ArrayList<>();
        for (int i = 0; i < fieldDefs.size(); i++) {
            FieldDef def = fieldDefs.get(i);
            BitableField field = new BitableField();
            field.setTableId(table.getId());
            field.setName(def.name);
            field.setFieldType(def.fieldType);
            field.setConfig(def.config);
            field.setRequired(def.required ? 1 : 0);
            field.setIsAiField(0);
            field.setSortOrder(i + 1);
            field.setWidth(150);
            fieldMapper.insert(field);
            fields.add(field);
        }

        // 5. 创建默认 Grid 视图
        BitableView defaultView = new BitableView();
        defaultView.setTableId(table.getId());
        defaultView.setName("默认视图");
        defaultView.setViewType("grid");
        defaultView.setSortOrder(0);
        defaultView.setCreatedBy(userId);
        viewMapper.insert(defaultView);

        // 6. 创建示例记录
        List<List<String>> sampleRecords = getSampleRecords(templateCode);
        if (sampleRecords != null && !sampleRecords.isEmpty()) {
            for (List<String> row : sampleRecords) {
                BitableRecord record = new BitableRecord();
                record.setTableId(table.getId());
                record.setCreatedBy(userId);
                record.setUpdatedBy(userId);
                record.setSortOrder(0);
                record.setVersion(0);
                recordMapper.insert(record);

                for (int col = 0; col < fieldDefs.size() && col < row.size(); col++) {
                    FieldDef def = fieldDefs.get(col);
                    String cellValue = row.get(col);
                    if (cellValue == null || cellValue.isEmpty()) {
                        continue;
                    }
                    BitableCellValue cell = buildCellValue(record.getId(), fields.get(col).getId(), def, cellValue);
                    cellMapper.saveOrUpdateCell(cell);
                }
            }
        }

        return base.getId();
    }

    private BitableCellValue buildCellValue(Long recordId, Long fieldId, FieldDef def, String rawValue) {
        BitableCellValue cell = new BitableCellValue();
        cell.setRecordId(recordId);
        cell.setFieldId(fieldId);

        switch (def.fieldType) {
            case "text", "single_select", "multi_select", "user" ->
                cell.setValueText(rawValue);
            case "number" -> {
                try {
                    cell.setValueNumber(new BigDecimal(rawValue));
                } catch (NumberFormatException e) {
                    cell.setValueText(rawValue);
                }
            }
            case "date" -> {
                try {
                    cell.setValueDate(java.time.LocalDate.parse(rawValue));
                } catch (Exception e) {
                    cell.setValueText(rawValue);
                }
            }
            case "progress" -> {
                try {
                    cell.setValueNumber(new BigDecimal(rawValue));
                } catch (NumberFormatException e) {
                    cell.setValueText(rawValue);
                }
            }
            case "rating" -> {
                try {
                    cell.setValueNumber(new BigDecimal(rawValue));
                } catch (NumberFormatException e) {
                    cell.setValueText(rawValue);
                }
            }
            default -> cell.setValueText(rawValue);
        }

        return cell;
    }

    // ======================== 字段定义 ========================

    private record FieldDef(String name, String fieldType, String config, boolean required) {}

    private static List<FieldDef> getFieldDefs(String templateCode) {
        return switch (templateCode) {
            case "project_management" -> List.of(
                    new FieldDef("任务名称", "text", null, true),
                    new FieldDef("负责人", "user", null, false),
                    new FieldDef("状态", "single_select",
                            "{\"options\":[" +
                                    "{\"label\":\"未开始\",\"color\":\"#909399\"}," +
                                    "{\"label\":\"进行中\",\"color\":\"#409EFF\"}," +
                                    "{\"label\":\"已完成\",\"color\":\"#67C23A\"}" +
                                    "]}", false),
                    new FieldDef("优先级", "single_select",
                            "{\"options\":[" +
                                    "{\"label\":\"高\",\"color\":\"#F56C6C\"}," +
                                    "{\"label\":\"中\",\"color\":\"#E6A23C\"}," +
                                    "{\"label\":\"低\",\"color\":\"#909399\"}" +
                                    "]}", false),
                    new FieldDef("开始日期", "date", null, false),
                    new FieldDef("结束日期", "date", null, false),
                    new FieldDef("进度", "progress", null, false),
                    new FieldDef("备注", "text", null, false)
            );
            case "recruitment" -> List.of(
                    new FieldDef("候选人", "text", null, true),
                    new FieldDef("岗位", "text", null, false),
                    new FieldDef("面试状态", "single_select",
                            "{\"options\":[" +
                                    "{\"label\":\"简历筛选\",\"color\":\"#909399\"}," +
                                    "{\"label\":\"初试\",\"color\":\"#409EFF\"}," +
                                    "{\"label\":\"复试\",\"color\":\"#E6A23C\"}," +
                                    "{\"label\":\"终试\",\"color\":\"#F56C6C\"}," +
                                    "{\"label\":\"已录用\",\"color\":\"#67C23A\"}," +
                                    "{\"label\":\"已拒绝\",\"color\":\"#909399\"}" +
                                    "]}", false),
                    new FieldDef("面试官", "user", null, false),
                    new FieldDef("面试日期", "date", null, false),
                    new FieldDef("评分", "rating", null, false),
                    new FieldDef("备注", "text", null, false)
            );
            case "crm" -> List.of(
                    new FieldDef("客户名称", "text", null, true),
                    new FieldDef("联系人", "text", null, false),
                    new FieldDef("手机号", "text", null, false),
                    new FieldDef("销售阶段", "single_select",
                            "{\"options\":[" +
                                    "{\"label\":\"初步接触\",\"color\":\"#909399\"}," +
                                    "{\"label\":\"需求确认\",\"color\":\"#409EFF\"}," +
                                    "{\"label\":\"报价\",\"color\":\"#E6A23C\"}," +
                                    "{\"label\":\"谈判\",\"color\":\"#F56C6C\"}," +
                                    "{\"label\":\"成交\",\"color\":\"#67C23A\"}," +
                                    "{\"label\":\"流失\",\"color\":\"#909399\"}" +
                                    "]}", false),
                    new FieldDef("预计金额", "number", null, false),
                    new FieldDef("跟进日期", "date", null, false),
                    new FieldDef("备注", "text", null, false)
            );
            default -> throw new BusinessException("未知模板编码: " + templateCode);
        };
    }

    // ======================== 示例记录 ========================

    private static List<List<String>> getSampleRecords(String templateCode) {
        return switch (templateCode) {
            case "project_management" -> List.of(
                    List.of("需求分析", "张三", "已完成", "高", "2025-01-06", "2025-01-10", "100", "完成需求文档评审"),
                    List.of("UI 设计", "李四", "进行中", "高", "2025-01-11", "2025-01-18", "60", "等待设计稿确认"),
                    List.of("后端开发", "王五", "进行中", "高", "2025-01-15", "2025-02-05", "40", "API 开发中"),
                    List.of("前端开发", "赵六", "未开始", "中", "2025-01-20", "2025-02-10", "0", "等待接口联调"),
                    List.of("测试", "钱七", "未开始", "中", "2025-02-10", "2025-02-20", "0", "编写测试用例"),
                    List.of("部署上线", "孙八", "未开始", "低", "2025-02-21", "2025-02-22", "0", "生产环境部署")
            );
            case "recruitment" -> List.of(
                    List.of("张三丰", "Java 高级工程师", "初试", "李四", "2025-02-01", "4", "技术基础扎实"),
                    List.of("李逍遥", "前端开发工程师", "简历筛选", "", "", "", "3 年经验，期望薪资合理"),
                    List.of("王小美", "产品经理", "复试", "赵六", "2025-02-05", "4.5", "沟通能力出色"),
                    List.of("赵云", "测试工程师", "终试", "孙八", "2025-02-08", "4", "自动化测试经验丰富"),
                    List.of("关羽", "Java 高级工程师", "已录用", "李四", "2025-02-10", "5", "技术面试满分，下周入职")
            );
            case "crm" -> List.of(
                    List.of("创新科技有限公司", "陈总", "13800138001", "需求确认", "150000", "2025-02-01", "对 ERP 系统感兴趣"),
                    List.of("未来软件公司", "刘经理", "13900139002", "报价", "80000", "2025-02-03", "预算有限，需打折"),
                    List.of("智慧云平台", "周先生", "13700137003", "初步接触", "", "", "第一次通话了解需求"),
                    List.of("敏捷网络科技", "吴经理", "13600136004", "谈判", "200000", "2025-02-08", "要求分期付款"),
                    List.of("大华集团", "郑总监", "13500135005", "成交", "500000", "2025-01-15", "已签合同，年付")
            );
            default -> List.of();
        };
    }
}