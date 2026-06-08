package com.kama.jchatmind.mcp.config;

/**
 * 解析 mcp.shell 配置与当前 OS，供 Java 预处理和 API 诊断使用。
 */
public final class McpShellPlatform {

    private McpShellPlatform() {
    }

    public static boolean isHostWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public static boolean useWindowsShell(McpProperties.Shell shell) {
        if (shell == null) {
            return isHostWindows();
        }
        String p = shell.getPlatform() == null ? "auto" : shell.getPlatform().trim().toLowerCase();
        return switch (p) {
            case "windows", "win32" -> true;
            case "posix", "linux", "unix" -> false;
            default -> isHostWindows();
        };
    }

    public static String resolvedExecutor(McpProperties.Shell shell) {
        if (shell == null) {
            return isHostWindows() ? "powershell" : "sh";
        }
        String e = shell.getExecutor() == null ? "auto" : shell.getExecutor().trim().toLowerCase();
        if (!"auto".equals(e)) {
            return e;
        }
        return useWindowsShell(shell) ? "powershell" : "sh";
    }

    public static String envPlatform(McpProperties.Shell shell) {
        return useWindowsShell(shell) ? "windows" : "posix";
    }
}
