package com.jowo.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.knowledge")
public class KnowledgeProperties {
    private String tempDir;
    private String libDir;
    private int maxSizeMb = 5;

    public String getTempDir() { return tempDir; }
    public void setTempDir(String v) { this.tempDir = v; }
    public String getLibDir() { return libDir; }
    public void setLibDir(String v) { this.libDir = v; }
    public int getMaxSizeMb() { return maxSizeMb; }
    public void setMaxSizeMb(int v) { this.maxSizeMb = v; }

    public long getMaxSizeBytes() { return maxSizeMb * 1024L * 1024L; }
}
