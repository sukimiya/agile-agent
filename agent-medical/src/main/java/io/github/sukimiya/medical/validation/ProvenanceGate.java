package io.github.sukimiya.medical.validation;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import io.github.sukimiya.medical.hospital.HospitalPackageRow;
import reactor.core.publisher.Mono;

/**
 * Gate 3: provenance — the source must come from the known vocabulary, the package must not
 * already be expired, and valid_from must not be more than a year old (suspected stale
 * documents need a human re-check, spec §4 来源). The clock is injected; no Clock bean is
 * exposed to the host application (auto-configuration inlines a system clock).
 */
public final class ProvenanceGate implements PackageGate {

    private static final List<String> KNOWN_SOURCES = List.of("import", "manual", "migration");
    private static final long MAX_VALID_FROM_AGE_DAYS = 365;

    private final Clock clock;

    public ProvenanceGate(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String name() {
        return "PROVENANCE";
    }

    @Override
    public Mono<List<String>> evaluate(GateContext context) {
        return Mono.fromCallable(() -> {
            HospitalPackageRow row = context.candidate();
            List<String> violations = new ArrayList<>();
            if (row.source() == null || !KNOWN_SOURCES.contains(row.source())) {
                violations.add("source must be one of " + KNOWN_SOURCES + " but was: " + row.source());
            }
            LocalDateTime now = LocalDateTime.now(clock);
            if (row.validUntil() == null || !row.validUntil().isAfter(now)) {
                violations.add("能力包有效期已过（valid_until=" + row.validUntil() + "），不得发布");
            }
            if (row.validFrom() != null
                    && row.validFrom().isBefore(now.minusDays(MAX_VALID_FROM_AGE_DAYS))) {
                violations.add("valid_from 距今超过 " + MAX_VALID_FROM_AGE_DAYS
                        + " 天，来源文档疑似陈旧，需人工复核");
            }
            return List.copyOf(violations);
        });
    }
}
