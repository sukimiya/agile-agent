package io.github.sukimiya.medical.hospital;

import java.util.List;

/** Top-level structure of the package content JSON (schema v1). */
public record HospitalPackageContent(int schemaVersion, List<HospitalToolSpec> tools) {

    public HospitalPackageContent {
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
