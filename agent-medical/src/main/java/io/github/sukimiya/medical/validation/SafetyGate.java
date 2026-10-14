package io.github.sukimiya.medical.validation;

import java.util.ArrayList;
import java.util.List;

import io.github.sukimiya.medical.hospital.HospitalPackageContent;
import io.github.sukimiya.medical.hospital.HospitalToolSpec;
import io.github.sukimiya.medical.hospital.TransportBinding;
import reactor.core.publisher.Mono;

/**
 * Gate 5: Safety — every hospital-supplied text surface is screened for prompt-injection
 * markers and banned medical claims (spec §7), and every binding carries only the field of
 * its declared type (least privilege). A null parsedContent is tolerated: the upstream
 * short-circuit means SAFETY never runs on unparsable packages, but defense in depth.
 */
public final class SafetyGate implements PackageGate {

    private final TextSafetyScreen screen;

    public SafetyGate(TextSafetyScreen screen) {
        this.screen = screen;
    }

    @Override
    public String name() {
        return "SAFETY";
    }

    @Override
    public Mono<List<String>> evaluate(GateContext context) {
        return Mono.fromCallable(() -> {
            List<String> violations = new ArrayList<>();
            violations.addAll(screen.screen("hospital_name", context.candidate().hospitalName()));
            violations.addAll(screen.screen("routing_description", context.candidate().routingDescription()));
            HospitalPackageContent content = context.parsedContent();
            if (content == null) {
                return List.copyOf(violations);
            }
            for (HospitalToolSpec tool : content.tools()) {
                String prefix = "tool " + tool.name() + " ";
                violations.addAll(screen.screen(prefix + "description", tool.description()));
                TransportBinding binding = tool.transport();
                if (binding == null) {
                    continue;
                }
                violations.addAll(screen.screen(prefix + "channel", binding.channel()));
                violations.addAll(screen.screen(prefix + "address", binding.address()));
                violations.addAll(hygiene(prefix, binding));
            }
            return List.copyOf(violations);
        });
    }

    /** Least privilege: a binding carries only the field of its declared type. */
    private static List<String> hygiene(String prefix, TransportBinding binding) {
        if (binding.type() == null) {
            return List.of(); // SCHEMA gate owns the missing-type violation
        }
        return switch (binding.type()) {
            case API, PORTAL -> binding.channel() != null || binding.address() != null
                    ? List.of(prefix + binding.type() + " binding 携带与类型无关的字段（最小权限）")
                    : List.of();
            case HUMAN -> binding.url() != null || binding.address() != null
                    ? List.of(prefix + "HUMAN binding 携带与类型无关的字段（最小权限）")
                    : List.of();
            case EMAIL -> binding.url() != null || binding.channel() != null
                    ? List.of(prefix + "EMAIL binding 携带与类型无关的字段（最小权限）")
                    : List.of();
        };
    }
}
