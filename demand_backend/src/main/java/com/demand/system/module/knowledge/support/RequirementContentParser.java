package com.demand.system.module.knowledge.support;

import org.springframework.stereotype.Component;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将工单富文本正文转换为适合检索和向量化的纯文本片段，并提取正文中的内部图片引用。
 */
@Component
public class RequirementContentParser {
    private static final int MAX_CHUNK_LENGTH = 1200;
    private static final int CHUNK_OVERLAP = 120;
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
            "(?i)/api/v1/files/(\\d+)(?:/(?:preview|preview-url))?(?:[/?#].*)?$");

    public List<String> parse(String requirementNo, String title, String html) {
        StringBuilder searchableText = new StringBuilder();
        appendLine(searchableText, "工单编号：" + valueOrEmpty(requirementNo));
        appendLine(searchableText, "工单名称：" + valueOrEmpty(title));

        String body = extractPlainText(html);
        if (!body.isBlank()) {
            searchableText.append('\n').append(body);
        }
        return split(searchableText.toString());
    }

    /** 提取正文中的图片引用。只返回本系统文件预览路径，不抓取任意外链，避免 SSRF。 */
    public List<RequirementImageReference> extractImageReferences(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<RequirementImageReference> references = new ArrayList<>();
        try {
            new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
                @Override
                public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                    collectImage(tag, attributes, position);
                }

                @Override
                public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                    collectImage(tag, attributes, position);
                }

                private void collectImage(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                    if (tag != HTML.Tag.IMG) {
                        return;
                    }
                    Object srcValue = attributes.getAttribute(HTML.Attribute.SRC);
                    if (srcValue == null || srcValue.toString().isBlank()) {
                        return;
                    }
                    String src = srcValue.toString().trim();
                    Long fileId = parseInternalFileId(src);
                    Object altValue = attributes.getAttribute(HTML.Attribute.ALT);
                    String alt = altValue == null ? "" : altValue.toString().trim();
                    references.add(new RequirementImageReference(references.size() + 1, src, fileId, alt, position));
                }
            }, true);
        } catch (Exception e) {
            // 正文解析失败不应阻塞工单保存或正文索引；图片分析按 best effort 处理。
        }
        return List.copyOf(references);
    }

    Long parseInternalFileId(String src) {
        try {
            String path = src;
            if (src.startsWith("http://") || src.startsWith("https://")) {
                path = URI.create(src).getPath();
            }
            Matcher matcher = FILE_PATH_PATTERN.matcher(path == null ? "" : path);
            return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    String extractPlainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        StringBuilder output = new StringBuilder();
        try {
            new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
                private boolean ignored;

                @Override
                public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                    if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) {
                        ignored = true;
                        return;
                    }
                    if (tag == HTML.Tag.LI) {
                        appendBreak(output);
                        output.append("- ");
                    } else if (tag == HTML.Tag.TD || tag == HTML.Tag.TH) {
                        if (!output.isEmpty() && output.charAt(output.length() - 1) != '\n') {
                            output.append(" | ");
                        }
                    } else if (isBlockTag(tag)) {
                        appendBreak(output);
                    }
                }

                @Override
                public void handleEndTag(HTML.Tag tag, int position) {
                    if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) {
                        ignored = false;
                        return;
                    }
                    if (isBlockTag(tag) || tag == HTML.Tag.TR || tag == HTML.Tag.LI) {
                        appendBreak(output);
                    }
                }

                @Override
                public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                    if (tag == HTML.Tag.BR || tag == HTML.Tag.HR) {
                        appendBreak(output);
                    } else if (tag == HTML.Tag.IMG) {
                        Object alt = attributes.getAttribute(HTML.Attribute.ALT);
                        if (alt != null && !alt.toString().isBlank()) {
                            output.append("[图片：").append(alt.toString().trim()).append(']');
                        }
                    }
                }

                @Override
                public void handleText(char[] data, int position) {
                    if (!ignored) {
                        appendNormalized(output, new String(data));
                    }
                }
            }, true);
        } catch (Exception ignored) {
            return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        }
        return output.toString()
                .replaceAll("[ \\t]+\\n", "\\n")
                .replaceAll("\\n{3,}", "\\n\\n")
                .trim();
    }

    private List<String> split(String text) {
        String normalized = text == null ? "" : text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\\n\\n")
                .trim();
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + MAX_CHUNK_LENGTH, normalized.length());
            if (end < normalized.length()) {
                int paragraphBreak = normalized.lastIndexOf("\n\n", end);
                int lineBreak = normalized.lastIndexOf('\n', end);
                if (paragraphBreak > start + MAX_CHUNK_LENGTH / 2) {
                    end = paragraphBreak;
                } else if (lineBreak > start + MAX_CHUNK_LENGTH / 2) {
                    end = lineBreak;
                }
            }
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }
        return chunks;
    }

    private static boolean isBlockTag(HTML.Tag tag) {
        return tag == HTML.Tag.P || tag == HTML.Tag.DIV || tag == HTML.Tag.H1 || tag == HTML.Tag.H2
                || tag == HTML.Tag.H3 || tag == HTML.Tag.H4 || tag == HTML.Tag.H5 || tag == HTML.Tag.H6
                || tag == HTML.Tag.UL || tag == HTML.Tag.OL || tag == HTML.Tag.TABLE || tag == HTML.Tag.TR
                || tag == HTML.Tag.BLOCKQUOTE || tag == HTML.Tag.PRE;
    }

    private static void appendLine(StringBuilder builder, String line) {
        if (line != null && !line.isBlank()) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(line.trim());
        }
    }

    private static void appendBreak(StringBuilder builder) {
        if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != '\n') {
            builder.append('\n');
        }
    }

    private static void appendNormalized(StringBuilder builder, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (!normalized.isBlank()) {
            if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != '\n'
                    && builder.charAt(builder.length() - 1) != ' ') {
                builder.append(' ');
            }
            builder.append(normalized);
        }
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public record RequirementImageReference(int position, String src, Long fileId, String alt, int htmlPosition) {
    }
}
