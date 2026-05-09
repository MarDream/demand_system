# 附件存储与 RAG 扩展设计

## 目标

需求附件当前通过 `file` 模块上传到 MinIO，并在需求表的 `attachments` JSON 中保留轻量元数据。为了支持后续附件内容检索、向量化召回和 RAG 知识库，需要把“业务引用”和“文件资产”拆成两层。

## 当前设计

1. 二进制文件存储
   `MinioStorageService` 负责把附件对象写入 MinIO，`file_records` 保存对象名、bucket、大小、MIME、上传人等基础元数据。
2. 需求侧引用
   `requirements.attachments` 保存附件引用快照，字段建议至少包含：
   `fileId`、`name`、`url`、`size`、`contentType`、`bucketName`、`objectName`
3. 前端展示
   需求新建/编辑页面使用上传返回的结构化对象直接展示和提交，避免只靠 URL 反推文件身份。

## 为什么这样拆

1. MinIO 适合做对象存储，但不适合承担业务索引和检索状态。
2. `file_records` 可以稳定承接后续异步处理链路，比如文本抽取、OCR、向量化、分片、审核。
3. `requirements.attachments` 保留快照后，即使附件后续被迁移 bucket 或增加签名 URL，也不影响历史需求内容回放。

## 推荐的后续扩展表

1. `file_parse_tasks`
   用于记录文件解析任务，字段建议：`file_id`、`status`、`parser_type`、`error_message`、`started_at`、`finished_at`
2. `file_chunks`
   用于保存切片结果，字段建议：`id`、`file_id`、`chunk_index`、`content_text`、`token_count`、`metadata_json`
3. `file_embeddings`
   用于保存向量索引引用，字段建议：`chunk_id`、`embedding_model`、`vector_id`、`index_name`、`created_at`
4. `knowledge_documents`
   用于沉淀可被 RAG 消费的文档实体，字段建议：`source_type`、`source_id`、`title`、`summary`、`visibility_scope`

## 建议处理链路

1. 用户上传附件到 MinIO，返回 `fileId + objectName`
2. 需求保存时把附件引用写入 `requirements.attachments`
3. 后端异步投递“文件解析任务”到 MQ
4. 解析服务按 MIME 类型做文本抽取
5. 抽取文本按块切分，写入 `file_chunks`
6. 向量化服务为分片生成 embedding，并写入向量库
7. RAG 查询时，先按权限过滤业务文档，再做向量召回与重排

## 技术建议

1. 对外下载不要长期暴露裸 MinIO URL，建议逐步切到短期预签名 URL。
2. 大文件解析应走异步任务，不要绑定在上传接口里。
3. 向量库建议独立于 MySQL；如果后续规模不大，可先用 Elasticsearch 向量检索或专门的向量数据库。
4. 权限设计要前置，RAG 召回结果必须继承项目、需求、角色范围。

## 与当前代码的衔接点

1. `demand_backend/src/main/java/com/demand/system/module/file`
   负责 MinIO 上传与文件主记录
2. `demand_backend/src/main/java/com/demand/system/module/requirement/dto/RequirementAttachmentDTO.java`
   负责需求附件引用结构
3. `requirements.attachments`
   适合作为业务引用快照，不建议直接承载全文解析结果
