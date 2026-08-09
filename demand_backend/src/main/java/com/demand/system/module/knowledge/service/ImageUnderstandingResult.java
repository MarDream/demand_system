package com.demand.system.module.knowledge.service;

/** 图片 OCR 和视觉描述结果。 */
public record ImageUnderstandingResult(String ocrText, String caption) {
    public boolean hasContent() {
        return (ocrText != null && !ocrText.isBlank()) || (caption != null && !caption.isBlank());
    }

    public static ImageUnderstandingResult empty() {
        return new ImageUnderstandingResult("", "");
    }
}
