package io.github.sukimiya.medical.validation;

import java.util.ArrayList;
import java.util.List;

import io.github.sukimiya.medical.hospital.HospitalPackageContent;
import io.github.sukimiya.medical.hospital.HospitalToolSpec;
import io.github.sukimiya.medical.hospital.TransportBinding;
import io.github.sukimiya.medical.hospital.TransportType;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Gate 4: Capability — the submitter must hold authority for the draft's scope, and every
 * curated network endpoint must pass SSRF screening (spec §14 resolutions 2 and 4). Screening
 * resolves DNS, which blocks, so the whole evaluation runs on boundedElastic. A red
 * CAPABILITY gate short-circuits SIMULATION (pipeline rule 2): the approval system must never
 * fire real HTTP at an unscreened host.
 */
public final class CapabilityGate implements PackageGate {

    private final EndpointScreeningPolicy endpoints;

    public CapabilityGate(EndpointScreeningPolicy endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public String name() {
        return "CAPABILITY";
    }

    @Override
    public Mono<List<String>> evaluate(GateContext context) {
        return Mono.fromCallable(() -> {
            List<String> violations = new ArrayList<>();
            if (!PackageSubmissionAuthority.maySubmit(context.submitter(), context.candidate())) {
                var scope = context.candidate().scopeType();
                violations.add("提交者无权提交 " + (scope == null ? "null" : scope.name())
                        + " 范围的能力包");
            }
            HospitalPackageContent content = context.parsedContent();
            if (content != null) {
                for (HospitalToolSpec tool : content.tools()) {
                    TransportBinding binding = tool.transport();
                    if (binding != null && (binding.type() == TransportType.API
                            || binding.type() == TransportType.PORTAL)
                            && !endpoints.permits(binding.url())) {
                        violations.add("tool " + tool.name()
                                + ": 端点主机未通过 SSRF 筛查（非 http(s)、内网/元数据地址或不可解析）");
                    }
                }
            }
            return List.copyOf(violations);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
