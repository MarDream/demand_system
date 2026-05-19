package com.demand.system.module.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.organization.dto.PositionCreateDTO;
import com.demand.system.module.organization.dto.PositionSortDTO;
import com.demand.system.module.organization.dto.PositionUpdateDTO;
import com.demand.system.module.organization.dto.PositionVO;
import com.demand.system.module.organization.entity.Position;
import com.demand.system.module.organization.mapper.PositionMapper;
import com.demand.system.module.organization.service.PositionService;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PositionServiceImpl implements PositionService {

    private final PositionMapper positionMapper;
    private final UserMapper userMapper;

    public PositionServiceImpl(PositionMapper positionMapper, UserMapper userMapper) {
        this.positionMapper = positionMapper;
        this.userMapper = userMapper;
    }

    @Override
    public List<PositionVO> list() {
        List<Position> positions = positionMapper.selectList(
            new LambdaQueryWrapper<Position>()
                .orderByAsc(Position::getSortOrder)
                .orderByAsc(Position::getId)
        );

        return positions.stream()
            .map(this::convertToVO)
            .collect(Collectors.toList());
    }

    @Override
    public PositionVO getById(Long id) {
        Position position = positionMapper.selectById(id);
        if (position == null) {
            throw new BusinessException("岗位不存在");
        }
        return convertToVO(position);
    }

    @Override
    public void create(PositionCreateDTO dto) {
        Position position = new Position();
        BeanUtils.copyProperties(dto, position);
        positionMapper.insert(position);
    }

    @Override
    public void update(PositionUpdateDTO dto) {
        Position position = positionMapper.selectById(dto.getId());
        if (position == null) {
            throw new BusinessException("岗位不存在");
        }

        BeanUtils.copyProperties(dto, position);
        positionMapper.updateById(position);
    }

    @Override
    public void delete(Long id) {
        Position position = positionMapper.selectById(id);
        if (position == null) {
            throw new BusinessException("岗位不存在");
        }

        // 检查是否有用户
        Long userCount = userMapper.selectCount(
            new LambdaQueryWrapper<User>()
                .eq(User::getPositionId, id)
        );
        if (userCount > 0) {
            throw new BusinessException("该岗位下存在用户，无法删除");
        }

        positionMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSort(PositionSortDTO dto) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            return;
        }

        for (PositionSortDTO.SortItem item : dto.getItems()) {
            Position position = positionMapper.selectById(item.getId());
            if (position != null) {
                position.setSortOrder(item.getSortOrder());
                positionMapper.updateById(position);
            }
        }
    }

    private PositionVO convertToVO(Position position) {
        PositionVO vo = new PositionVO();
        BeanUtils.copyProperties(position, vo);
        return vo;
    }
}
