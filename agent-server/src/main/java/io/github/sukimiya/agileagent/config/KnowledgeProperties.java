package io.github.sukimiya.agileagent.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.knowledge")
public class KnowledgeProperties {
    private String tempDir;
    private String libDir;
    private int maxSizeMb = 5;
    private Duration summaryTimeout = Duration.ofSeconds(30);

    public String getTempDir() { return tempDir; }
    public void setTempDir(String v) { this.tempDir = v; }
    public String getLibDir() { return libDir; }
    public void setLibDir(String v) { this.libDir = v; }
    public int getMaxSizeMb() { return maxSizeMb; }
    public void setMaxSizeMb(int v) { this.maxSizeMb = v; }
    public Duration getSummaryTimeout() { return summaryTimeout; }
    public void setSummaryTimeout(Duration v) { this.summaryTimeout = v; }

    public long getMaxSizeBytes() { return maxSizeMb * 1024L * 1024L; }
}
