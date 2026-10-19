package io.github.sukimiya.agileagent.medical;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.sukimiya.agileagent.PostgresTestDatabase;
import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.CapabilityDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolInvocation;
import io.github.sukimiya.agileagent.spi.capability.ToolResult;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.medical.hospital.HospitalCapabilityProvider;
import io.github.sukimiya.medical.hospital.HospitalPackageAccessPolicy;
import io.github.sukimiya.medical.hospital.HospitalPackageCodec;
import io.github.sukimiya.medical.hospital.HospitalPackageContent;
import io.github.sukimiya.medical.hospital.HospitalPackageRepository;
import io.github.sukimiya.medical.hospital.HospitalPackageRow;
import io.github.sukimiya.medical.hospital.HospitalPackageValidator;
import io.github.sukimiya.medical.hospital.HospitalToolSpec;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRepository;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRow;
import io.github.sukimiya.medical.hospital.TransportBinding;
import io.github.sukimiya.medical.hospital.UnifiedToolCatalog;
import io.github.sukimiya.medical.lifecycle.HospitalPackageLifecycleService;
import io.github.sukimiya.medical.lifecycle.LifecycleOutcome;
import io.github.sukimiya.medical.lifecycle.SubmissionStatus;
import io.github.sukimiya.medical.rules.ClaimOutcome;
import io.github.sukimiya.medical.rules.CompleteOutcome;
import io.github.sukimiya.medical.rules.ConversationGuard;
import io.github.sukimiya.medical.rules.EmergencyAction;
import io.github.sukimiya.medical.rules.EmergencyCondition;
import io.github.sukimiya.medical.rules.EmergencyRuleContent;
import io.github.sukimiya.medical.rules.EscalationService;
import io.github.sukimiya.medical.rules.MedicalRuleCodec;
import io.github.sukimiya.medical.rules.MedicalRuleLifecycleService;
import io.github.sukimiya.medical.rules.MedicalRuleRepository;
import io.github.sukimiya.medical.rules.MedicalRuleRow;
import io.github.sukimiya.medical.rules.MedicalRuleValidator;
import io.github.sukimiya.medical.rules.MedicalTaskEventRepository;
import io.github.sukimiya.medical.rules.RuleEngine;
import io.github.sukimiya.medical.rules.SafetyPatternAction;
import io.github.sukimiya.medical.rules.SafetyPatternCondition;
import io.github.sukimiya.medical.rules.SafetyPatternRuleContent;
import io.github.sukimiya.medical.rules.SafetyPatternStore;
import io.github.sukimiya.medical.transport.HumanTaskTransportExecutor;
import io.github.sukimiya.medical.transport.HttpApiTransportExecutor;
import io.github.sukimiya.medical.transport.TransportExecutor;
import io.github.sukimiya.medical.validation.CapabilityGate;
import io.github.sukimiya.medical.validation.EndpointScreeningPolicy;
import io.github.sukimiya.medical.validation.GatePipeline;
import io.github.sukimiya.medical.validation.ProvenanceGate;
import io.github.sukimiya.medical.validation.SafetyGate;
import io.github.sukimiya.medical.validation.SchemaGate;
import io.github.sukimiya.medical.validation.SemanticGate;
import io.github.sukimiya.medical.validation.SimulationGate;
import io.github.sukimiya.medical.validation.TextSafetyScreen;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * C04 acceptance (Plan 4 spec §12): the rule engine + E-call + human takeover + fixed-script
 * state machine wired manually against a real PostgreSQL database — scenarios ①–⑩ end to end.
 * A mutable clock keeps every SLA assertion deterministic (no sleeps, no timing flakes).
 * The V11 migration seeds the GLOBAL flagship rule, so an empty database already carries the
 * safety baseline (spec §4).
 */
