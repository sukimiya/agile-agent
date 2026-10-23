package io.github.sukimiya.agileagent.memory;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

import io.github.sukimiya.agileagent.memory.MemoryEvalRunner.RunInfo;

/**
 * Real-model settings for {@code memory-eval-live}, read from the environment. Missing settings fail the run and
 * name only the variables, never their values; the API key never appears in {@link #toString} or any report.
 */
record MemoryEvalLiveConfig(String baseUrl, String apiKey, String model, String modelVersion) {

    static final String BASE_URL = "MEMORY_EVAL_LLM_BASE_URL";
    static final String API_KEY = "MEMORY_EVAL_LLM_API_KEY";
    static final String MODEL = "MEMORY_EVAL_LLM_MODEL";
    static final String MODEL_VERSION = "MEMORY_EVAL_LLM_MODEL_VERSION";

    /** The current system sends no temperature or other sampling parameters; the provider default applies. */
    static final String TEMPERATURE = "未发送（当前系统请求不设置 temperature 等采样参数，使用服务商默认值）";

    static MemoryEvalLiveConfig fromEnv(Map<String, String> env) {
        var missing = new ArrayList<String>();
        for (var name : new String[] {BASE_URL, API_KEY, MODEL}) {
            var value = env.get(name);
            if (value == null || value.isBlank()) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("memory-eval-live 需要真实模型配置，缺少环境变量：" + String.join(", ", missing)
                    + "（OpenAI 兼容接口：BASE_URL 为包含 /v1 的基础地址，请求 {BASE_URL}/chat/completions）");
        }
        var version = env.get(MODEL_VERSION);
        return new MemoryEvalLiveConfig(env.get(BASE_URL).trim(), env.get(API_KEY).trim(), env.get(MODEL).trim(),
                version == null || version.isBlank() ? null : version.trim());
    }

    RunInfo runInfo(String environment) {
        return new RunInfo(true, model, modelVersion == null ? "未提供（" + MODEL_VERSION + "）" : modelVersion,
                host(baseUrl), TEMPERATURE, MemoryEvalRunner.FULL_HISTORY, MemoryEvalRunner.commit(), Instant.now(),
                environment);
    }

    private static String host(String url) {
        try {
            var host = URI.create(url).getHost();
            return host == null ? "未知" : host;
        } catch (IllegalArgumentException e) {
            return "未知";
        }
    }

    @Override
    public String toString() {
        return "MemoryEvalLiveConfig[endpointHost=" + host(baseUrl) + ", model=" + model + ", modelVersion="
                + modelVersion + ", apiKey=[REDACTED]]";
    }
}
