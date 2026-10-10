package io.github.sukimiya.agileagent.agent;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.service.ApiCatalog;
import io.github.sukimiya.agileagent.service.EnterpriseApiClient;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.spi.capability.AgentCapabilityProvider;
import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.CapabilityDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolInvocation;
import io.github.sukimiya.agileagent.spi.capability.ToolResult;
import reactor.core.publisher.Mono;

/** Transitional provider adapter preserving existing API role behavior during capability migration. */
final class ApiRoleCapabilityProvider implements AgentCapabilityProvider {

    private static final Logger log = LoggerFactory.getLogger(ApiRoleCapabilityProvider.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final AgentRole role;
    private final ApiCatalog catalog;
    private final EnterpriseApiClient enterpriseApi;

    ApiRoleCapabilityProvider(AgentRole role, ApiCatalog catalog, EnterpriseApiClient enterpriseApi) {
        this.role = role;
        this.catalog = catalog;
        this.enterpriseApi = enterpriseApi;
    }

    @Override public String code() { return role.code(); }
    @Override public String displayName() { return role.displayName(); }
    @Override public String routingDescription() { return role.routingBlurb(); }

    @Override
    public Mono<CapabilityDefinition> definition(AgentExecutionContext context) {
        return Mono.fromSupplier(() -> new CapabilityDefinition(role.systemPrompt(), role.tools().stream()
                .map(tool -> new ToolDefinition(tool.name(), tool.description(), JSON.valueToTree(tool.parametersSchema())))
                .toList()));
    }

    @Override
    public Mono<ToolResult> execute(ToolInvocation invocation, AgentExecutionContext context) {
        var args = invocation.arguments();
        return switch (invocation.toolName()) {
            case "getApiDetail" -> {
                String service = args.path("service").asText();
                String path = args.path("path").asText();
                var detail = catalog.detail(service, path);
                log.info("getApiDetail service={} path={} found={}", service, path, detail.isPresent());
                yield Mono.just(new ToolResult(detail.isPresent(), detail.orElse("接口不存在: " + service + " " + path)));
            }
            case "callEnterpriseApi" -> callEnterpriseApi(args, context);
            default -> Mono.just(new ToolResult(false, "unknown tool: " + invocation.toolName()));
        };
    }

    private Mono<ToolResult> callEnterpriseApi(com.fasterxml.jackson.databind.JsonNode args,
                                                AgentExecutionContext context) {
        String service = args.path("service").asText();
        String method = args.path("method").asText();
        String path = args.path("path").asText();
        Map<String, Object> params = args.has("params")
                ? JSON.convertValue(args.get("params"), new TypeReference<Map<String, Object>>() {})
                : Map.of();
        if (!catalog.isWhitelisted(service, method, path)) {
            log.info("callEnterpriseApi REJECTED service={} method={} path={} params={}", service, method, path,
                    redact(params.toString(), null));
            return Mono.just(new ToolResult(false,
                    "调用被拒绝：不在已注册接口白名单中 (" + service + " " + method + " " + path + ")"));
        }
        String accessToken = context.accessTokenAccessor().get();
        String safeParams = redact(params.toString(), accessToken);
        return enterpriseApi.call(service, method, path, params, accessToken)
                .map(result -> {
                    String safeResult = redact(result.toString(), accessToken);
                    log.info("callEnterpriseApi OK service={} method={} path={} params={} result={}", service,
                            method, path, safeParams,
                            safeResult.length() > 500 ? safeResult.substring(0, 500) + "...(truncated)" : safeResult);
                    return new ToolResult(true, safeResult);
                })
                .onErrorResume(EnterpriseApiClient.EnterpriseApiException.class, error -> {
                    log.info("callEnterpriseApi FAILED service={} method={} path={} params={} error={}", service,
                            method, path, safeParams, redact(error.getMessage(), accessToken));
                    return Mono.just(new ToolResult(false, "接口调用失败：" + redact(error.getMessage(), accessToken)));
                });
    }

    private static String redact(String value, String token) {
        if (value == null) return "";
        return token == null || token.isEmpty() ? value : value.replace(token, "[redacted]");
    }
}
