package com.demand.system.module.llm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.demand.system.module.llm.dto.LlmApplicationUpdateDTO;
import com.demand.system.module.llm.dto.LlmApplicationVO;
import com.demand.system.module.llm.entity.LlmApplication;
import com.demand.system.module.llm.entity.LlmModel;
import com.demand.system.module.llm.entity.LlmProvider;
import com.demand.system.module.llm.mapper.LlmApplicationMapper;
import com.demand.system.module.llm.mapper.LlmModelMapper;
import com.demand.system.module.llm.mapper.LlmProviderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class LlmApplicationService {
    private final LlmApplicationMapper applicationMapper;
    private final LlmModelMapper modelMapper;
    private final LlmProviderMapper providerMapper;

    public LlmApplicationService(LlmApplicationMapper applicationMapper,
                                 LlmModelMapper modelMapper,
                                 LlmProviderMapper providerMapper) {
        this.applicationMapper = applicationMapper;
        this.modelMapper = modelMapper;
        this.providerMapper = providerMapper;
    }

    public List<LlmApplicationVO> list() {
        List<LlmApplication> applications = applicationMapper.selectList(
                new LambdaQueryWrapper<LlmApplication>().orderByAsc(LlmApplication::getSortOrder)
        );
        return applications.stream().map(this::toVO).toList();
    }

    @Transactional
    public LlmApplicationVO update(String code, LlmApplicationUpdateDTO dto) {
        LlmApplication application = getApplication(code);
        if (dto == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "应用配置不能为空");
        }

        if (dto.getModelId() != null) {
            LlmModel model = modelMapper.selectById(dto.getModelId());
            if (model == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "指定模型不存在");
            }
            if (!isModelTypeCompatible(application.getModelType(), model)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "模型类型不匹配：该应用需要 " + application.getModelType() + " 类型模型");
            }
        }

        LlmApplication update = new LlmApplication();
        update.setId(application.getId());
        update.setModelId(dto.getModelId());
        if (dto.getEnabled() != null) {
            update.setEnabled(dto.getEnabled());
        }
        applicationMapper.updateById(update);
        return toVO(applicationMapper.selectById(application.getId()));
    }

    public LlmApplication getApplication(String code) {
        LlmApplication application = applicationMapper.selectOne(
                new LambdaQueryWrapper<LlmApplication>().eq(LlmApplication::getCode, code)
        );
        if (application == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未找到 LLM 应用配置：" + code);
        }
        return application;
    }

    private LlmApplicationVO toVO(LlmApplication application) {
        LlmApplicationVO vo = new LlmApplicationVO();
        vo.setId(application.getId());
        vo.setCode(application.getCode());
        vo.setName(application.getName());
        vo.setDescription(application.getDescription());
        vo.setModelType(application.getModelType());
        vo.setModelId(application.getModelId());
        vo.setEnabled(application.getEnabled());
        vo.setSortOrder(application.getSortOrder());

        if (application.getModelId() != null) {
            LlmModel model = modelMapper.selectById(application.getModelId());
            if (model != null) {
                vo.setModelName(model.getName());
                vo.setModelCode(model.getModelId());
                LlmProvider provider = providerMapper.selectById(model.getProviderId());
                vo.setProviderName(provider != null ? provider.getName() : null);
                vo.setModelAvailable(Boolean.TRUE.equals(model.getEnabled())
                        && provider != null && Boolean.TRUE.equals(provider.getEnabled()));
            } else {
                vo.setModelAvailable(false);
            }
        } else {
            vo.setModelAvailable(false);
        }
        return vo;
    }

    private boolean isModelTypeCompatible(String applicationType, LlmModel model) {
        if ("chat".equalsIgnoreCase(applicationType)) {
            return !"embedding".equalsIgnoreCase(model.getModelType())
                    && !"rerank".equalsIgnoreCase(model.getModelType());
        }
        return applicationType.equalsIgnoreCase(model.getModelType());
    }
}
