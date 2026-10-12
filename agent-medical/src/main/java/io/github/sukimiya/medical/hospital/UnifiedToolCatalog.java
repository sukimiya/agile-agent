package io.github.sukimiya.medical.hospital;

import java.util.List;
import java.util.Set;

/**
 * The schema v1 unified tool set (spec §11 resolution 4). Every v1 package must declare
 * exactly these five tools. Fine-grained tools extend the catalog in a later schema version.
 */
public final class UnifiedToolCatalog {

    public static final List<String> SCHEMA_V1_TOOLS = List.of(
            "searchServices", "checkAvailability", "requestQuotation",
            "requestConsultation", "submitMedicalRecords");

    public static final Set<String> SCHEMA_V1_TOOL_SET = Set.copyOf(SCHEMA_V1_TOOLS);

    private UnifiedToolCatalog() {
    }
}
