package com.demand.system.module.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.knowledge.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    @Select("<script>"
            + "SELECT kc.* FROM knowledge_chunks kc "
            + "JOIN knowledge_documents kd ON kd.id = kc.document_id AND kd.deleted_at = 0 "
            + "WHERE kd.source_type = 'requirement_body' "
            + "AND kd.status = 'indexed' "
            + "AND (kc.content LIKE CONCAT('%', #{query}, '%') "
            + "OR kc.section_title LIKE CONCAT('%', #{query}, '%') "
            + "<foreach collection='terms' item='term'>"
            + " OR kc.content LIKE CONCAT('%', #{term}, '%')"
            + " OR kc.section_title LIKE CONCAT('%', #{term}, '%')"
            + "</foreach>) "
            + "ORDER BY kc.id DESC LIMIT #{limit}"
            + "</script>")
    List<KnowledgeChunk> searchRequirementBodyChunks(@Param("query") String query,
                                                       @Param("terms") List<String> terms,
                                                       @Param("limit") int limit);

    @Select("<script>SELECT * FROM knowledge_chunks WHERE vector_id IN "
            + "<foreach collection='vectorIds' item='vectorId' open='(' separator=',' close=')'>"
            + "#{vectorId}</foreach></script>")
    List<KnowledgeChunk> selectByVectorIds(@Param("vectorIds") List<String> vectorIds);
}
