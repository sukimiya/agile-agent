package io.github.sukimiya.medical.hospital;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.sukimiya.agileagent.spi.capability.AgentCapabilityProvider;
import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.CapabilityDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolInvocation;
import io.github.sukimiya.agileagent.spi.capability.ToolResult;
import io.github.sukimiya.medical.transport.TransportExecutor;
import io.github.sukimiya.medical.transport.TransportRequest;
import reactor.core.publisher.Mono;

/**
 * The single HOSPITAL capability (spec §5): a data-driven provider whose hospitals,
 * tools, and transports come entirely from approved knowledge rows — adding a hospital
 * is inserting a row, never a code change (spec §4 两层架构). Hospital choice and tool
 * choice are internal to this capability; the orchestrator only ever routes to HOSPITAL.
 */
public final class HospitalCapabilityProvider implements AgentCapabilityProvider {

    public static final String CODE = "HOSPITAL";

    private static final String PROMPT_TEMPLATE = """
            你是医疗行政协调助手，帮助外籍患者对接中国医院的服务流程。

            规则：
            1. 你只做行政协调与信息转达，绝不给出医疗建议、诊断或治疗方案。
            2. 工具名格式为「医院代码_工具名」，医院代码取自下方医院索引。
            3. 人工通道工具返回「已提交」仅表示等待医院确认；在医院确认之前，绝不向用户声称已预约、已缴费或已提交成功。
            4. 只使用下方索引中的医院与工具，不编造不存在的服务、号源或结果。

            医院索引：
            %s
            """;

    private final HospitalPackageRepository packages;
    private final HospitalPackageAccessPolicy accessPolicy;
    private final HospitalPackageCodec codec;
    private final Map<TransportType, TransportExecutor> executors;
    private final Clock clock;

    public HospitalCapabilityProvider(HospitalPackageRepository packages,
            HospitalPackageAccessPolicy accessPolicy, HospitalPackageCodec codec,
            List<TransportExecutor> transportExecutors, Clock clock) {
        this.packages = packages;
        this.accessPolicy = accessPolicy;
        this.codec = codec;
        this.executors = transportExecutors.stream()
                .collect(Collectors.toUnmodifiableMap(TransportExecutor::type, Function.identity()));
        this.clock = clock;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String displayName() {
        return "医院服务";
    }

    @Override
    public String routingDescription() {
        return "对接中国医院的预约、咨询、报价与材料提交等行政协调服务";
    }

    @Override
    public Mono<CapabilityDefinition> definition(AgentExecutionContext context) {
        return packages.findApproved()
                .filter(row -> accessPolicy.canConsume(context.identity(), row))
                .filter(this::withinValidityWindow)
                .concatMap(row -> Mono.justOrEmpty(codec.parse(row.content())
                                .map(content -> new HospitalTools(row, advertisedTools(row, content))))
                        .filter(hospital -> !hospital.tools().isEmpty()))
                .collectList()
                .map(hospitals -> {
                    List<ToolDefinition> tools = new ArrayList<>();
                    StringBuilder index = new StringBuilder();
                    for (var hospital : hospitals) {
                        var row = hospital.row();
                        if (index.length() > 0) {
                            index.append('\n');
                        }
                        index.append(row.hospitalCode()).append(" | ").append(row.hospitalName())
                                .append(" | ").append(row.routingDescription());
                        tools.addAll(hospital.tools());
                    }
                    return new CapabilityDefinition(PROMPT_TEMPLATE.formatted(index), List.copyOf(tools));
                });
    }

    @Override
    public Mono<ToolResult> execute(ToolInvocation invocation, AgentExecutionContext context) {
        String toolName = invocation.toolName();
        int separator = toolName.indexOf('_');
        if (separator <= 0 || separator == toolName.length() - 1) {
            return Mono.just(new ToolResult(false, "unknown tool: " + toolName));
        }
        String hospitalCode = toolName.substring(0, separator);
        String unifiedName = toolName.substring(separator + 1);
        if (!UnifiedToolCatalog.SCHEMA_V1_TOOL_SET.contains(unifiedName)) {
            return Mono.just(new ToolResult(false, "unknown tool: " + toolName));
        }
        return packages.findByHospitalCode(hospitalCode)
                .flatMap(row -> executeOnPackage(row, unifiedName, invocation, context))
                .switchIfEmpty(Mono.just(new ToolResult(false, "医院能力不可用: " + hospitalCode)));
    }

    private Mono<ToolResult> executeOnPackage(HospitalPackageRow row, String unifiedName,
            ToolInvocation invocation, AgentExecutionContext context) {
        if (!withinValidityWindow(row) || !accessPolicy.canConsume(context.identity(), row)) {
            return refused(row);
        }
        Optional<HospitalToolSpec> spec = codec.parse(row.content())
                .flatMap(content -> content.tools().stream()
                        .filter(candidate -> unifiedName.equals(candidate.name()))
                        .findFirst());
        return spec.map(found -> dispatch(row, found, invocation, context)).orElseGet(() -> refused(row));
    }

    private Mono<ToolResult> dispatch(HospitalPackageRow row, HospitalToolSpec spec,
            ToolInvocation invocation, AgentExecutionContext context) {
        TransportExecutor executor = spec.transport() == null
                ? null : executors.get(spec.transport().type());
        if (executor == null) {
            return Mono.just(new ToolResult(false, "该服务通道暂未开放，请改用其他方式或联系人工客服"));
        }
        return executor.execute(new TransportRequest(row.hospitalCode(), row.hospitalName(),
                spec.name(), spec.transport(), invocation.arguments(), context));
    }

    private static Mono<ToolResult> refused(HospitalPackageRow row) {
        return Mono.just(new ToolResult(false, "医院能力包暂不可用: " + row.hospitalCode()));
    }

    /** Only schema-v1 unified tools with a registered executor are advertised; junk data is skipped. */
    private List<ToolDefinition> advertisedTools(HospitalPackageRow row, HospitalPackageContent content) {
        return content.tools().stream()
                .filter(spec -> UnifiedToolCatalog.SCHEMA_V1_TOOL_SET.contains(spec.name()))
                .filter(spec -> spec.transport() != null && executors.containsKey(spec.transport().type()))
                .filter(spec -> spec.description() != null)
                .filter(spec -> spec.parametersSchema() != null && !spec.parametersSchema().isNull())
                .map(spec -> new ToolDefinition(toolName(row.hospitalCode(), spec.name()),
                        spec.description(), spec.parametersSchema()))
                .toList();
    }

    private boolean withinValidityWindow(HospitalPackageRow row) {
        LocalDateTime now = LocalDateTime.now(clock);
        return !now.isBefore(row.validFrom()) && !now.isAfter(row.validUntil());
    }

    static String toolName(String hospitalCode, String unifiedName) {
        return hospitalCode + "_" + unifiedName;
    }

    private record HospitalTools(HospitalPackageRow row, List<ToolDefinition> tools) {
    }
}
