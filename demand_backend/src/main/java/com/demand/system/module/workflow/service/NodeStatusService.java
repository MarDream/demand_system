package com.demand.system.module.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.requirement.dto.SortRequest;
import com.demand.system.module.workflow.entity.NodeStatus;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.mapper.NodeStatusMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NodeStatusService {

    /**
     * 乱码来源编码：中文以 UTF-8 写入却被按单字节编码读取时会产生这两种乱码。
     * ISO-8859-1 为 JDK 规范保证的字符集，始终可用；
     * windows-1252 非规范保证（精简运行时可能缺失），故先探测、缺失时降级，避免类初始化失败。
     */
    private static final List<Charset> MOJIBAKE_SOURCE_CHARSETS = Charset.isSupported("windows-1252")
            ? List.of(StandardCharsets.ISO_8859_1, Charset.forName("windows-1252"))
            : List.of(StandardCharsets.ISO_8859_1);

    private final NodeStatusMapper nodeStatusMapper;
    private final WorkflowNodeMapper workflowNodeMapper;

    public NodeStatusService(NodeStatusMapper nodeStatusMapper, WorkflowNodeMapper workflowNodeMapper) {
        this.nodeStatusMapper = nodeStatusMapper;
        this.workflowNodeMapper = workflowNodeMapper;
    }

    public List<NodeStatus> list() {
        return nodeStatusMapper.selectList(
            new LambdaQueryWrapper<NodeStatus>().orderByAsc(NodeStatus::getSortOrder)
        );
    }

    @Transactional
    public int repairGarbledNames() {
        List<NodeStatus> statuses = nodeStatusMapper.selectList(null);
        int repaired = 0;
        for (NodeStatus status : statuses) {
            String name = status.getName();
            if (!isGarbledName(name)) {
                continue;
            }
            String recovered = recoverName(name);
            if (!recovered.equals(name) && !isGarbledName(recovered)) {
                status.setName(recovered);
                nodeStatusMapper.updateById(status);
                repaired++;
            }
        }
        return repaired;
    }

    /**
     * 动态还原乱码名称：按可能被误解的源编码取回原始字节，再以 UTF-8 重新解码。
     * 不依赖任何固定的状态名对照表；还原后仍判定为乱码则放弃，避免写入脏数据。
     */
    private String recoverName(String garbled) {
        for (Charset source : MOJIBAKE_SOURCE_CHARSETS) {
            String decoded = new String(garbled.getBytes(source), StandardCharsets.UTF_8);
            if (!decoded.equals(garbled) && !isGarbledName(decoded)) {
                return decoded;
            }
        }
        return garbled;
    }

    @Transactional
    public void create(NodeStatus nodeStatus) {
        validateCodeUnique(nodeStatus.getCode(), null);
        if (nodeStatus.getSortOrder() == null) {
            nodeStatus.setSortOrder(nextSortOrder());
        }
        nodeStatusMapper.insert(nodeStatus);
    }

    @Transactional
    public void update(NodeStatus nodeStatus) {
        validateCodeUnique(nodeStatus.getCode(), nodeStatus.getId());
        nodeStatusMapper.updateById(nodeStatus);
    }

    @Transactional
    public void delete(Long id) {
        NodeStatus existing = nodeStatusMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "节点状态不存在");
        }
        validateNotUsedByWorkflow(existing.getCode());
        nodeStatusMapper.deleteById(id);
    }

    @Transactional
    public List<NodeStatus> sort(List<SortRequest> sortRequests) {
        if (CollectionUtils.isEmpty(sortRequests)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "排序列表不能为空");
        }
        List<Long> ids = sortRequests.stream().map(SortRequest::getId).toList();
        List<NodeStatus> statuses = nodeStatusMapper.selectBatchIds(ids);
        if (statuses.size() != sortRequests.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "部分节点状态不存在");
        }
        Map<Long, Integer> sortOrderMap = sortRequests.stream()
                .collect(Collectors.toMap(SortRequest::getId, SortRequest::getSortOrder));
        statuses.forEach(status ->
            nodeStatusMapper.update(null, new LambdaUpdateWrapper<NodeStatus>()
                .eq(NodeStatus::getId, status.getId())
                .set(NodeStatus::getSortOrder, sortOrderMap.get(status.getId())))
        );
        return list();
    }

    private void validateCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<NodeStatus> wrapper = new LambdaQueryWrapper<NodeStatus>()
            .eq(NodeStatus::getCode, code);
        if (excludeId != null) {
            wrapper.ne(NodeStatus::getId, excludeId);
        }
        if (nodeStatusMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态编码已存在: " + code);
        }
    }

    private Integer nextSortOrder() {
        NodeStatus last = nodeStatusMapper.selectOne(
                new LambdaQueryWrapper<NodeStatus>()
                        .orderByDesc(NodeStatus::getSortOrder)
                        .last("LIMIT 1")
        );
        return last == null || last.getSortOrder() == null ? 0 : last.getSortOrder() + 1;
    }

    private boolean isGarbledName(String name) {
        if (!StringUtils.hasText(name)) {
            return true;
        }
        boolean hasChinese = name.codePoints().anyMatch(codePoint ->
                codePoint >= 0x4E00 && codePoint <= 0x9FFF
        );
        if (hasChinese) {
            return false;
        }
        return name.indexOf('Ã') >= 0
                || name.indexOf('Â') >= 0
                || name.indexOf('æ') >= 0
                || name.indexOf('å') >= 0
                || name.indexOf('ä') >= 0
                || name.indexOf('ç') >= 0
                || name.indexOf('è') >= 0
                || name.indexOf('é') >= 0
                || name.indexOf('œ') >= 0
                || name.indexOf('º') >= 0
                || name.indexOf('»') >= 0
                || name.indexOf('¼') >= 0
                || name.indexOf('½') >= 0
                || name.indexOf('¾') >= 0
                || name.indexOf('¿') >= 0;
    }

    private void validateNotUsedByWorkflow(String statusCode) {
        long count = workflowNodeMapper.selectCount(
                new LambdaQueryWrapper<WorkflowNode>()
                        .apply(
                                "JSON_UNQUOTE(JSON_EXTRACT(properties, '$.nodeStatusCode')) = {0} " +
                                        "OR JSON_UNQUOTE(JSON_EXTRACT(properties, '$.statusCode')) = {0}",
                                statusCode
                        )
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该节点状态已被工作流节点引用，无法删除");
        }
    }
}
