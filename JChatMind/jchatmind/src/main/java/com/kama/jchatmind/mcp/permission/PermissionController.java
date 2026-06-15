package com.kama.jchatmind.mcp.permission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/mcp/permission")
public class PermissionController {

    private static final Logger log = LoggerFactory.getLogger(PermissionController.class);

    private final PermissionManager permissionManager;

    public PermissionController(PermissionManager permissionManager) {
        this.permissionManager = permissionManager;
    }

    @GetMapping("/mode")
    public Map<String, Object> getMode() {
        return Map.of("mode", permissionManager.getMode());
    }

    @PutMapping("/mode")
    public Map<String, Object> setMode(@RequestBody Map<String, String> body) {
        String mode = body.get("mode");
        permissionManager.setMode(mode);
        return Map.of("ok", true, "mode", permissionManager.getMode());
    }
    @PostMapping("/respond")
    public Map<String, Object> respond(@RequestBody Map<String, String> body) {
        String toolUseId = body.get("toolUseId");
        String decision = body.get("decision");
        if (toolUseId == null || decision == null) {
            return Map.of("ok", false, "error", "toolUseId and decision required");
        }
        permissionManager.respond(toolUseId, decision);
        log.info("Permission response: toolUseId={}, decision={}", toolUseId, decision);
        return Map.of("ok", true);
    }
}