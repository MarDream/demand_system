package com.demand.system.module.bitable.query;

/**
 * 筛选规则树节点（递归）。
 * 支持两类节点：
 * <ul>
 *   <li>{@link FilterGroupNode}：逻辑组（and/or），包含若干子节点</li>
 *   <li>{@link FilterPredicateNode}：叶子谓词（字段级单条筛选规则）</li>
 * </ul>
 *
 * <p>该模型同时供应用层内存筛选与后续 SQL 下推复用，避免两套规则解析逻辑。</p>
 */
public sealed interface FilterNode permits FilterGroupNode, FilterPredicateNode {

}