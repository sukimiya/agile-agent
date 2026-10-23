package io.github.sukimiya.agileagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryModel.HistoryPage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnCommand;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;

class MemoryScopeTest {

    @Test
    void rejectsBlankScope() {
        assertThatThrownBy(() -> new MemoryScope(" ", "u1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemoryScope("t1", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemoryScope(null, "u1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemoryScope("t1", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsValuesLongerThanTheIdentityColumns() {
        assertThatThrownBy(() -> new MemoryScope("t".repeat(MemoryScope.MAX_TENANT_LENGTH + 1), "u1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemoryScope("t1", "u".repeat(MemoryScope.MAX_SUBJECT_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void caseDistinctScopesRemainDistinct() {
        var lower = new MemoryScope("tenant-a", "alice");
        var upper = new MemoryScope("tenant-a", "Alice");
        var upperTenant = new MemoryScope("Tenant-A", "alice");

        assertThat(lower).isNotEqualTo(upper).isNotEqualTo(upperTenant);
        assertThat(upper.subject()).isEqualTo("Alice");
        assertThat(upperTenant.tenantId()).isEqualTo("Tenant-A");
    }

    @Test
    void scopeValuesAreNotTrimmedIntoAnotherIdentity() {
        var padded = new MemoryScope("tenant-a", " alice");

        assertThat(padded.subject()).isEqualTo(" alice");
        assertThat(padded).isNotEqualTo(new MemoryScope("tenant-a", "alice"));
    }

    @Test
    void scopeIsResolvedFromServerSideIdentity() {
        var scope = MemoryScope.of(MemoryTestFixture.identity("alice", "tenant-a"));

        assertThat(scope).isEqualTo(new MemoryScope("tenant-a", "alice"));
    }

    @Test
    void missingTenantIsForbidden() {
        for (var tenant : new String[] {null, "", "  "}) {
            assertThatThrownBy(() -> MemoryScope.of(MemoryTestFixture.identity("alice", tenant)))
                    .isInstanceOfSatisfying(MemoryException.class, e -> {
                        assertThat(e.code()).isEqualTo(ErrorCode.TENANT_REQUIRED);
                        assertThat(e.code().httpStatus()).isEqualTo(403);
                    });
        }
        assertThatThrownBy(() -> MemoryScope.of(null))
                .isInstanceOfSatisfying(MemoryException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.TENANT_REQUIRED));
    }

    @Test
    void errorCodesMapToTheFixedHttpStatuses() {
        assertThat(ErrorCode.INVALID_CONVERSATION_ID.httpStatus()).isEqualTo(400);
        assertThat(ErrorCode.TENANT_REQUIRED.httpStatus()).isEqualTo(403);
        assertThat(ErrorCode.CONVERSATION_NOT_FOUND.httpStatus()).isEqualTo(404);
        assertThat(ErrorCode.IDEMPOTENCY_CONFLICT.httpStatus()).isEqualTo(409);
        assertThat(ErrorCode.TURN_IN_PROGRESS.httpStatus()).isEqualTo(409);
        assertThat(ErrorCode.TURN_TERMINATED.httpStatus()).isEqualTo(409);
        assertThat(ErrorCode.STORAGE_UNAVAILABLE.httpStatus()).isEqualTo(503);
        assertThat(ErrorCode.INSTANCE_CONFLICT.httpStatus()).isEqualTo(503);
    }

    @Test
    void turnInProgressCarriesTheActiveTurnId() {
        var e = MemoryException.turnInProgress("turn-1", "conv-1");

        assertThat(e.code()).isEqualTo(ErrorCode.TURN_IN_PROGRESS);
        assertThat(e.turnId()).isEqualTo("turn-1");
        assertThat(e.conversationId()).isEqualTo("conv-1");
        assertThat(new MemoryException(ErrorCode.STORAGE_UNAVAILABLE).turnId()).isNull();
        assertThat(new MemoryException(ErrorCode.STORAGE_UNAVAILABLE).conversationId()).isNull();
    }

    @Test
    void onlyFinishedStatusesAreTerminal() {
        assertThat(TurnStatus.ACCEPTED.terminal()).isFalse();
        assertThat(TurnStatus.RUNNING.terminal()).isFalse();
        assertThat(List.of(TurnStatus.COMPLETED, TurnStatus.FAILED, TurnStatus.CANCELLED, TurnStatus.INTERRUPTED))
                .allMatch(TurnStatus::terminal);
    }

    @Test
    void unobservedUsageIsNotReportedAsZeroTokens() {
        assertThat(TokenUsage.notObserved().observed()).isFalse();
        assertThat(new TokenUsage(true, 1, 2, 3).totalTokens()).isEqualTo(3);
    }

    @Test
    void turnContractsRequireScopeAndIds() {
        var scope = new MemoryScope("t1", "u1");
        assertThatThrownBy(() -> new TurnCommand(null, "c1", "r1", "hi")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TurnCommand(scope, null, "r1", "hi")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TurnTicket(scope, "c1", null, 1)).isInstanceOf(NullPointerException.class);
        assertThat(new TurnCommand(scope, "c1", "r1", "hi").text()).isEqualTo("hi");
    }

    @Test
    void historyPageCopiesItsItems() {
        var items = new java.util.ArrayList<MemoryModel.StoredMessage>();
        var page = new HistoryPage(items, null, false);
        items.add(null);

        assertThat(page.items()).isEmpty();
    }

    @Test
    void sourceTypesDistinguishProvenance() {
        assertThat(SourceType.values()).contains(SourceType.USER_INPUT, SourceType.ASSISTANT,
                SourceType.TOOL_RESULT, SourceType.KNOWLEDGE_DOC, SourceType.WEB, SourceType.MODEL_SUMMARY);
    }
}
