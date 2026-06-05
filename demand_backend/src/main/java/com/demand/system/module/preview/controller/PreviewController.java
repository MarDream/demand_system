package com.demand.system.module.preview.controller;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.Result;
import com.demand.system.module.preview.PreviewService;
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
     * 生成外部文件预览服务的访问 URL。
     *
     * <p>前端只关心传入的原始 fileUrl（http/https），返回的是可直接嵌入 iframe 的完整预览地址。
     * 具体预览服务实现（如 kkFileView、OnlyOffice 等）的协议细节由后端封装。</p>
     */
    @GetMapping("/office")
    public Result<PreviewUrlVO> buildOfficePreviewUrl(@RequestParam("fileUrl") String fileUrl,
                                                      @RequestParam(value = "watermarkTxt", required = false) String watermarkTxt) {
        try {
            String previewUrl = previewService.buildPreviewUrl(fileUrl, watermarkTxt);
            return Result.success(new PreviewUrlVO(previewUrl));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, e.getMessage());
        } catch (IllegalStateException e) {
            throw new BusinessException(500, e.getMessage());
        }
    }
}
