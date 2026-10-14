package io.github.sukimiya.medical.validation;

import java.util.List;

import io.github.sukimiya.medical.hospital.HospitalPackageValidator;
import reactor.core.publisher.Mono;

/** Gate 1: code-layer schema — the hardened Plan 2 validator, wrapped unchanged (spec §7). */
public final class SchemaGate implements PackageGate {

    private final HospitalPackageValidator validator;

    public SchemaGate(HospitalPackageValidator validator) {
        this.validator = validator;
    }

    @Override
    public String name() {
        return "SCHEMA";
    }

    @Override
    public Mono<List<String>> evaluate(GateContext context) {
        return Mono.fromCallable(() -> validator.validate(context.candidate()));
    }
}
