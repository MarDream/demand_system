package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableOperation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 多维表格-操作历史审计 Mapper
 */
@Mapper
public interface BitableOperationMapper extends BaseMapper<BitableOperation> {

    /**
     * 分页查询指定多维表格容器的操作历史（支持筛选）
     *
     * @param baseId         容器ID
     * @param offset         偏移量
     * @param limit          限制数量
     * @param operationTypes 操作类型编码列表，为空查全部
     * @param userId         操作人ID，为空查全部
     * @param startTime      起始时间，为空不限
     * @param endTime        截止时间，为空不限
     * @return 操作历史列表
     */
    List<BitableOperation> selectByBaseId(@Param("baseId") Long baseId,
                                          @Param("offset") Integer offset,
                                          @Param("limit") Integer limit,
                                          @Param("operationTypes") List<String> operationTypes,
                                          @Param("userId") Long userId,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定多维表格容器的操作历史数量（支持筛选）
     */
    int countByBaseId(@Param("baseId") Long baseId,
                      @Param("operationTypes") List<String> operationTypes,
                      @Param("userId") Long userId,
                      @Param("startTime") LocalDateTime startTime,
                      @Param("endTime") LocalDateTime endTime);

    /**
     * 分页查询指定数据表的操作历史（支持筛选）
     */
    List<BitableOperation> selectByTableId(@Param("baseId") Long baseId,
                                           @Param("tableId") Long tableId,
                                           @Param("offset") Integer offset,
                                           @Param("limit") Integer limit,
                                           @Param("operationTypes") List<String> operationTypes,
                                           @Param("userId") Long userId,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定数据表的操作历史数量（支持筛选）
     */
    int countByTableId(@Param("baseId") Long baseId,
                       @Param("tableId") Long tableId,
                       @Param("operationTypes") List<String> operationTypes,
                       @Param("userId") Long userId,
                       @Param("startTime") LocalDateTime startTime,
                       @Param("endTime") LocalDateTime endTime);
}
