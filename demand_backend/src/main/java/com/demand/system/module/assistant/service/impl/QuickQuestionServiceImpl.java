package com.demand.system.module.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.module.assistant.dto.ExtractedQuestionVO;
import com.demand.system.module.assistant.dto.QuickQuestionCreateDTO;
import com.demand.system.module.assistant.dto.QuickQuestionVO;
import com.demand.system.module.assistant.entity.QuickQuestion;
import com.demand.system.module.assistant.mapper.QuickQuestionMapper;
import com.demand.system.module.assistant.service.QuickQuestionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class QuickQuestionServiceImpl implements QuickQuestionService {

    private static final int MAX_PER_PAGE = 3;
    private static final int DEFAULT_WINDOW_DAYS = 30;
    private static final int DEFAULT_MIN_FREQUENCY = 5;

    private final QuickQuestionMapper quickQuestionMapper;

    public QuickQuestionServiceImpl(QuickQuestionMapper quickQuestionMapper) {
        this.quickQuestionMapper = quickQuestionMapper;
    }

    @Override
    public List<QuickQuestionVO> getForFrontend(String pageRoute) {
        return quickQuestionMapper.selectForFrontend(pageRoute, MAX_PER_PAGE);
    }

    @Override
    public List<QuickQuestionVO> listAll(String pageRoute, String status, String category) {
        LambdaQueryWrapper<QuickQuestion> wrapper = new LambdaQueryWrapper<>();
        if (pageRoute != null && !pageRoute.isEmpty()) {
            wrapper.eq(QuickQuestion::getPageRoute, pageRoute);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(QuickQuestion::getStatus, status);
        }
        if (category != null && !category.isEmpty()) {
            wrapper.eq(QuickQuestion::getCategory, category);
        }
        wrapper.orderByDesc(QuickQuestion::getWeight)
               .orderByAsc(QuickQuestion::getSortOrder)
               .orderByDesc(QuickQuestion::getUpdatedAt);

        return quickQuestionMapper.selectList(wrapper).stream().map(q -> {
            QuickQuestionVO vo = new QuickQuestionVO();
            vo.setId(q.getId());
            vo.setCategory(q.getCategory());
            vo.setQuestionText(q.getQuestionText());
            vo.setPageRoute(q.getPageRoute());
            vo.setWeight(q.getWeight());
            vo.setSortOrder(q.getSortOrder());
            vo.setHitCount(q.getHitCount());
            vo.setStatus(q.getStatus());
            return vo;
        }).toList();
    }

    @Override
    public List<ExtractedQuestionVO> getExtracted(int windowDays, int minFrequency) {
        return quickQuestionMapper.selectExtractedFromLogs(
            windowDays > 0 ? windowDays : DEFAULT_WINDOW_DAYS,
            minFrequency > 0 ? minFrequency : DEFAULT_MIN_FREQUENCY
        );
    }

    @Override
    @Transactional
    public QuickQuestionVO create(QuickQuestionCreateDTO dto) {
        QuickQuestion entity = new QuickQuestion();
        entity.setQuestionText(dto.getQuestionText());
        entity.setCategory(dto.getCategory() != null ? dto.getCategory() : "manual_curated");
        entity.setPageRoute(dto.getPageRoute());
        entity.setWeight(dto.getWeight() != null ? dto.getWeight() : 50);
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "enabled");
        entity.setSource("admin_manual");
        entity.setHitCount(0);
        quickQuestionMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public QuickQuestionVO update(Long id, QuickQuestionCreateDTO dto) {
        QuickQuestion entity = quickQuestionMapper.selectById(id);
        if (entity == null) {
            throw new RuntimeException("快捷问题不存在: " + id);
        }
        entity.setQuestionText(dto.getQuestionText());
        if (dto.getCategory() != null) entity.setCategory(dto.getCategory());
        entity.setPageRoute(dto.getPageRoute());
        if (dto.getWeight() != null) entity.setWeight(dto.getWeight());
        if (dto.getSortOrder() != null) entity.setSortOrder(dto.getSortOrder());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        entity.setUpdatedAt(LocalDateTime.now());
        quickQuestionMapper.updateById(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        quickQuestionMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void toggleStatus(Long id, String status) {
        LambdaUpdateWrapper<QuickQuestion> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(QuickQuestion::getId, id)
               .set(QuickQuestion::getStatus, status)
               .set(QuickQuestion::getUpdatedAt, LocalDateTime.now());
        quickQuestionMapper.update(null, wrapper);
    }

    @Override
    @Transactional
    public QuickQuestionVO adoptAiSuggestion(String questionText, String pageRoute, String questionHash) {
        String text = questionText == null ? null : questionText.trim();
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("问题文本不能为空");
        }
        String route = (pageRoute == null || pageRoute.isEmpty()) ? null : pageRoute.trim();

        // 同文本（同页面作用域）已存在 → 转为人工维护并启用
        LambdaQueryWrapper<QuickQuestion> existsWrapper = new LambdaQueryWrapper<QuickQuestion>()
                .eq(QuickQuestion::getQuestionText, text);
        if (route != null) {
            existsWrapper.eq(QuickQuestion::getPageRoute, route);
        } else {
            existsWrapper.isNull(QuickQuestion::getPageRoute);
        }
        QuickQuestion exists = quickQuestionMapper.selectOne(existsWrapper);
        if (exists != null) {
            exists.setCategory("manual_curated");
            exists.setSource("admin_manual");
            exists.setStatus("enabled");
            exists.setUpdatedAt(LocalDateTime.now());
            quickQuestionMapper.updateById(exists);
            return toVO(exists);
        }

        // 不存在 → 创建人工维护记录
        QuickQuestion entity = new QuickQuestion();
        entity.setQuestionText(text);
        entity.setPageRoute(route);
        entity.setCategory("manual_curated");
        entity.setSource("admin_manual");
        entity.setStatus("enabled");
        entity.setWeight(50);
        entity.setSortOrder(0);
        entity.setHitCount(0);
        quickQuestionMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public void recordClick(Long id) {
        quickQuestionMapper.incrementHitCount(id);
    }

    private QuickQuestionVO toVO(QuickQuestion entity) {
        QuickQuestionVO vo = new QuickQuestionVO();
        vo.setId(entity.getId());
        vo.setCategory(entity.getCategory());
        vo.setQuestionText(entity.getQuestionText());
        vo.setPageRoute(entity.getPageRoute());
        vo.setWeight(entity.getWeight());
        vo.setSortOrder(entity.getSortOrder());
        vo.setHitCount(entity.getHitCount());
        vo.setStatus(entity.getStatus());
        return vo;
    }
}
