package com.demand.system.module.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.assistant.entity.AssistantMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AssistantMessageMapper extends BaseMapper<AssistantMessage> {

    /**
     * 批量查询每个会话的最新一条消息，避免会话列表逐会话查询（N+1）。
     */
    @Select("""
            <script>
            SELECT m.* FROM assistant_messages m
            JOIN (
                SELECT session_id, MAX(id) AS max_id
                FROM assistant_messages
                WHERE deleted_at = 0 AND session_id IN
                <foreach collection="sessionIds" item="sid" open="(" separator="," close=")">
                    #{sid}
                </foreach>
                GROUP BY session_id
            ) t ON m.id = t.max_id
            </script>
            """)
    List<AssistantMessage> selectLatestBySessionIds(@Param("sessionIds") List<Long> sessionIds);
}
