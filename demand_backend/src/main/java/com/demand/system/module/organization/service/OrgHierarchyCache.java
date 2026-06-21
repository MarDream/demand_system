package com.demand.system.module.organization.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 组织层级缓存服务
 * 用于缓存组织的子孙节点查询，提升权限过滤性能
 */
@Service
public class OrgHierarchyCache {

    private static final Logger log = LoggerFactory.getLogger(OrgHierarchyCache.class);

    private final SysOrgService sysOrgService;

    /**
     * 组织子孙节点缓存
     * Key: 组织ID
     * Value: 包含该组织及其所有子孙组织的ID列表
     */
    private final LoadingCache<Long, List<Long>> descendantsCache;

    public OrgHierarchyCache(SysOrgService sysOrgService) {
        this.sysOrgService = sysOrgService;
        this.descendantsCache = Caffeine.newBuilder()
                .maximumSize(10000) // 最多缓存10000个组织的子孙数据
                .expireAfterWrite(Duration.ofMinutes(30)) // 写入后30分钟过期
                .recordStats() // 记录统计信息
                .build(this::loadDescendants);
    }

    /**
     * 获取组织的所有子孙节点ID（包含自身）
     *
     * @param orgId 组织ID
     * @return 包含该组织及其所有子孙组织的ID列表
     */
    public List<Long> getDescendantsWithSelf(Long orgId) {
        if (orgId == null) {
            return new ArrayList<>();
        }
        try {
            List<Long> descendants = descendantsCache.get(orgId);
            if (descendants == null) {
                return new ArrayList<>();
            }
            // 返回包含自身的列表
            List<Long> result = new ArrayList<>(descendants);
            if (!result.contains(orgId)) {
                result.add(0, orgId);
            }
            return result;
        } catch (Exception e) {
            log.error("获取组织子孙节点失败: orgId={}", orgId, e);
            // 降级：至少返回自身
            List<Long> fallback = new ArrayList<>();
            fallback.add(orgId);
            return fallback;
        }
    }

    /**
     * 批量获取多个组织的子孙节点（去重）
     *
     * @param orgIds 组织ID列表
     * @return 所有组织的子孙节点ID集合（去重）
     */
    public List<Long> getDescendantsBatch(List<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> allDescendants = new ArrayList<>();
        for (Long orgId : orgIds) {
            if (orgId != null) {
                allDescendants.addAll(getDescendantsWithSelf(orgId));
            }
        }

        // 去重
        return allDescendants.stream().distinct().toList();
    }

    /**
     * 加载组织的子孙节点（实际查询逻辑）
     */
    private List<Long> loadDescendants(Long orgId) {
        try {
            List<Long> descendants = sysOrgService.getDescendantIds(orgId);
            log.debug("加载组织子孙节点: orgId={}, count={}", orgId, descendants.size());
            return descendants;
        } catch (Exception e) {
            log.error("加载组织子孙节点失败: orgId={}", orgId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 手动刷新指定组织的缓存
     */
    public void refresh(Long orgId) {
        if (orgId != null) {
            descendantsCache.refresh(orgId);
            log.info("刷新组织缓存: orgId={}", orgId);
        }
    }

    /**
     * 手动使指定组织的缓存失效
     */
    public void invalidate(Long orgId) {
        if (orgId != null) {
            descendantsCache.invalidate(orgId);
            log.info("使组织缓存失效: orgId={}", orgId);
        }
    }

    /**
     * 清空所有缓存
     * 在组织结构发生变化时调用（如：新增、删除、移动组织）
     */
    @CacheEvict(allEntries = true)
    public void invalidateAll() {
        descendantsCache.invalidateAll();
        log.info("清空所有组织缓存");
    }

    /**
     * 获取缓存统计信息
     */
    public String getCacheStats() {
        var stats = descendantsCache.stats();
        return String.format(
            "缓存统计 - 命中率: %.2f%%, 命中次数: %d, 未命中次数: %d, 加载成功: %d, 加载失败: %d, 当前大小: %d",
            stats.hitRate() * 100,
            stats.hitCount(),
            stats.missCount(),
            stats.loadSuccessCount(),
            stats.loadFailureCount(),
            descendantsCache.estimatedSize()
        );
    }

    /**
     * 预热缓存：加载常用组织的子孙节点
     * 建议在应用启动时调用
     */
    public void warmUp(List<Long> topOrgIds) {
        if (topOrgIds == null || topOrgIds.isEmpty()) {
            return;
        }
        log.info("开始预热组织缓存: {}", topOrgIds.size());
        int successCount = 0;
        for (Long orgId : topOrgIds) {
            try {
                descendantsCache.get(orgId);
                successCount++;
            } catch (Exception e) {
                log.warn("预热组织缓存失败: orgId={}", orgId, e);
            }
        }
        log.info("组织缓存预热完成: 成功={}/{}", successCount, topOrgIds.size());
    }
}
