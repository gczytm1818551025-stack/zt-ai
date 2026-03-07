package org.dialectics.ai.agent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@Slf4j
public class FileStorageTools {
    @Value("${zt-ai.file.base:~/Desktop}")
    private String baseFolder;
    @Value("${zt-ai.file.domain:http://localhost:18081}")
    private String domain;

    @Tool(name = "saveFile", description = "存储文件并返回文件uuid")
    public String saveFile(@ToolParam(description = "文件内容的输入字符串") String content) {
        var uuid = IdUtil.fastSimpleUUID();
        FileUtil.writeBytes(content.getBytes(StandardCharsets.UTF_8), getFilePath(uuid));
        return uuid;
    }

    @Tool(name = "generateDownloadUrl", description = "根据文件uuid生成可下载url")
    public String generateDownloadUrl(@ToolParam(description = "文件uuid") String uuid) {
        return StrUtil.format("{}{}?name={}", domain, StrUtil.format("/public/content/download/{uuid}", Map.of("uuid", uuid)), "resultdoc.html");
    }

    @Tool(name = "generateOpenUrl", description = "根据文件uuid生成可打开url")
    public String generateOpenUrl(@ToolParam(description = "文件uuid") String uuid) {
        return StrUtil.format("{}{}", domain, StrUtil.format("/public/content/open/{uuid}", Map.of("uuid", uuid)));
    }

    @Tool(name = "generateDownloadableContent", description = "根据文件uuid生成可下载的媒体内容，生成内容包含媒体类型和数据输入流")
    public DownloadTableContent generateDownloadableContent(@ToolParam(description = "文件uuid") String uuid) {
        // 获取文件输入流
        var inputStream = FileUtil.getInputStream(this.getFilePath(uuid));
        try {
            // 通过tika获取文件类型
            Tika tika = new Tika();
            String mimeType = tika.detect(inputStream);
            // 创建下载内容对象返回
            return new DownloadTableContent(new MediaType(MediaType.valueOf(mimeType), StandardCharsets.UTF_8), inputStream);
        } catch (Exception e) {
            log.error("IOException in generateDownloadableContent", e);
            return new DownloadTableContent(MediaType.APPLICATION_OCTET_STREAM, inputStream);
        }
    }

    private String getFilePath(String uuid) {
        return baseFolder + "/" + uuid;
    }

    /**
     * 可下载内容的数据结构，包含媒体类型和输入流。
     *
     * @param type 媒体类型（如 application/pdf、image/jpeg）
     * @param src  输入流，用于读取文件内容
     */
    public record DownloadTableContent(MediaType type, InputStream src) {
    }
}

