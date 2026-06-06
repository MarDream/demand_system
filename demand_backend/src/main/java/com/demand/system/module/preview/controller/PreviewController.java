package com.demand.system.module.preview.controller;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.Result;
import com.demand.system.module.preview.PreviewService;
import com.demand.system.module.preview.dto.AsyncPreviewVO;
import com.demand.system.module.preview.dto.PreviewUrlVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/preview")
public class PreviewController {

    private final PreviewService previewService;

    public PreviewController(PreviewService previewService) {
        this.previewService = previewService;
    }

    /**
     * 生成外部文件预览服务的访问 URL（同步模式）。
     *
     * <p>支持两种入参模式：
     * <ul>
     *   <li>知识库文档：{@code knowledgeBaseId + documentId} —— 服务端按需签发长有效期 MinIO URL</li>
     *   <li>外部 URL：{@code fileUrl} —— 直接使用传入的 URL（兜底场景，如分享页临时签名）</li>
     * </ul>
     * 两种模式二选一；同时传或都不传都会 400。</p>
     */
    @GetMapping("/office")
    public Result<PreviewUrlVO> buildOfficePreviewUrl(@RequestParam(value = "knowledgeBaseId", required = false) Long knowledgeBaseId,
                                                      @RequestParam(value = "documentId", required = false) Long documentId,
                                                      @RequestParam(value = "fileUrl", required = false) String fileUrl,
                                                      @RequestParam(value = "watermarkTxt", required = false) String watermarkTxt) {
        try {
            String previewUrl = resolveAndBuild(knowledgeBaseId, documentId, fileUrl, watermarkTxt);
            return Result.success(new PreviewUrlVO(previewUrl));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, e.getMessage());
        } catch (IllegalStateException e) {
            throw new BusinessException(500, e.getMessage());
        }
    }

    /**
     * 异步预览：提交转换任务，<strong>不阻塞</strong>等待完成。
     *
     * <p>该方法只负责"提交"环节：调 kkFileView 的 /onlinePreview 拿到 taskId
     * （小文件可能直接返回 completed previewUrl）。前端在 status=processing 时
     * 调用 {@link #pollOfficeStatus} 轮询状态。</p>
     *
     * <p>这种拆分解决了大文件转码超过前端 axios 15s 默认超时的痛点：
     * 单次 HTTP 调用都在 1s 量级，不会被前端 cancel。</p>
     */
    @GetMapping("/office-submit")
    public Result<AsyncPreviewVO> submitOfficePreview(@RequestParam(value = "knowledgeBaseId", required = false) Long knowledgeBaseId,
                                                     @RequestParam(value = "documentId", required = false) Long documentId,
                                                     @RequestParam(value = "fileUrl", required = false) String fileUrl,
                                                     @RequestParam(value = "watermarkTxt", required = false) String watermarkTxt) {
        try {
            AsyncPreviewVO result;
            if (knowledgeBaseId != null && documentId != null) {
                result = previewService.submitAsyncPreview(knowledgeBaseId, documentId, watermarkTxt);
            } else if (fileUrl != null) {
                result = previewService.submitAsyncPreview(fileUrl, watermarkTxt);
            } else {
                throw new IllegalArgumentException("knowledgeBaseId+documentId 或 fileUrl 必须二选一");
            }
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, e.getMessage());
        } catch (IllegalStateException e) {
            throw new BusinessException(500, e.getMessage());
        }
    }

    /**
     * 异步预览：轮询任务状态。
     *
     * <p>前端在 {@code office-submit} 返回 {@code status=processing} 后周期性调用本接口。
     * 返回 {@code status=completed} 时 {@code previewUrl} 可直接嵌入 iframe。</p>
     */
    @GetMapping("/office-status")
    public Result<AsyncPreviewVO> pollOfficeStatus(@RequestParam("taskId") String taskId,
                                                   @RequestParam(value = "previewUrl", required = false) String previewUrl) {
        try {
            AsyncPreviewVO result = previewService.pollAsyncPreviewStatus(taskId, previewUrl);
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, e.getMessage());
        } catch (IllegalStateException e) {
            throw new BusinessException(500, e.getMessage());
        }
    }

    /**
     * 同步模式异步预览（兼容保留）：阻塞等待 kkFileView 转码完成，最多约 60 秒。
     *
     * <p>供后端批处理、内部测试或不愿走轮询的调用方使用；前端请走
     * {@code /office-submit} + {@code /office-status}。</p>
     */
    @GetMapping("/office-async")
    public Result<AsyncPreviewVO> buildOfficePreviewUrlAsync(@RequestParam(value = "knowledgeBaseId", required = false) Long knowledgeBaseId,
                                                             @RequestParam(value = "documentId", required = false) Long documentId,
                                                             @RequestParam(value = "fileUrl", required = false) String fileUrl,
                                                             @RequestParam(value = "watermarkTxt", required = false) String watermarkTxt) {
        try {
            AsyncPreviewVO result;
            if (knowledgeBaseId != null && documentId != null) {
                result = previewService.buildAsyncPreviewUrl(knowledgeBaseId, documentId, watermarkTxt);
            } else if (fileUrl != null) {
                result = previewService.buildAsyncPreviewUrl(fileUrl, watermarkTxt);
            } else {
                throw new IllegalArgumentException("knowledgeBaseId+documentId 或 fileUrl 必须二选一");
            }
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, e.getMessage());
        } catch (IllegalStateException e) {
            throw new BusinessException(500, e.getMessage());
        }
    }

    private String resolveAndBuild(Long knowledgeBaseId, Long documentId, String fileUrl, String watermarkTxt) {
        if (knowledgeBaseId != null && documentId != null) {
            return previewService.buildPreviewUrl(knowledgeBaseId, documentId, watermarkTxt);
        }
        if (fileUrl != null) {
            return previewService.buildPreviewUrl(fileUrl, watermarkTxt);
        }
        throw new IllegalArgumentException("knowledgeBaseId+documentId 或 fileUrl 必须二选一");
    }
}
