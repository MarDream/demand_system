package com.demand.system.module.knowledge.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementContentParserTest {
    private final RequirementContentParser parser = new RequirementContentParser();

    @Test
    void parsesRichTextStructureAndImageAltText() {
        List<String> chunks = parser.parse(
                "DEM-1024",
                "登录失败排查",
                "<h2>现象</h2><p>用户无法登录</p><ul><li>提示密码错误</li></ul>"
                        + "<table><tr><th>环境</th><th>结果</th></tr><tr><td>生产</td><td>失败</td></tr></table>"
                        + "<img src='/api/v1/files/1/preview' alt='错误截图'>"
        );

        assertFalse(chunks.isEmpty());
        String content = String.join("\n", chunks);
        assertTrue(content.contains("工单编号：DEM-1024"));
        assertTrue(content.contains("工单名称：登录失败排查"));
        assertTrue(content.contains("用户无法登录"));
        assertTrue(content.contains("提示密码错误"));
        assertTrue(content.contains("生产"));
        assertTrue(content.contains("[图片：错误截图]"));
    }

    @Test
    void extractsOnlyInternalImageFileReferences() {
        List<RequirementContentParser.RequirementImageReference> images = parser.extractImageReferences(
                "<img src=\"/api/v1/files/42/preview\" alt=\"错误截图\">"
                        + "<img src=\"https://example.com/image.png\" alt=\"外链\">"
                        + "<img src=\"/api/v1/files/43/preview-url?token=x\" alt=\"流程图\">"
        );

        assertEquals(3, images.size());
        assertEquals(1, images.get(0).position());
        assertEquals(42L, images.get(0).fileId());
        assertEquals(43L, images.get(2).fileId());
        assertEquals("外链", images.get(1).alt());
        assertEquals(null, images.get(1).fileId());
    }

    @Test
    void keepsMetadataWhenBodyIsEmpty() {
        List<String> chunks = parser.parse("DEM-1", "空正文工单", "");
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("空正文工单"));
    }
}