class MedicalC04IntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HospitalPackageCodec HOSP_CODEC = new HospitalPackageCodec();
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final LocalDateTime START_LOCAL = LocalDateTime.of(2026, 10, 16, 12, 0);
    private static final Instant START = START_LOCAL.toInstant(ZoneOffset.UTC);

    /** Spec-verbatim scripts from the V11 flagship seed (design spec §6). */
    private static final String AWAITING_SCRIPT =
            "我们已立即为您联系专事专办医疗协调专员，请稍候。在专员接入前，本平台不提供任何医疗建议。"
                    + "如情况危急，请立即拨打 120 或前往就近急诊。";
    private static final String TAKEN_OVER_SCRIPT =
            "专事专办医疗协调专员已接管本次会话，我将仅提供辅助性行政信息。";

    private static final IdentityContext USER = new IdentityContext("user-1", "用户一",
            "tenant-1", "租户一", "org-1", "机构一", Set.of("USER"), Set.of(), Set.of(), false, false);
    private static final IdentityContext OPS = new IdentityContext("ops-1", "协调专员一",
            null, null, null, null, Set.of("MEDICAL_OPS"), Set.of(), Set.of(), false, false);
    private static final IdentityContext OPS_2 = new IdentityContext("ops-2", "协调专员二",
            null, null, null, null, Set.of("MEDICAL_OPS"), Set.of(), Set.of(), false, false);
    private static final IdentityContext ADMIN = new IdentityContext("admin-1", "平台管理员",
            null, null, null, null, Set.of("PLATFORM_ADMIN"), Set.of(), Set.of(), true, false);

    private PostgresTestDatabase db;
    private MockWebServer apiServer;
    private MutableClock clock;
    private HospitalPackageRepository packages;
    private MedicalHumanTaskRepository tasks;
    private MedicalRuleCodec ruleCodec;
    private TextSafetyScreen screen;
    private MedicalRuleLifecycleService ruleLifecycle;
    private HospitalPackageLifecycleService packageLifecycle;
    private EscalationService escalation;
    private ConversationGuard guard;
    private RuleEngine ruleEngine;
    private HospitalCapabilityProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        db = PostgresTestDatabase.create("medical_c04");
        db.migrate();
        apiServer = new MockWebServer();
        // Always-200: simulation probes inside package review fire many requests.
        apiServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(200).setBody("{\"ok\":true}");
            }
        });
        apiServer.start();
        clock = new MutableClock(START);

        packages = new HospitalPackageRepository(db.databaseClient());
        tasks = new MedicalHumanTaskRepository(db.databaseClient());
        var events = new MedicalTaskEventRepository(db.databaseClient());
        var ruleRepo = new MedicalRuleRepository(db.databaseClient());
        ruleCodec = new MedicalRuleCodec();
        var patternStore = new SafetyPatternStore(ruleRepo, ruleCodec, clock);
        screen = new TextSafetyScreen(patternStore);
        ruleLifecycle = new MedicalRuleLifecycleService(ruleRepo,
                new MedicalRuleValidator(ruleCodec, screen, clock), ruleCodec, patternStore);
        escalation = new EscalationService(tasks, events, clock, 1800L, true);
        guard = new ConversationGuard(tasks, escalation);
        ruleEngine = new RuleEngine(ruleRepo, ruleCodec, escalation, clock);
        var endpoints = new EndpointScreeningPolicy(Set.of("127.0.0.1", "localhost"));
        List<TransportExecutor> executors = List.of(
                new HttpApiTransportExecutor(WebClient.builder(), endpoints),
                new HumanTaskTransportExecutor(tasks));
        provider = new HospitalCapabilityProvider(packages, new HospitalPackageAccessPolicy(),
                HOSP_CODEC, executors, guard, ruleEngine, clock);
        packageLifecycle = new HospitalPackageLifecycleService(packages, new GatePipeline(List.of(
                new SchemaGate(new HospitalPackageValidator(HOSP_CODEC)),
                new SemanticGate(),
                new ProvenanceGate(clock),
                new CapabilityGate(endpoints),
                new SafetyGate(screen),
                new SimulationGate(new HospitalPackageAccessPolicy(), HOSP_CODEC, executors, tasks,
                        guard, ruleEngine))), HOSP_CODEC);
        seedApprovedHospitals();
    }

    @AfterEach
    void tearDown() throws Exception {
        apiServer.shutdown();
        db.close();
    }

    /** Mutable clock: SLA arithmetic stays deterministic — advance() replaces sleeping. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant start) {
            this.instant = start;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private void seedApprovedHospitals() {
        ObjectNode schema = JSON.createObjectNode().put("type", "object");
        schema.set("properties", JSON.createObjectNode());
        String apiContent = HOSP_CODEC.write(new HospitalPackageContent(1,
                UnifiedToolCatalog.SCHEMA_V1_TOOLS.stream()
                        .map(name -> new HospitalToolSpec(name, "描述-" + name, schema,
                                TransportBinding.api(apiServer.url("/api/hospital/unified").toString())))
                        .toList()));
        packages.insert(new HospitalPackageRow("pkg-mockapi", "mockapi", "模拟互联网医院",
                "模拟互联网医院路由描述", HospitalPackageRow.Status.APPROVED, 1, apiContent, "manual",
                START_LOCAL.minusDays(1), START_LOCAL.plusDays(1),
                HospitalPackageRow.ScopeType.GLOBAL, null, null, null, null, "admin", null))
                .block(TIMEOUT);
        String humanContent = HOSP_CODEC.write(new HospitalPackageContent(1,
                UnifiedToolCatalog.SCHEMA_V1_TOOLS.stream()
                        .map(name -> new HospitalToolSpec(name, "描述-" + name, schema,
                                TransportBinding.human("wechat-desk")))
                        .toList()));
        packages.insert(new HospitalPackageRow("pkg-mockhuman", "mockhuman", "模拟人工医院",
                "模拟人工医院路由描述", HospitalPackageRow.Status.APPROVED, 1, humanContent, "manual",
                START_LOCAL.minusDays(1), START_LOCAL.plusDays(1),
                HospitalPackageRow.ScopeType.GLOBAL, null, null, null, null, "admin", null))
                .block(TIMEOUT);
    }

    private static AgentExecutionContext context(String conversationId) {
        return new AgentExecutionContext(USER, conversationId, () -> "token");
    }

    private ToolResult triage(String conversationId, String category, String disease, String notes) {
        ObjectNode args = JSON.createObjectNode().put("triageCategory", category);
        var pairs = args.putArray("symptomToDisease");
        if (disease != null) {
            pairs.addObject().put("symptom", "症状-" + disease).put("disease", disease);
        }
        if (notes != null) {
            args.put("notes", notes);
        }
        return provider.execute(new ToolInvocation("submitTriage", "call-1", args),
                context(conversationId)).block(TIMEOUT);
    }

    private ToolResult executeTool(String conversationId, String toolName) {
        return provider.execute(new ToolInvocation(toolName, "call-x", JSON.createObjectNode()),
                context(conversationId)).block(TIMEOUT);
    }

    private CapabilityDefinition definition(String conversationId) {
        return provider.definition(context(conversationId)).block(TIMEOUT);
    }

    private Map<String, Object> openEcall(String conversationId) {
        return db.jdbc().queryForMap("SELECT id, status, priority, escalation_level, sla_deadline "
                + "FROM medical_human_tasks WHERE conversation_id = ? "
                + "AND task_kind = 'ECALL' AND status <> 'COMPLETED'", conversationId);
    }

    private int ecallCount(String conversationId) {
        return db.jdbc().queryForObject("SELECT COUNT(*) FROM medical_human_tasks "
                + "WHERE conversation_id = ? AND task_kind = 'ECALL'", Integer.class, conversationId);
    }

    private List<Map<String, Object>> eventsOf(String taskId) {
        return db.jdbc().queryForList("SELECT event_type, level, actor, detail "
                + "FROM medical_task_events WHERE task_id = ? ORDER BY created_at", taskId);
    }

    private int levelOf(String taskId) {
        return db.jdbc().queryForObject(
                "SELECT escalation_level FROM medical_human_tasks WHERE id = ?",
                Integer.class, taskId);
    }

    // ---- spec §12 scenario ① ----

    @Test
    void triageHitCreatesEcallWithAuditAndReturnsFixedScript() {
        var result = triage("conversation-c04-hit", "EMERGENCY", "急性心肌梗死", "用户自述持续胸痛");

        assertThat(result.success()).isTrue();
        assertThat(result.displayResult()).isEqualTo(AWAITING_SCRIPT);
        var task = openEcall("conversation-c04-hit");
        assertThat(task.get("status")).isEqualTo("PENDING");
        assertThat(task.get("priority")).isEqualTo(100);
        assertThat(task.get("escalation_level")).isEqualTo(0);
        assertThat(((Timestamp) task.get("sla_deadline")).toLocalDateTime()).isEqualTo(START_LOCAL);
        assertThat(db.jdbc().queryForMap("SELECT hospital_code, tool_name, created_by "
                + "FROM medical_human_tasks WHERE id = ?", (String) task.get("id")))
                .containsEntry("hospital_code", "ecall")   // ruling R5 constants
                .containsEntry("tool_name", "submitTriage")
                .containsEntry("created_by", "user-1");
        var events = eventsOf((String) task.get("id"));
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).containsEntry("event_type", "CREATED").containsEntry("level", 0);
        assertThat((String) events.get(0).get("detail"))
                .contains("rule_code=global-emergency-baseline")
                .contains("triage_category=EMERGENCY");
    }

    // ---- spec §12 scenario ② (red line: 受限态下自主工具执行 = 0) ----

    @Test
    void restrictedStateZeroToolsFixedPromptAndAllExecutionsRefused() {
        triage("conversation-c04-restricted", "EMERGENCY", "脑卒中", null);

        var definition = definition("conversation-c04-restricted");
        assertThat(definition.tools()).isEmpty();
        assertThat(definition.systemPrompt())
                .contains(AWAITING_SCRIPT)
                .contains("你没有任何可用工具")
                .doesNotContain("医院索引");

        var hospital = executeTool("conversation-c04-restricted", "mockapi_searchServices");
        assertThat(hospital.success()).isFalse();
        assertThat(hospital.displayResult()).isEqualTo(AWAITING_SCRIPT);
        // re-triage while restricted is refused too (spec §5 — no duplicate E-call noise)
        var retriage = executeTool("conversation-c04-restricted", "submitTriage");
        assertThat(retriage.success()).isFalse();
        assertThat(retriage.displayResult()).isEqualTo(AWAITING_SCRIPT);
        assertThat(apiServer.getRequestCount()).isZero();
    }

    // ---- spec §12 scenario ③ ----

    @Test
    void claimCasTransitionsToTakenOverAuxiliaryPrompt() {
        triage("conversation-c04-takeover", "CRITICAL", "stroke", null);
        String taskId = (String) openEcall("conversation-c04-takeover").get("id");

        assertThat(escalation.claim(taskId, OPS).block(TIMEOUT)).isEqualTo(ClaimOutcome.APPLIED);

        assertThat(db.jdbc().queryForMap("SELECT status, claimed_by, claimed_at "
                + "FROM medical_human_tasks WHERE id = ?", taskId))
                .containsEntry("status", "CLAIMED")
                .containsEntry("claimed_by", "ops-1");
        var definition = definition("conversation-c04-takeover");
        assertThat(definition.tools()).isEmpty();
        assertThat(definition.systemPrompt())
                .contains(TAKEN_OVER_SCRIPT)
                .contains("你没有任何可用工具");
        var refused = executeTool("conversation-c04-takeover", "mockapi_searchServices");
        assertThat(refused.success()).isFalse();
        assertThat(refused.displayResult()).isEqualTo(TAKEN_OVER_SCRIPT);
        assertThat(eventsOf(taskId)).extracting(event -> event.get("event_type"))
                .containsExactly("CREATED", "CLAIMED");
    }

    // ---- spec §12 scenario ④ ----

    @Test
    void doubleClaimYieldsExactlyOneWinner() {
        triage("conversation-c04-claim", "EMERGENCY", "休克", null);
        String taskId = (String) openEcall("conversation-c04-claim").get("id");

        assertThat(escalation.claim(taskId, USER).block(TIMEOUT))
                .isEqualTo(ClaimOutcome.NOT_AUTHORIZED); // MEDICAL_OPS / platformAdmin only
        assertThat(escalation.claim(taskId, OPS).block(TIMEOUT)).isEqualTo(ClaimOutcome.APPLIED);
        assertThat(escalation.claim(taskId, OPS_2).block(TIMEOUT)).isEqualTo(ClaimOutcome.CONFLICT);
        assertThat(escalation.claim(taskId, ADMIN).block(TIMEOUT)).isEqualTo(ClaimOutcome.CONFLICT);

        assertThat(db.jdbc().queryForObject(
                "SELECT claimed_by FROM medical_human_tasks WHERE id = ?", String.class, taskId))
                .isEqualTo("ops-1");
        assertThat(eventsOf(taskId).stream()
                .filter(event -> "CLAIMED".equals(event.get("event_type"))).count())
                .isEqualTo(1L);
    }

    // ---- spec §12 scenario ⑤ + C04 consultation closure (创建/追踪/完成) ----

    @Test
    void completionRestoresNormalStateAndClosesTheHumanConsultation() {
        triage("conversation-c04-close", "EMERGENCY", "昏迷", null);
        String ecallId = (String) openEcall("conversation-c04-close").get("id");
        assertThat(escalation.claim(ecallId, OPS).block(TIMEOUT)).isEqualTo(ClaimOutcome.APPLIED);

        assertThat(escalation.complete(ecallId, "已协助用户转入急诊绿色通道", OPS).block(TIMEOUT))
                .isEqualTo(CompleteOutcome.APPLIED);

        assertThat(db.jdbc().queryForMap("SELECT status, resolution_note, completed_at "
                + "FROM medical_human_tasks WHERE id = ?", ecallId))
                .containsEntry("status", "COMPLETED")
                .containsEntry("resolution_note", "已协助用户转入急诊绿色通道");
        assertThat(eventsOf(ecallId)).extracting(event -> event.get("event_type"))
                .containsExactly("CREATED", "CLAIMED", "COMPLETED");

        // conversation is normal again: tools re-advertised, autonomous execution allowed
        var definition = definition("conversation-c04-close");
        assertThat(definition.tools()).hasSize(11); // 2 hospitals × 5 tools + submitTriage
        assertThat(definition.tools()).extracting(t -> t.name()).contains("submitTriage");
        assertThat(executeTool("conversation-c04-close", "mockapi_searchServices").success()).isTrue();
        assertThat(apiServer.getRequestCount()).isGreaterThan(0);

        // C04 closure: agent-created consultation (PENDING) is tracked, then completed by ops
        // after human-verified hospital confirmation. Before COMPLETED, the agent output
        // never claims a booking (red line) — the pending script proves it.
        var consult = provider.execute(new ToolInvocation("mockhuman_requestConsultation", "call-c1",
                        JSON.createObjectNode().put("department", "cardiology")),
                context("conversation-c04-close")).block(TIMEOUT);
        assertThat(consult.success()).isTrue();
        assertThat(consult.displayResult()).contains("尚未确认任何预约").doesNotContain("已预约成功");
        String normalId = db.jdbc().queryForObject("SELECT id FROM medical_human_tasks "
                + "WHERE conversation_id = 'conversation-c04-close' AND task_kind = 'NORMAL'",
                String.class);
        assertThat(db.jdbc().queryForObject("SELECT status FROM medical_human_tasks WHERE id = ?",
                String.class, normalId)).isEqualTo("PENDING"); // tracking
        assertThat(escalation.complete(normalId, "医院已确认预约", OPS).block(TIMEOUT))
                .isEqualTo(CompleteOutcome.APPLIED); // NORMAL completes straight from PENDING
        assertThat(db.jdbc().queryForMap("SELECT status, resolution_note "
                + "FROM medical_human_tasks WHERE id = ?", normalId))
                .containsEntry("status", "COMPLETED")
                .containsEntry("resolution_note", "医院已确认预约");
        assertThat(eventsOf(normalId)).extracting(event -> event.get("event_type"))
                .containsExactly("COMPLETED");
    }

    // ---- spec §12 scenario ⑥ ----

    @Test
    void triageMissLeavesNormalFlowUntouched() {
        var result = triage("conversation-c04-miss", "ROUTINE", "普通感冒", "轻微流涕");

        assertThat(result.success()).isTrue();
        assertThat(result.displayResult()).isEqualTo(RuleEngine.TRIAGE_MISS);
        assertThat(ecallCount("conversation-c04-miss")).isZero();
        assertThat(definition("conversation-c04-miss").tools()).hasSize(11);
        assertThat(executeTool("conversation-c04-miss", "mockapi_searchServices").success()).isTrue();
    }

    // ---- spec §12 scenario ⑦ (timed + lazy escalation, ceiling with one terminal audit) ----

    @Test
    void slaOverdueEscalatesLazilyAndCeilingsWithOneTerminalAudit() {
        triage("conversation-c04-sla", "EMERGENCY", "肺栓塞", null);
        String taskId = (String) openEcall("conversation-c04-sla").get("id");

        // deadline == now is NOT overdue (strict <) — no premature escalation
        assertThat(escalation.escalateOverdue().block(TIMEOUT)).isZero();

        clock.advance(Duration.ofSeconds(1));
        assertThat(escalation.escalateOverdue().block(TIMEOUT)).isEqualTo(1);
        assertEscalated(taskId, 1); // window levels[0]=300 → deadline = now+300s

        // lazy double-check (ruling 5): touching the conversation runs the same routine
        clock.advance(Duration.ofSeconds(301));
        assertThat(guard.state("conversation-c04-sla").block(TIMEOUT).restricted()).isTrue();
        assertEscalated(taskId, 2); // window levels[1]=900

        clock.advance(Duration.ofSeconds(901));
        assertThat(escalation.escalateOverdue().block(TIMEOUT)).isEqualTo(1);
        assertEscalated(taskId, 3); // window levels[2]=1800

        // ceiling: ladder exhausted — level frozen at 3, exactly ONE terminal audit,
        // repeated ticks stay silent, conversation still restricted (spec §8 层级用尽封顶)
        clock.advance(Duration.ofSeconds(1801));
        assertThat(escalation.escalateOverdue().block(TIMEOUT)).isZero();
        assertThat(escalation.escalateOverdue().block(TIMEOUT)).isZero();
        assertThat(levelOf(taskId)).isEqualTo(3);
        assertThat(eventsOf(taskId).stream()
                .filter(event -> "ESCALATED".equals(event.get("event_type"))
                        && Integer.valueOf(3).equals(event.get("level"))
                        && String.valueOf(event.get("detail")).contains("已冒泡至最高层级"))
                .count()).isEqualTo(1L);
        assertThat(definition("conversation-c04-sla").tools()).isEmpty();
    }

    private void assertEscalated(String taskId, int level) {
        assertThat(levelOf(taskId)).isEqualTo(level);
        var escalated = eventsOf(taskId).stream()
                .filter(event -> "ESCALATED".equals(event.get("event_type"))
                        && Integer.valueOf(level).equals(event.get("level")))
                .toList();
        assertThat(escalated).hasSize(1);
        assertThat(String.valueOf(escalated.get(0).get("detail"))).contains("sla_deadline=");
    }

    // ---- spec §12 scenario ⑧ (red line: 每会话未关闭 E-call ≤ 1) ----

    @Test
    void repeatTriageOnOpenEcallIsIdempotent() {
        var first = triage("conversation-c04-idem", "EMERGENCY", "心源性猝死", null);
        assertThat(first.success()).isTrue();

        // provider level: the restricted state refuses re-triage outright (spec §5)
        assertThat(executeTool("conversation-c04-idem", "submitTriage").success()).isFalse();

        // engine level: even a direct repeat hit reuses the open task (partial unique index)
        ObjectNode args = JSON.createObjectNode().put("triageCategory", "CRITICAL");
        args.putArray("symptomToDisease").addObject()
                .put("symptom", "抽搐").put("disease", "癫痫持续状态");
        var second = ruleEngine.triage(args, context("conversation-c04-idem")).block(TIMEOUT);
        assertThat(second.success()).isTrue();
        assertThat(second.displayResult()).isEqualTo(AWAITING_SCRIPT);

        assertThat(ecallCount("conversation-c04-idem")).isEqualTo(1);
        String taskId = (String) openEcall("conversation-c04-idem").get("id");
        assertThat(eventsOf(taskId)).extracting(event -> event.get("event_type"))
                .containsExactly("CREATED");
    }

    // ---- spec §12 scenario ⑨ (red line: GLOBAL 基线不可被下级规则放松) ----

    @Test
    void globalBaselineCannotBeRelaxedByAHospitalRule() {
        // A HOSPITAL rule with the SAME rule_code tries to relax the baseline: one disease
        // only, slaSeconds 600, softer scripts. Inserted straight into the DB — the merge
        // must still take the strictest combination (spec §6: 只能收紧，不能放松).
        String content = ruleCodec.write(new EmergencyRuleContent(1, "EMERGENCY",
                new EmergencyCondition(List.of(), List.of("急性心肌梗死")),
                new EmergencyAction(10, 600, List.of(), "院级宽松等待话术", "院级宽松接管话术")));
        db.jdbc().update("INSERT INTO medical_rules (id, rule_code, rule_kind, scope_type, "
                + "tenant_id, tenant_name, hospital_code, content_json, source, valid_from, "
                + "valid_until, status, approval_version, created_by) VALUES "
                + "(?, 'global-emergency-baseline', 'EMERGENCY', 'HOSPITAL', 'tenant-1', '租户一', "
                + "'mockapi', ?, 'manual', ?, ?, 'APPROVED', 0, 'admin')",
                "rule-hosp-relax", content,
                Timestamp.valueOf(START_LOCAL.minusDays(1)),
                Timestamp.valueOf(START_LOCAL.plusDays(1)));

        // 脑卒中 is ONLY on the GLOBAL list — the hospital rule cannot remove it (union merge)
        var result = triage("conversation-c04-baseline", "ROUTINE", "脑卒中", null);
        assertThat(result.success()).isTrue();
        // donor = tightest SLA = the GLOBAL row: the pre-approved GLOBAL scripts stay,
        // the softer hospital scripts never surface
        assertThat(result.displayResult()).isEqualTo(AWAITING_SCRIPT)
                .doesNotContain("院级宽松");
        var task = openEcall("conversation-c04-baseline");
        assertThat(((Timestamp) task.get("sla_deadline")).toLocalDateTime())
                .isEqualTo(START_LOCAL); // min SLA 0 wins, not 600
        assertThat(task.get("priority")).isEqualTo(100); // max priority wins
    }

    // ---- spec §12 scenario ⑩ (data-driven safety mode live in the six-gate SafetyGate) ----

    @Test
    void approvedSafetyPatternTakesEffectInTheSixGateSafetyGate() {
        // before: the marker phrase is unknown to the frozen code baseline
        assertThat(screen.screen("描述", "推荐 MIRACLE_CURE_2026 疗法")).isEmpty();

        String content = ruleCodec.write(new SafetyPatternRuleContent(1, "SAFETY_PATTERN",
                new SafetyPatternCondition(List.of("MIRACLE_CURE_2026"), "规则数据"),
                new SafetyPatternAction()));
        var draft = new MedicalRuleRow(null, "safety-pattern-miracle-cure", "SAFETY_PATTERN",
                MedicalRuleRow.ScopeType.GLOBAL, null, null, null, null, null, content, "manual",
                START_LOCAL.minusDays(1), START_LOCAL.plusDays(1), MedicalRuleRow.Status.PENDING,
                "admin-1", null);
        var submitted = ruleLifecycle.submit(draft, ADMIN).block(TIMEOUT);
        assertThat(submitted.status()).isEqualTo(SubmissionStatus.SUBMITTED);
        var report = ruleLifecycle.review(submitted.state().id()).block(TIMEOUT);
        assertThat(report.passed()).isTrue();
        assertThat(ruleLifecycle.approve(submitted.state().id(),
                submitted.state().approvalVersion(), ADMIN).block(TIMEOUT))
                .isEqualTo(LifecycleOutcome.APPLIED); // approve reloads the runtime store

        // after: the data increment is live without a redeploy; the frozen baseline still bites
        assertThat(screen.screen("描述", "推荐 MIRACLE_CURE_2026 疗法"))
                .containsExactly("描述: 命中数据驱动安全模式（规则数据）");
        assertThat(screen.screen("描述", "本院包治百病")).isNotEmpty();

        // and the six-gate SafetyGate consumes the SAME composite screen (spec §12⑩)
        var packageDraft = new HospitalPackageRow(null, "c04pattern", "数据模式医院",
                "数据模式医院路由描述", HospitalPackageRow.Status.PENDING, 1,
                patternPackageContent(), "manual",
                START_LOCAL.minusDays(1), START_LOCAL.plusDays(1),
                HospitalPackageRow.ScopeType.GLOBAL, null, null, null, null, "admin-1", null);
        var pkgSubmitted = packageLifecycle.submit(packageDraft, ADMIN).block(TIMEOUT);
        assertThat(pkgSubmitted.status()).isEqualTo(SubmissionStatus.SUBMITTED);
        var gateReport = packageLifecycle.review(pkgSubmitted.state().id(), ADMIN)
                .block(Duration.ofSeconds(30));
        assertThat(gateReport.passed()).isFalse();
        assertThat(gateReport.violations().get("SAFETY"))
                .anyMatch(violation -> violation.contains("命中数据驱动安全模式（规则数据）"));
    }

    private String patternPackageContent() {
        ObjectNode schema = JSON.createObjectNode().put("type", "object");
        schema.set("properties", JSON.createObjectNode());
        return HOSP_CODEC.write(new HospitalPackageContent(1,
                UnifiedToolCatalog.SCHEMA_V1_TOOLS.stream()
                        .map(name -> new HospitalToolSpec(name,
                                "searchServices".equals(name)
                                        ? "推荐 MIRACLE_CURE_2026 疗法" : "描述-" + name,
                                schema,
                                TransportBinding.api(apiServer.url("/api/hospital/unified").toString())))
                        .toList()));
    }
}
