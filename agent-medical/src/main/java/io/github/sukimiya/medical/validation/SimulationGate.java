package io.github.sukimiya.medical.validation;

import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.ToolInvocation;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.medical.hospital.HospitalCapabilityProvider;
import io.github.sukimiya.medical.hospital.HospitalPackageAccessPolicy;
import io.github.sukimiya.medical.hospital.HospitalPackageCodec;
import io.github.sukimiya.medical.hospital.HospitalPackageContent;
import io.github.sukimiya.medical.hospital.HospitalPackageRepository;
import io.github.sukimiya.medical.hospital.HospitalPackageRow;
import io.github.sukimiya.medical.hospital.HospitalToolSpec;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRepository;
import io.github.sukimiya.medical.hospital.TransportBinding;
import io.github.sukimiya.medical.hospital.TransportType;
import io.github.sukimiya.medical.hospital.UnifiedToolCatalog;
import io.github.sukimiya.medical.transport.TransportExecutor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The sixth gate (spec §14 resolution 3): a deterministic dry-run of the candidate
 * package through a sandboxed provider — no LLM, no real hospital state. Five probes:
 * definition, read-only execution, anomalous arguments, cross-tenant isolation, and
 * repeat submission (human-task idempotency). Rows created by the human-task probe are
 * always cleaned up, pass or fail.
 */
public final class SimulationGate implements PackageGate {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> READ_ONLY_TOOLS = Set.of("searchServices", "checkAvailability");

    private final HospitalPackageAccessPolicy accessPolicy;
    private final HospitalPackageCodec codec;
    private final List<TransportExecutor> executors;
    private final MedicalHumanTaskRepository tasks;

    public SimulationGate(HospitalPackageAccessPolicy accessPolicy, HospitalPackageCodec codec,
            List<TransportExecutor> executors, MedicalHumanTaskRepository tasks) {
        this.accessPolicy = accessPolicy;
        this.codec = codec;
        this.executors = List.copyOf(executors);
        this.tasks = tasks;
    }

    @Override
    public String name() {
        return "SIMULATION";
    }

    @Override
    public Mono<List<String>> evaluate(GateContext context) {
        HospitalPackageContent content = context.parsedContent();
        if (content == null) {
            return Mono.just(List.of("模拟关无法执行：content 未能解析（上游关应已拦截）"));
        }
        HospitalPackageRow shadow = approvedShadow(context.candidate());
        HospitalPackageRepository sandbox = new HospitalPackageRepository(null) {
            @Override
            public Flux<HospitalPackageRow> findApproved() {
                return Flux.just(shadow);
            }

            @Override
            public Mono<HospitalPackageRow> findByHospitalCode(String hospitalCode) {
                return shadow.hospitalCode().equals(hospitalCode) ? Mono.just(shadow) : Mono.empty();
            }
        };
        var provider = new HospitalCapabilityProvider(sandbox, accessPolicy, codec, executors,
                Clock.systemDefaultZone());
        String conversationId = "c02-simulation-" + UUID.randomUUID();
        AgentExecutionContext simContext = new AgentExecutionContext(reviewerIdentity(shadow),
                conversationId, SimulationGate::forbiddenToken);
        List<String> violations = new ArrayList<>();
        return probeDefinition(provider, shadow, content, simContext, violations)
                .then(probeReadOnlyExecution(provider, shadow, content, simContext, violations))
                .then(probeAnomalousArguments(provider, shadow, content, simContext, violations))
                .then(probeCrossTenantIsolation(provider, shadow, content, violations))
                // Inter-probe cleanup: read-only probes may have persisted human tasks
                // (HUMAN-bound searchServices/checkAvailability) under the shared simulation
                // conversation; they would pollute the repeat-submission probe's dedup count.
                .then(tasks.deleteByConversationId(conversationId))
                .then(probeRepeatSubmission(provider, shadow, content, simContext, conversationId,
                        violations))
                .then(Mono.fromCallable(() -> List.copyOf(violations)))
                .onErrorResume(error -> {
                    violations.add("模拟执行异常: " + error.getClass().getSimpleName());
                    return Mono.just(List.copyOf(violations));
                });
    }

    private static String forbiddenToken() {
        throw new IllegalStateException("simulation must not read access tokens");
    }

    /** The sandbox row must be APPROVED — canConsume only admits APPROVED packages. */
    private static HospitalPackageRow approvedShadow(HospitalPackageRow candidate) {
        return new HospitalPackageRow(candidate.id(), candidate.hospitalCode(),
                candidate.hospitalName(), candidate.routingDescription(),
                HospitalPackageRow.Status.APPROVED, candidate.schemaVersion(), candidate.content(),
                candidate.source(), candidate.validFrom(), candidate.validUntil(),
                candidate.scopeType(), candidate.tenantId(), candidate.tenantName(),
                candidate.orgId(), candidate.orgName(), "gate-simulation", candidate.createdAt());
    }

