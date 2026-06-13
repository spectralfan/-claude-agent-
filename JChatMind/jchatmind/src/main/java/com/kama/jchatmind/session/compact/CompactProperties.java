package com.kama.jchatmind.session.compact;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "session.compact")
public class CompactProperties {
    private boolean enabled = true;
    private double threshold = 0.80;
    private int toolResultLimit = 8000;
    private int toolResultKeep = 4000;
    private String model = "deepseek-chat";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }
    public int getToolResultLimit() { return toolResultLimit; }
    public void setToolResultLimit(int limit) { this.toolResultLimit = limit; }
    public int getToolResultKeep() { return toolResultKeep; }
    public void setToolResultKeep(int keep) { this.toolResultKeep = keep; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}