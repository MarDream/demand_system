package com.demand.system.module.bitable.converter;

import com.demand.system.module.bitable.dto.*;
import com.demand.system.module.bitable.entity.*;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 多维表格 MapStruct 转换器
 */
@Mapper(componentModel = "spring")
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

    BitableField toField(BitableFieldCreateDTO dto);

    BitableFieldVO toFieldVO(BitableField field);

    List<BitableFieldVO> toFieldVOList(List<BitableField> list);

    // ==================== View ====================

    BitableView toView(BitableViewCreateDTO dto);

    BitableViewVO toViewVO(BitableView view);

    List<BitableViewVO> toViewVOList(List<BitableView> list);

    // ==================== Record ====================

    /**
     * Record VO 仅映射基础字段，cells 字段由 Service 层单独装配
     */
    BitableRecordVO toRecordVO(BitableRecord record);

    List<BitableRecordVO> toRecordVOList(List<BitableRecord> list);

    // ==================== CellValue ====================

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
