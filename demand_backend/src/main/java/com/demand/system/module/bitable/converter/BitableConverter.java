package com.demand.system.module.bitable.converter;

import com.demand.system.module.bitable.dto.*;
import com.demand.system.module.bitable.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * 多维表格 MapStruct 转换器
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BitableConverter {

    // ==================== Base ====================

    BitableBase toBase(BitableBaseCreateDTO dto);

    BitableBase toBase(BitableBaseUpdateDTO dto);

    BitableBaseVO toBaseVO(BitableBase base);

    List<BitableBaseVO> toBaseVOList(List<BitableBase> list);

    // ==================== Table ====================

    BitableTable toTable(BitableTableCreateDTO dto);

    BitableTableVO toTableVO(BitableTable table);

    List<BitableTableVO> toTableVOList(List<BitableTable> list);

    // ==================== Field ====================

    @Mapping(target = "config", expression = "java(com.demand.system.module.bitable.util.BitableJsonUtils.toJsonString(dto.getConfig()))")
    BitableField toField(BitableFieldCreateDTO dto);

    @Mapping(target = "config", expression = "java(com.demand.system.module.bitable.util.BitableJsonUtils.parseJson(field.getConfig()))")
    BitableFieldVO toFieldVO(BitableField field);

    List<BitableFieldVO> toFieldVOList(List<BitableField> list);

    // ==================== View ====================

    @Mapping(target = "sortConfig", expression = "java(com.demand.system.module.bitable.util.BitableJsonUtils.toJsonString(dto.getSortConfig()))")
    @Mapping(target = "filterConfig", expression = "java(com.demand.system.module.bitable.util.BitableJsonUtils.toJsonString(dto.getFilterConfig()))")
    @Mapping(target = "groupConfig", expression = "java(com.demand.system.module.bitable.util.BitableJsonUtils.toJsonString(dto.getGroupConfig()))")
    @Mapping(target = "columnConfig", expression = "java(com.demand.system.module.bitable.util.BitableJsonUtils.toJsonString(dto.getColumnConfig()))")
    @Mapping(target = "colorConfig", expression = "java(com.demand.system.module.bitable.util.BitableJsonUtils.toJsonString(dto.getColorConfig()))")
    @Mapping(target = "config", expression = "java(com.demand.system.module.bitable.util.BitableJsonUtils.toJsonString(dto.getConfig()))")
    BitableView toView(BitableViewCreateDTO dto);

    @Mapping(target = "sortConfig", expression = "java(com.demand.system.module.bitable.util.BitableJsonUtils.parseJson(view.getSortConfig()))")
    @Mapping(target = "filterConfig", expression = "java(com.demand.system.module.bitable.util.BitableJsonUtils.parseJson(view.getFilterConfig()))")
    @Mapping(target = "groupConfig", expression = "java(com.demand.system.module.bitable.util.BitableJsonUtils.parseJson(view.getGroupConfig()))")
    @Mapping(target = "columnConfig", expression = "java(com.demand.system.module.bitable.util.BitableJsonUtils.parseJson(view.getColumnConfig()))")
    @Mapping(target = "colorConfig", expression = "java(com.demand.system.module.bitable.util.BitableJsonUtils.parseJson(view.getColorConfig()))")
    @Mapping(target = "config", expression = "java(com.demand.system.module.bitable.util.BitableJsonUtils.parseJson(view.getConfig()))")
    @Mapping(target = "isDefault", ignore = true)
    BitableViewVO toViewVO(BitableView view);

    List<BitableViewVO> toViewVOList(List<BitableView> list);

    // ==================== Record ====================

    /**
     * Record VO 仅映射基础字段，cells 字段由 Service 层单独装配
     */
    BitableRecordVO toRecordVO(BitableRecord record);

    List<BitableRecordVO> toRecordVOList(List<BitableRecord> list);

    // ==================== CellValue ====================

    @Mapping(target = "valueJson", expression = "java(com.demand.system.module.bitable.util.BitableJsonUtils.parseJson(cell.getValueJson()))")
    BitableCellValueVO toCellValueVO(BitableCellValue cell);

    List<BitableCellValueVO> toCellValueVOList(List<BitableCellValue> list);

    // ==================== Member ====================

    BitableBaseMemberVO toMemberVO(BitableBaseMember member);

    List<BitableBaseMemberVO> toMemberVOList(List<BitableBaseMember> list);

    // ==================== Comment ====================

    BitableCommentVO toCommentVO(BitableComment comment);

    List<BitableCommentVO> toCommentVOList(List<BitableComment> list);

    // ==================== Operation ====================

    BitableOperationVO toOperationVO(BitableOperation operation);

    List<BitableOperationVO> toOperationVOList(List<BitableOperation> list);
}
