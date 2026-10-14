package io.github.sukimiya.medical.validation;

import java.util.List;

import reactor.core.publisher.Mono;

/**
 * One publication gate of the six-gate pipeline (spec §7). Contract: evaluate emits exactly
 * one (possibly empty) violation list and never throws on bad package data — violations are
 * data, not exceptions. Only infrastructure failures may propagate as errors.
 */
public interface PackageGate {

    /** Stable gate identifier, used as the GateReport violations key (e.g. "SCHEMA"). */
    String name();

    Mono<List<String>> evaluate(GateContext context);
}
