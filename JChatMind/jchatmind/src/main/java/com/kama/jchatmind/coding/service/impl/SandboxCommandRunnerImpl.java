package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.model.dto.CommandExecutionResult;
import com.kama.jchatmind.coding.service.SandboxCommandRunner;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SandboxCommandRunnerImpl implements SandboxCommandRunner {

    @Override
    public CommandExecutionResult run(List<String> command, Path workspace, int timeoutSeconds, int outputMaxChars) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workspace.toFile());
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            StringBuilder out = new StringBuilder();
            Thread reader = new Thread(() -> readOutput(process, out, outputMaxChars));
            reader.setDaemon(true);
            reader.start();

            boolean done = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!done) {
                process.destroyForcibly();
                return CommandExecutionResult.builder()
                        .exitCode(-1)
                        .timeout(true)
                        .output("命令执行超时（" + timeoutSeconds + "秒）")
                        .build();
            }
            reader.join(1000);
            return CommandExecutionResult.builder()
                    .exitCode(process.exitValue())
                    .timeout(false)
                    .output(truncateHeadTail(out.toString(), outputMaxChars))
                    .build();
        } catch (Exception e) {
            return CommandExecutionResult.builder()
                    .exitCode(-1)
                    .timeout(false)
                    .output("命令执行失败: " + e.getMessage())
                    .build();
        }
    }

    private void readOutput(Process process, StringBuilder out, int maxChars) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                out.append(line).append('\n');
                if (out.length() > maxChars * 3) {
                    out.delete(0, out.length() - maxChars * 2);
                }
            }
        } catch (IOException ignore) {
        }
    }

    private String truncateHeadTail(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        int half = maxChars / 2;
        return text.substring(0, half) + "\n...(输出已截断)...\n" + text.substring(text.length() - half);
    }
}
