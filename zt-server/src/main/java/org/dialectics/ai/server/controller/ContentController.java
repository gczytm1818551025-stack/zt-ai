package org.dialectics.ai.server.controller;

import org.dialectics.ai.agent.tools.FileStorageTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 内容控制器，用于处理与文件内容相关的请求，如下载和预览。
 */
@RestController
@RequestMapping("/public/content")
public class ContentController {
    @Autowired
    private FileStorageTools fileStorageTools;

    /**
     * 下载文件接口。
     *
     * @param uuid 文件唯一标识符。
     * @param name 下载时使用的文件名。
     * @return 包含 InputStreamResource 的 ResponseEntity 对象，表示可下载的文件资源。
     */
    @GetMapping("/download/{uuid}")
    public ResponseEntity<InputStreamResource> download(@PathVariable("uuid") String uuid, @RequestParam("name") String name) {
        var dw = fileStorageTools.generateDownloadableContent(uuid);
        return ResponseEntity.ok()
                .contentType(dw.type())
                .header("Content-disposition", "attachment; filename=" + name)
                .body(new InputStreamResource(dw.src()));
    }

    /**
     * 预览文件接口。
     *
     * @param uuid 文件唯一标识符。
     * @return 包含 InputStreamResource 的 ResponseEntity 对象，表示可预览的文件资源。
     */
    @GetMapping("/open/{uuid}")
    public ResponseEntity<InputStreamResource> open(@PathVariable("uuid") String uuid) {
        var dw = fileStorageTools.generateDownloadableContent(uuid);
        return ResponseEntity.ok()
                .contentType(dw.type())
                .body(new InputStreamResource(dw.src()));
    }
}
