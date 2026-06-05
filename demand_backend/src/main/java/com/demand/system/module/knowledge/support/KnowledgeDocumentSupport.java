package com.demand.system.module.knowledge.support;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class KnowledgeDocumentSupport {

    public static final Set<String> PREVIEW_SUPPORTED_EXTENSIONS = Set.copyOf(new LinkedHashSet<>(List.of(
            "docx", "wps", "doc", "docm", "xls", "xlsx", "csv", "xlsm", "ppt", "pptx", "vsd", "rtf",
            "odt", "wmf", "emf", "dps", "et", "ods", "ots", "tsv", "odp", "otp", "sxi", "ott",
            "vsdx", "fodt", "fods", "xltx", "tga", "psd", "dotm", "ett", "xlt", "xltm", "wpt",
            "dot", "xlam", "dotx", "xla", "pages", "eps", "pptm",
            "jpg", "jpeg", "png", "gif", "bmp", "ico", "jfif", "webp", "heic", "avif", "heif",
            "rar", "zip", "jar", "7-zip", "tar", "gzip", "7z",
            "obj", "3ds", "stl", "ply", "off", "3dm", "fbx", "dae", "wrl", "3mf", "ifc", "glb",
            "o3dv", "gltf", "stp", "bim", "fcstd", "step", "iges", "brep",
            "eml", "msg", "xmind", "epub", "dcm", "drawio",
            "xml", "xbrl", "json", "tif", "tiff", "ofd", "svg",
            "dwg", "dxf", "dwf", "iges", "igs", "dwt", "dng", "ifc", "dwfx", "stl", "cf2", "plt",
            "txt", "html", "htm", "asp", "jsp", "properties", "md", "gitignore", "log", "java",
            "py", "c", "cpp", "sql", "sh", "bat", "m", "bas", "prg", "cmd",
            "php", "go", "python", "js", "ftl", "css", "lua", "rb", "yaml", "yml", "h", "cs",
            "aspx", "pdf", "bpmn", "mp3", "wav", "mp4", "flv", "mpd", "m3u8", "ts", "m4a",
            "3gp", "avi", "mkv", "mov", "mpeg", "rm", "wmv"
    )));

    public static final Set<String> DIRECT_TEXT_PREVIEW_EXTENSIONS = Set.copyOf(new LinkedHashSet<>(List.of(
            "txt", "md", "csv", "tsv", "json", "xml", "xbrl", "log", "yml", "yaml", "html", "htm",
            "asp", "jsp", "properties", "gitignore", "java", "py", "python", "c", "cpp", "sql",
            "sh", "bat", "m", "bas", "prg", "cmd", "php", "go", "js", "ftl", "css", "lua",
            "rb", "h", "cs", "aspx"
    )));

    public static final Set<String> IMAGE_EXTENSIONS = Set.copyOf(new LinkedHashSet<>(List.of(
            "jpg", "jpeg", "png", "gif", "bmp", "ico", "jfif", "webp", "svg"
    )));

    public static final Set<String> VECTORIZABLE_EXTENSIONS = Set.copyOf(new LinkedHashSet<>(List.of(
            "txt", "md", "csv", "tsv", "json", "xml", "xbrl", "log", "yml", "yaml", "html", "htm",
            "asp", "jsp", "properties", "gitignore", "java", "py", "python", "c", "cpp", "sql",
            "sh", "bat", "m", "bas", "prg", "cmd", "php", "go", "js", "ftl", "css", "lua",
            "rb", "h", "cs", "aspx", "pdf", "doc", "docx", "xls", "xlsx"
    )));

    private KnowledgeDocumentSupport() {
    }

    public static boolean isSupported(String extension) {
        return extension != null && PREVIEW_SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
    }

    public static boolean isVectorizable(String extension) {
        return extension != null && VECTORIZABLE_EXTENSIONS.contains(extension.toLowerCase());
    }

    public static boolean isDirectTextPreview(String extension) {
        return extension != null && DIRECT_TEXT_PREVIEW_EXTENSIONS.contains(extension.toLowerCase());
    }

    public static boolean isImage(String extension) {
        return extension != null && IMAGE_EXTENSIONS.contains(extension.toLowerCase());
    }
}
