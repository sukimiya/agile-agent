package com.jowo.agent.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {
    private String dir;
    private int ttlHours = 24;
    private int maxSizeMb = 10;
    private int maxFilesPerRequest = 10;
    private List<String> allowedContentTypes = List.of();

    public String getDir() { return dir; }
    public void setDir(String dir) { this.dir = dir; }
    public int getTtlHours() { return ttlHours; }
    public void setTtlHours(int v) { this.ttlHours = v; }
    public int getMaxSizeMb() { return maxSizeMb; }
    public void setMaxSizeMb(int v) { this.maxSizeMb = v; }
    public int getMaxFilesPerRequest() { return maxFilesPerRequest; }
    public void setMaxFilesPerRequest(int v) { this.maxFilesPerRequest = v; }
    public List<String> getAllowedContentTypes() { return allowedContentTypes; }
    public void setAllowedContentTypes(List<String> v) { this.allowedContentTypes = v; }

    public long getMaxSizeBytes() { return maxSizeMb * 1024L * 1024L; }
    public long getTtlMillis() { return ttlHours * 3600_000L; }
}
