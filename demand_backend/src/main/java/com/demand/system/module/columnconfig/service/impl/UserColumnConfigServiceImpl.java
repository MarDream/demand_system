package com.demand.system.module.columnconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.module.columnconfig.entity.UserColumnConfig;
import com.demand.system.module.columnconfig.mapper.UserColumnConfigMapper;
import com.demand.system.module.columnconfig.service.UserColumnConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserColumnConfigServiceImpl implements UserColumnConfigService {

    private final UserColumnConfigMapper mapper;
    private final ObjectMapper objectMapper;

    public UserColumnConfigServiceImpl(UserColumnConfigMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<String> getVisibleColumns(Long userId, String pageKey) {
        UserColumnConfig config = findConfig(userId, pageKey);
        if (config == null || config.getVisibleColumns() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(config.getVisibleColumns(), new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @Override
    public void saveVisibleColumns(Long userId, String pageKey, List<String> columns) {
        String json;
        try {
            json = objectMapper.writeValueAsString(columns);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化列配置失败", e);
        }

        UserColumnConfig existing = findConfig(userId, pageKey);
        if (existing != null) {
            UpdateWrapper<UserColumnConfig> wrapper = new UpdateWrapper<>();
            wrapper.eq("id", existing.getId()).set("visible_columns", json);
            mapper.update(null, wrapper);
        } else {
            UserColumnConfig config = new UserColumnConfig();
            config.setUserId(userId);
            config.setPageKey(pageKey);
            config.setVisibleColumns(json);
            mapper.insert(config);
        }
    }

    private UserColumnConfig findConfig(Long userId, String pageKey) {
        return mapper.selectOne(
                new LambdaQueryWrapper<UserColumnConfig>()
                        .eq(UserColumnConfig::getUserId, userId)
                        .eq(UserColumnConfig::getPageKey, pageKey)
        );
    }
}