package io.github.sukimiya.medical.validation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The fixed-order six-gate pipeline (spec §14 resolution 5): gates run in injected order,
 * every violation is collected (one red gate does not stop the next), and exactly two
 * short-circuit rules apply — unparsable content skips everything after SCHEMA, and a red
 * CAPABILITY gate skips SIMULATION so un-screened endpoints are never probed.
 */
public final class GatePipeline {

    private static final Set<String> NEVER_SKIPPED = Set.of("SCHEMA");

    private final List<PackageGate> gates;

    public GatePipeline(List<PackageGate> gates) {
        this.gates = List.copyOf(gates);
    }

    /**
     * Runs all gates and collects the report. The returned Mono is cold and safe to
     * resubscribe (retry, test re-run): per-subscription state lives inside
     * {@code Mono.defer}. Sequencing: {@code concatMap} subscribes to each gate's evaluate
     * payload only after the previous gate completes, in injected order — the skip rules
     * read the violations collected so far, so this ordering is load-bearing.
     */
    public Mono<GateReport> run(GateContext context, long approvalVersion) {
        return Mono.defer(() -> {
            Map<String, List<String>> violations = new LinkedHashMap<>();
            List<String> skipped = new ArrayList<>();
            return Flux.fromIterable(gates)
                    .concatMap(gate -> {
                        if (shouldSkip(gate.name(), context, violations)) {
                            skipped.add(gate.name());
                            violations.put(gate.name(), List.of());
                            return Mono.empty();
                        }
                        return gate.evaluate(context)
                                .doOnNext(found -> violations.put(gate.name(), List.copyOf(found)));
                    })
                    .then(Mono.fromCallable(() -> {
                        boolean passed = violations.values().stream().allMatch(List::isEmpty);
                        return new GateReport(context.candidate().hospitalCode(), approvalVersion,
                                passed, violations, List.copyOf(skipped));
                    }));
        });
    }

    private static boolean shouldSkip(String gateName, GateContext context,
            Map<String, List<String>> violations) {
        if (NEVER_SKIPPED.contains(gateName)) {
            return false;
        }
        if (context.parsedContent() == null) {
            // Rule 1: unparsable content — every gate after SCHEMA is moot.
            return true;
        }
        if ("SIMULATION".equals(gateName)) {
            // Rule 2: CAPABILITY red — never probe an endpoint that failed screening.
            List<String> capability = violations.get("CAPABILITY");
            return capability != null && !capability.isEmpty();
        }
        return false;
    }
}