    /** Mirrors the candidate's scope columns so canConsume admits the reviewer for TENANT/ORG rows. */
    private static IdentityContext reviewerIdentity(HospitalPackageRow shadow) {
        Set<String> orgIds = shadow.orgId() == null ? Set.of() : Set.of(shadow.orgId());
        return new IdentityContext("gate-simulation", "六道关模拟审查", shadow.tenantId(),
                shadow.tenantName(), shadow.orgId(), shadow.orgName(), Set.of("VALIDATOR"),
                orgIds, orgIds, true, false);
    }

    private Mono<Void> probeDefinition(HospitalCapabilityProvider provider,
            HospitalPackageRow shadow, HospitalPackageContent content,
            AgentExecutionContext simContext, List<String> violations) {
        return provider.definition(simContext).doOnNext(definition -> {
            long expected = expectedAdvertisedCount(content);
            if (expected == 0) {
                violations.add("无可用传输通道：没有任何工具可广告"
                        + "（EMAIL/PORTAL 执行器落地前，此类能力包不得发布）");
                return;
            }
            if (definition.tools().size() != expected) {
                violations.add("定义探针：期望广告 " + expected + " 个工具，实际 "
                        + definition.tools().size());
            }
            if (!definition.systemPrompt().contains(shadow.hospitalCode())) {
                violations.add("定义探针：systemPrompt 未包含医院代码 " + shadow.hospitalCode());
            }
        }).then();
    }

    /** Mirrors the provider's four advertisement filters so the expected count is exact. */
    private long expectedAdvertisedCount(HospitalPackageContent content) {
        Set<TransportType> registered = executors.stream()
                .map(TransportExecutor::type)
                .collect(Collectors.toSet());
        return content.tools().stream()
                .filter(spec -> UnifiedToolCatalog.SCHEMA_V1_TOOL_SET.contains(spec.name()))
                .filter(spec -> spec.transport() != null && registered.contains(spec.transport().type()))
                .filter(spec -> spec.description() != null)
                .filter(spec -> spec.parametersSchema() != null && !spec.parametersSchema().isNull())
                .count();
    }

    private Mono<Void> probeReadOnlyExecution(HospitalCapabilityProvider provider,
            HospitalPackageRow shadow, HospitalPackageContent content,
            AgentExecutionContext simContext, List<String> violations) {
        List<HospitalToolSpec> readOnly = content.tools().stream()
                .filter(spec -> READ_ONLY_TOOLS.contains(spec.name()))
                .filter(this::executable)
                .toList();
        return Flux.fromIterable(readOnly)
                .concatMap(spec -> provider.execute(new ToolInvocation(
                                shadow.hospitalCode() + "_" + spec.name(),
                                "probe-readonly-" + spec.name(), JSON.createObjectNode()), simContext)
                        .doOnNext(result -> {
                            if (!result.success()) {
                                checkHostEcho(spec, result.displayResult(),
                                        "只读执行探针 " + spec.name(), violations);
                            }
                        }))
                .then();
    }

    private Mono<Void> probeAnomalousArguments(HospitalCapabilityProvider provider,
            HospitalPackageRow shadow, HospitalPackageContent content,
            AgentExecutionContext simContext, List<String> violations) {
        Optional<HospitalToolSpec> firstApi = content.tools().stream()
                .filter(spec -> spec.transport() != null
                        && spec.transport().type() == TransportType.API)
                .filter(spec -> UnifiedToolCatalog.SCHEMA_V1_TOOL_SET.contains(spec.name()))
                .findFirst();
        if (firstApi.isEmpty()) {
            return Mono.empty();
        }
        HospitalToolSpec spec = firstApi.get();
        ObjectNode args = JSON.createObjectNode();
        args.set("__probe__", JSON.createObjectNode().set("nested",
                JSON.createArrayNode().add(1).add(2).add(3)));
        args.put("keyword", 12345);
        return provider.execute(new ToolInvocation(shadow.hospitalCode() + "_" + spec.name(),
                        "probe-anomalous", args), simContext)
                .doOnNext(result -> {
                    if (result.displayResult() == null) {
                        violations.add("异常参数探针 " + spec.name()
                                + ": displayResult 为 null（违反 ToolResult 契约）");
                    } else if (!result.success()) {
                        checkHostEcho(spec, result.displayResult(),
                                "异常参数探针 " + spec.name(), violations);
                    }
                })
                .then();
    }

