package com.demand.system.module.knowledge.service;

/**
 * 正文图片理解扩展点。实现可以接入 OCR 服务、视觉大模型或企业内部图像服务。
 * 未配置实现时应返回空结果，不能阻塞工单保存和正文索引。
 */
public interface ImageUnderstandingService {
    ImageUnderstandingResult analyze(byte[] imageBytes, String contentType, String altText);

    default boolean enabled() {
        return true;
    }

    /** 未启用时用于索引日志和管理提示的原因。 */
    default String unavailableReason() {
        return "图片理解服务未配置";
    }
}
