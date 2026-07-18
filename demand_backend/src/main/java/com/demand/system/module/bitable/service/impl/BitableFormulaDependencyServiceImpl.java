package com.demand.system.module.bitable.service.impl;

import com.demand.system.module.bitable.entity.BitableFormulaDependency;
import com.demand.system.module.bitable.mapper.BitableFieldMapper;
import com.demand.system.module.bitable.mapper.BitableFormulaDependencyMapper;
import com.demand.system.module.bitable.service.BitableFormulaDependencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多维表格-公式依赖关系 Service 实现
 */
@Service
public class BitableFormulaDependencyServiceImpl implements BitableFormulaDependencyService {

    private static final Logger log = LoggerFactory.getLogger(BitableFormulaDependencyServiceImpl.class);

    /**
     * 匹配 {字段名} 模式的正则
     */
    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("\\{([^}]+)}");

    /**
     * 匹配 f+fieldId 模式的正则（如 fld123 或 f123）
     */
    private static final Pattern FIELD_ID_PATTERN = Pattern.compile("f(?:ld)?(\\d+)");

    private final BitableFormulaDependencyMapper dependencyMapper;
    private final BitableFieldMapper fieldMapper;

    public BitableFormulaDependencyServiceImpl(BitableFormulaDependencyMapper dependencyMapper,
                                                BitableFieldMapper fieldMapper) {
        this.dependencyMapper = dependencyMapper;
        this.fieldMapper = fieldMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDependencies(Long formulaFieldId, String formulaExpr) {
        // 先删除旧的依赖
        dependencyMapper.deleteByFormulaFieldId(formulaFieldId);

        if (formulaExpr == null || formulaExpr.isBlank()) {
            return;
        }

        // 解析公式中的字段引用
        Set<Long> referencedFieldIds = parseFieldReferences(formulaExpr);
        if (referencedFieldIds.isEmpty()) {
            return;
        }

        // 批量插入新依赖
        List<BitableFormulaDependency> dependencies = new ArrayList<>();
        for (Long depId : referencedFieldIds) {
            // 排除自引用（公式引用自身）
            if (depId.equals(formulaFieldId)) {
                continue;
            }
            BitableFormulaDependency dep = new BitableFormulaDependency();
            dep.setFormulaFieldId(formulaFieldId);
            dep.setDependencyFieldId(depId);
            dep.setDependencyKind("direct");
            dependencies.add(dep);
        }

        if (!dependencies.isEmpty()) {
            dependencyMapper.batchInsert(dependencies);
        }
    }

    @Override
    public List<Long> detectCycle(Long formulaFieldId) {
        // 从数据库加载所有依赖关系构建邻接表
        List<BitableFormulaDependency> allDeps = dependencyMapper.selectAllDependencies();
        Map<Long, List<Long>> adj = buildAdjacencyList(allDeps);

        // DFS 检测从 formulaFieldId 出发是否能回到自身
        Set<Long> visited = new HashSet<>();
        Set<Long> onStack = new HashSet<>();
        Map<Long, Long> parent = new HashMap<>();
        List<Long> cycle = new ArrayList<>();

        if (dfs(formulaFieldId, adj, visited, onStack, parent, cycle)) {
            return cycle;
        }
        return null;
    }

    @Override
    public List<Long> getDependentFormulaFieldIds(Long fieldId) {
        List<Long> directDependents = dependencyMapper.selectFormulaFieldIdsByDependencyFieldId(fieldId);
        if (directDependents.isEmpty()) {
            return Collections.emptyList();
        }

        // 级联查找：从直接依赖出发，沿 formula_field_id -> dependency_field_id 反向继续查找
        Set<Long> result = new LinkedHashSet<>(directDependents);
        Queue<Long> queue = new LinkedList<>(directDependents);

        while (!queue.isEmpty()) {
            Long currentFormulaFieldId = queue.poll();
            List<Long> nextLevel = dependencyMapper.selectFormulaFieldIdsByDependencyFieldId(currentFormulaFieldId);
            for (Long next : nextLevel) {
                if (result.add(next)) {
                    queue.add(next);
                }
            }
        }

        return new ArrayList<>(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDependencies(Long formulaFieldId) {
        dependencyMapper.deleteByFormulaFieldId(formulaFieldId);
    }

    /**
     * 解析公式表达式中的字段引用
     * 支持两种模式：{字段名} 和 f+fieldId（如 f123 或 fld123）
     *
     * @param formulaExpr 公式表达式
     * @return 被引用的字段ID集合
     */
    private Set<Long> parseFieldReferences(String formulaExpr) {
        Set<Long> fieldIds = new HashSet<>();

        // 1. 解析 {字段名} 模式 -> 需要查数据库转换字段名为ID
        Matcher nameMatcher = FIELD_NAME_PATTERN.matcher(formulaExpr);
        Set<String> fieldNames = new HashSet<>();
        while (nameMatcher.find()) {
            String name = nameMatcher.group(1).trim();
            if (!name.isEmpty()) {
                fieldNames.add(name);
            }
        }

        // 2. 解析 f+fieldId / fld+fieldId 模式 -> 直接提取ID
        Matcher idMatcher = FIELD_ID_PATTERN.matcher(formulaExpr);
        while (idMatcher.find()) {
            try {
                fieldIds.add(Long.parseLong(idMatcher.group(1)));
            } catch (NumberFormatException e) {
                log.warn("解析字段ID失败: {}", idMatcher.group(0));
            }
        }

        // 3. 将字段名转为字段ID（需查数据库）
        // 字段名无法在当前方法中直接转换为ID，因为没有tableId上下文
        // 字段名引用会在 evaluateFormula 时通过 fieldValues 映射解析
        // 这里只记录 f+fieldId 模式的直接依赖
        // 对于 {字段名} 模式，依赖在公式求值时动态解析

        return fieldIds;
    }

    /**
     * 构建邻接表：formulaFieldId -> [dependencyFieldId, ...]
     */
    private Map<Long, List<Long>> buildAdjacencyList(List<BitableFormulaDependency> allDeps) {
        Map<Long, List<Long>> adj = new HashMap<>();
        for (BitableFormulaDependency dep : allDeps) {
            adj.computeIfAbsent(dep.getFormulaFieldId(), k -> new ArrayList<>())
                    .add(dep.getDependencyFieldId());
        }
        return adj;
    }

    /**
     * DFS 检测有向图中的环
     * 如果从 start 节点出发经过依赖链能回到 start，则存在循环引用
     *
     * @param node     当前节点
     * @param adj      邻接表
     * @param visited  已访问节点（全局）
     * @param onStack  当前递归栈上的节点
     * @param parent   记录路径
     * @param cycle    输出循环链路
     * @return 是否检测到循环
     */
    private boolean dfs(Long node, Map<Long, List<Long>> adj,
                        Set<Long> visited, Set<Long> onStack,
                        Map<Long, Long> parent, List<Long> cycle) {
        visited.add(node);
        onStack.add(node);

        List<Long> neighbors = adj.getOrDefault(node, Collections.emptyList());
        for (Long neighbor : neighbors) {
            if (!visited.contains(neighbor)) {
                parent.put(neighbor, node);
                if (dfs(neighbor, adj, visited, onStack, parent, cycle)) {
                    return true;
                }
            } else if (onStack.contains(neighbor)) {
                // 发现环，回溯构建循环链路
                buildCyclePath(node, neighbor, parent, cycle);
                return true;
            }
        }

        onStack.remove(node);
        return false;
    }

    /**
     * 构建循环路径
     *
     * @param from    环的末端节点
     * @param to      环的起始节点（与 from 形成环）
     * @param parent  父节点映射
     * @param cycle   输出循环链路
     */
    private void buildCyclePath(Long from, Long to, Map<Long, Long> parent, List<Long> cycle) {
        // 从 from 回溯到 to
        LinkedList<Long> path = new LinkedList<>();
        Long current = from;
        while (current != null && !current.equals(to)) {
            path.addFirst(current);
            current = parent.get(current);
        }
        path.addFirst(to);
        // 闭合环路：再添加 to 表示回到起点
        path.addLast(to);
        cycle.addAll(path);
    }
}