    private Mono<Void> probeCrossTenantIsolation(HospitalCapabilityProvider provider,
            HospitalPackageRow shadow, HospitalPackageContent content, List<String> violations) {
        if (shadow.scopeType() == HospitalPackageRow.ScopeType.GLOBAL) {
            // GLOBAL packages are consumable by every tenant by design — nothing to isolate.
            return Mono.empty();
        }
        Optional<HospitalToolSpec> anyExecutable = content.tools().stream()
                .filter(this::executable)
                .findFirst();
        if (anyExecutable.isEmpty()) {
            return Mono.empty();
        }
        IdentityContext foreign = new IdentityContext("gate-simulation-foreign", "外部租户探针",
                "tenant-simulation-foreign-" + UUID.randomUUID(), "外部租户", null, null,
                Set.of(), Set.of(), Set.of(), false, false);
        AgentExecutionContext foreignContext = new AgentExecutionContext(foreign,
                "c02-simulation-foreign-" + UUID.randomUUID(), SimulationGate::forbiddenToken);
        HospitalToolSpec spec = anyExecutable.get();
        return provider.execute(new ToolInvocation(shadow.hospitalCode() + "_" + spec.name(),
                        "probe-cross-tenant", JSON.createObjectNode()), foreignContext)
                .doOnNext(result -> {
                    boolean refused = !result.success()
                            && ("医院能力包暂不可用: " + shadow.hospitalCode())
                                    .equals(result.displayResult());
                    if (!refused) {
                        violations.add("跨租户探针：外部租户执行未被拒绝（期望「医院能力包暂不可用」）");
                    }
                })
                .then();
    }

    private Mono<Void> probeRepeatSubmission(HospitalCapabilityProvider provider,
            HospitalPackageRow shadow, HospitalPackageContent content,
            AgentExecutionContext simContext, String conversationId, List<String> violations) {
        Optional<HospitalToolSpec> humanTool = content.tools().stream()
                .filter(spec -> spec.transport() != null
                        && spec.transport().type() == TransportType.HUMAN)
                .filter(spec -> executorFor(TransportType.HUMAN) != null)
                .filter(spec -> UnifiedToolCatalog.SCHEMA_V1_TOOL_SET.contains(spec.name()))
                .findFirst();
        if (humanTool.isEmpty()) {
            // API-only package: still exercise a repeat call; HTTP endpoints deduplicate
            // on their own side, so there is nothing to assert here.
            Optional<HospitalToolSpec> search = content.tools().stream()
                    .filter(spec -> "searchServices".equals(spec.name()))
                    .filter(this::executable)
                    .findFirst();
            return search.map(spec -> executeTwice(provider, shadow, spec, simContext))
                    .orElseGet(Mono::empty);
        }
        return executeTwice(provider, shadow, humanTool.get(), simContext)
                .then(tasks.countByConversationId(conversationId))
                .doOnNext(count -> {
                    if (count != 1L) {
                        violations.add("重复提交探针：期望恰好 1 条人工任务，实际 " + count);
                    }
                })
                .then(tasks.deleteByConversationId(conversationId))
                .onErrorResume(error -> tasks.deleteByConversationId(conversationId)
                        .then(Mono.error(error)))
                .then();
    }

    private Mono<Void> executeTwice(HospitalCapabilityProvider provider, HospitalPackageRow shadow,
            HospitalToolSpec spec, AgentExecutionContext simContext) {
        ObjectNode args = JSON.createObjectNode().put("question", "六道关模拟探针");
        return provider.execute(new ToolInvocation(shadow.hospitalCode() + "_" + spec.name(),
                        "probe-dedup", args.deepCopy()), simContext)
                .then(provider.execute(new ToolInvocation(shadow.hospitalCode() + "_" + spec.name(),
                        "probe-dedup-2", args.deepCopy()), simContext))
                .then();
    }

    private boolean executable(HospitalToolSpec spec) {
        return spec.transport() != null && executorFor(spec.transport().type()) != null
                && UnifiedToolCatalog.SCHEMA_V1_TOOL_SET.contains(spec.name());
    }

    private TransportExecutor executorFor(TransportType type) {
        return executors.stream().filter(executor -> executor.type() == type).findFirst().orElse(null);
    }

    /** Red line: curated-endpoint failure text must never echo the hospital host back. */
    private static void checkHostEcho(HospitalToolSpec spec, String displayResult,
            String probeLabel, List<String> violations) {
        TransportBinding binding = spec.transport();
        if (binding == null || binding.url() == null || displayResult == null) {
            return;
        }
        String host;
        try {
            host = URI.create(binding.url()).getHost();
        } catch (IllegalArgumentException ignored) {
            return;
        }
        if (host != null && !host.isBlank() && displayResult.contains(host)) {
            violations.add(probeLabel + ": 失败文本回显了端点主机（红线违规）");
        }
    }
}
