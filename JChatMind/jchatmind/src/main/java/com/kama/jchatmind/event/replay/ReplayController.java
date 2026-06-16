package com.kama.jchatmind.event.replay;

import com.kama.jchatmind.session.config.SessionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 事件重放控制器 — 前端断线重连后，通过 run_id 回放完整的 Agent 执行过程。
 * 读取 runs/{runId}/events.jsonl 返回 NDJSON。
 */
@Slf4j
@RestController
@RequestMapping("/api/events/replay")
@RequiredArgsConstructor
public class ReplayController {

    private final SessionProperties sessionProperties;

    /**
     * 回放指定 run 的全部事件。
     * GET /api/events/replay/{runId}?sinceLine=N
     */
    @GetMapping("/{runId}")
    public ResponseEntity<InputStreamResource> replay(@PathVariable String runId,
                                                       @RequestParam(defaultValue = "0") int sinceLine) {
        Path eventsFile = Path.of(sessionProperties.getStoreRoot(), "runs", runId, "events.jsonl");
        if (!Files.exists(eventsFile)) {
            return ResponseEntity.notFound().build();
        }
        try {
            InputStream stream = Files.newInputStream(eventsFile);
            if (sinceLine > 0) {
                // 粗略跳过指定字节数，精确跳过行数由前端处理
                stream.skip(sinceLine);
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/x-ndjson"))
                    .body(new InputStreamResource(stream));
        } catch (IOException e) {
            log.warn("Failed to read events file for runId={}: {}", runId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 返回事件文件元信息。
     */
    @GetMapping("/{runId}/info")
    public ResponseEntity<?> info(@PathVariable String runId) {
        Path eventsFile = Path.of(sessionProperties.getStoreRoot(), "runs", runId, "events.jsonl");
        if (!Files.exists(eventsFile)) {
            return ResponseEntity.notFound().build();
        }
        try {
            long size = Files.size(eventsFile);
            long lines = Files.lines(eventsFile).count();
            return ResponseEntity.ok(java.util.Map.of(
                    "runId", runId,
                    "fileSize", size,
                    "lineCount", lines
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}