package io.github.sukimiya.agileagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.cos")
public class CosProperties {
    private String endpoint;
    private String secretId;
    private String secretKey;
    private String keyPrefix = "chat-uploads";

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint == null ? null : endpoint.replaceAll("/+$", "");
    }
    public String getSecretId() { return secretId; }
    public void setSecretId(String secretId) { this.secretId = secretId; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

    public boolean enabled() {
        return nonBlank(endpoint) && nonBlank(secretId) && nonBlank(secretKey);
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }
}
