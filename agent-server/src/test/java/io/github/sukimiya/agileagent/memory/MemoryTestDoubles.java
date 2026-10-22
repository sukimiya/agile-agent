package io.github.sukimiya.agileagent.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Function;

import io.github.sukimiya.agileagent.memory.MemoryModel.AcceptedTurn;
import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.service.LlmClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Storage-free doubles for tests that must not perform database I/O (BlockHound, logging, Spring wiring): a
 * lifecycle that runs the operation with an in-memory recorder, and a history that returns a fixed prompt.
 */
public final class MemoryTestDoubles {

    private MemoryTestDoubles() {}

    public static AcceptedTurn acceptedTurn(String conversationId) {
        var ticket = new TurnTicket(TurnTestSupport.ALICE, conversationId, "turn-" + conversationId, 1);
        return new AcceptedTurn(ticket, false, null, TokenUsage.notObserved());
    }

    @SuppressWarnings("unchecked")
    public static TurnLifecycle passThroughLifecycle() {
        var lifecycle = mock(TurnLifecycle.class);
        when(lifecycle.execute(any(), any())).thenAnswer(invocation -> {
            TurnTicket ticket = invocation.getArgument(0);
            Function<TurnRecorder, Flux<String>> operation = invocation.getArgument(1);
            var recorder = new TurnRecorder(ticket, (t, text) -> Mono.just(true), new MemoryProperties(),
                    System::nanoTime);
            return Flux.defer(() -> operation.apply(recorder));
        });
        return lifecycle;
    }

    public static ConversationHistory fixedHistory(String userInput, List<LlmClient.Message> context) {
        var history = mock(ConversationHistory.class);
        when(history.contextBefore(any())).thenReturn(Mono.just(context));
        when(history.userInput(any())).thenReturn(Mono.just(userInput));
        return history;
    }
}
