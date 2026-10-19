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
import io.github.sukimiya.medical.rules.ConversationGuard;
import io.github.sukimiya.medical.rules.ConversationState;
import io.github.sukimiya.medical.rules.RuleEngine;
import io.github.sukimiya.medical.transport.TransportExecutor;
import io.github.sukimiya.medical.transport.TransportRequest;
import reactor.core.publisher.Mono;

/**
 * The single HOSPITAL capability (spec §5): a data-driven provider whose hospitals,
 * tools, and transports come entirely from approved knowledge rows — adding a hospital
 * is inserting a row, never a code change (spec §4 两层架构). Hospital choice and tool
 * choice are internal to this capability; the orchestrator only ever routes to HOSPITAL.
 *
 * <p>Plan 4 (spec §8, ruling 4): every definition()/execute() entry consults the
 * {@link ConversationGuard} FIRST. In any restricted state (awaiting takeover, taken over,
 * fail-closed) the agent receives a fixed-script systemPrompt and ZERO advertised tools —
 * the DomainAgent only executes tools that definition() declared, so autonomous action is
 * hard-gated — and execute() refuses everything with the state's script as defense in
 * depth. In the normal state the capability-level built-in submitTriage is always
 * advertised alongside the hospital tools (spec §5) and routed to the deterministic
 * {@link RuleEngine}.
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
            5. 行政协调涉及病情语义（症状、疾病名、紧急程度）时，必须先调用内建工具 submitTriage 提交结构化分诊；是否紧急由平台规则引擎判定，你无权自行判断。

            医院索引：
            %s
            """;

    /** Restricted-state prompts (spec ruling 4): fixed script, zero tools, no discretion. */
    private static final String AWAITING_TAKEOVER_PROMPT = """
            本次会话已被医疗规则引擎升级为紧急专办通道（E-call），正在等待专事专办医疗协调专员接管。
            你没有任何可用工具：绝不执行任何工具调用或行政协调操作，绝不给出医疗建议、诊断或治疗方案，绝不声称已预约。
            无论用户说什么，只逐字回复以下预批准话术，不得增删、改写或补充任何其他内容：
            %s
            """;

    private static final String TAKEN_OVER_PROMPT = """
            专事专办医疗协调专员已接管本次会话。
            你没有任何可用工具，退居辅助角色：绝不给出医疗建议、诊断或治疗方案，绝不声称已预约，绝不执行任何操作。
            回复用户时以下列预批准话术开场，其后仅可补充辅助性行政信息：
            %s
            """;

    private static final String FAIL_CLOSED_PROMPT = """
            会话状态检查失败。为安全起见（宁闸勿放），本次会话已进入受限模式。
            你没有任何可用工具，绝不执行任何操作。只逐字回复以下话术，不得增删或改写：
            """ + RuleEngine.SAFE_FALLBACK + "\n";

    private final HospitalPackageRepository packages;
    private final HospitalPackageAccessPolicy accessPolicy;
    private final HospitalPackageCodec codec;
    private final Map<TransportType, TransportExecutor> executors;
    private final ConversationGuard guard;
    private final RuleEngine ruleEngine;
    private final Clock clock;

    public HospitalCapabilityProvider(HospitalPackageRepository packages,
            HospitalPackageAccessPolicy accessPolicy, HospitalPackageCodec codec,
            List<TransportExecutor> transportExecutors, ConversationGuard guard,
            RuleEngine ruleEngine, Clock clock) {
        this.packages = packages;
        this.accessPolicy = accessPolicy;
        this.codec = codec;
        this.executors = transportExecutors.stream()
                .collect(Collectors.toUnmodifiableMap(TransportExecutor::type, Function.identity()));
        this.guard = guard;
        this.ruleEngine = ruleEngine;
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
        return guard.state(context.conversationId())
                .flatMap(state -> state.restricted()
                        ? Mono.just(restrictedDefinition(state))
                        : normalDefinition(context));
    }

    @Override
    public Mono<ToolResult> execute(ToolInvocation invocation, AgentExecutionContext context) {
        return guard.state(context.conversationId())
                .flatMap(state -> state.restricted()
                        ? Mono.just(refusedInRestrictedState(state))
                        : executeNormal(invocation, context));
    }

    /** Normal state: data-driven hospital tools + the built-in submitTriage (spec §5). */
    private Mono<CapabilityDefinition> normalDefinition(AgentExecutionContext context) {
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
                    tools.add(UnifiedToolCatalog.triageToolDefinition());
                    return new CapabilityDefinition(PROMPT_TEMPLATE.formatted(index), List.copyOf(tools));
                });
    }

    private static CapabilityDefinition restrictedDefinition(ConversationState state) {
        String prompt = switch (state.state()) {
            case AWAITING_TAKEOVER ->
                AWAITING_TAKEOVER_PROMPT.formatted(state.payload().awaitingTakeoverScript());
            case TAKEN_OVER -> TAKEN_OVER_PROMPT.formatted(state.payload().takenOverScript());
            case FAIL_CLOSED -> FAIL_CLOSED_PROMPT;
            case NORMAL -> throw new IllegalStateException("NORMAL is not a restricted state");
        };
        return new CapabilityDefinition(prompt, List.of());
    }

    private Mono<ToolResult> executeNormal(ToolInvocation invocation, AgentExecutionContext context) {
        String toolName = invocation.toolName();
        if (UnifiedToolCatalog.TRIAGE_TOOL.equals(toolName)) {
            return ruleEngine.triage(invocation.arguments(), context);
        }
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

    /**
     * Defense in depth (spec ruling 4): even if an LLM somehow issues a tool call in a
     * restricted state, every call is refused with the state's fixed script — never a
     * hospital-tool result, and never triage (spec §5: re-triage while an E-call is open
     * would only duplicate it).
     */
    private static ToolResult refusedInRestrictedState(ConversationState state) {
        String script = switch (state.state()) {
            case AWAITING_TAKEOVER -> state.payload().awaitingTakeoverScript();
            case TAKEN_OVER -> state.payload().takenOverScript();
            case FAIL_CLOSED -> RuleEngine.SAFE_FALLBACK;
            case NORMAL -> throw new IllegalStateException("NORMAL is not a restricted state");
        };
        return new ToolResult(false, script);
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
