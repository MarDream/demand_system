package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.dto.BitableBaseCreateDTO;
import com.demand.system.module.bitable.dto.BitableBaseUpdateDTO;
import com.demand.system.module.bitable.dto.BitableBaseVO;

import java.util.List;

/**
 * 多维表格容器 Service
 */
public interface BitableBaseService {

    /**
     * 列出用户可见的 Base（创建的 + 作为成员的）
     *
     * @param userId 用户ID
     * @return Base 列表
     */
    List<BitableBaseVO> listBases(Long userId);

    /**
     * 获取 Base 详情（含 tableCount）
     *
     * @param id Base ID
     * @return Base 详情
     */
    BitableBaseVO getBaseById(Long id);

    /**
     * 创建 Base，返回新 Base 的 ID
     *
     * @param dto    创建参数
     * @param userId 创建者ID
     * @return 新 Base 的 ID
     */
    Long createBase(BitableBaseCreateDTO dto, Long userId);

    /**
     * 更新 Base
     *
     * @param id  Base ID
     * @param dto 更新参数
     */
    void updateBase(Long id, BitableBaseUpdateDTO dto);

    /**
     * 删除 Base（级联删除表/字段/记录/单元格/视图/成员）
     *
     * @param id Base ID
     */
    void deleteBase(Long id);
}