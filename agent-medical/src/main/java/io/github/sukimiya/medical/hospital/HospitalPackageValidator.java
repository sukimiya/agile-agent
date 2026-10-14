package io.github.sukimiya.medical.hospital;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Code-layer schema enforcement (spec §4): a package row is acceptable only when identity,
 * provenance, validity window, schema version, tool set, and per-transport required fields
 * all conform. Returns the violation list — empty means valid. Plan 3 plugs this into the
 * six-gate Validator pipeline before publication.
 */
public final class HospitalPackageValidator {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Hospital codes become tool-name prefixes ("code_tool"), so they may not contain underscores. */
    public static final Pattern HOSPITAL_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9]*$");

    private final HospitalPackageCodec codec;

    public HospitalPackageValidator(HospitalPackageCodec codec) {
        this.codec = codec;
    }

    public List<String> validate(HospitalPackageRow row) {
        List<String> violations = new ArrayList<>();
        if (row.hospitalCode() == null || !HOSPITAL_CODE_PATTERN.matcher(row.hospitalCode()).matches()) {
            violations.add("hospital_code must match ^[a-z][a-z0-9]*$");
        }
        if (isBlank(row.hospitalName())) {
            violations.add("hospital_name must not be blank");
        }
        if (isBlank(row.routingDescription())) {
            violations.add("routing_description must not be blank");
        }
        if (isBlank(row.source())) {
            violations.add("source (provenance) must not be blank");
        }
        if (row.validFrom() == null || row.validUntil() == null) {
            violations.add("valid_from and valid_until are required");
        } else if (!row.validUntil().isAfter(row.validFrom())) {
            violations.add("valid_until must be after valid_from");
        }
        if (row.schemaVersion() != CURRENT_SCHEMA_VERSION) {
            violations.add("unsupported schema_version: " + row.schemaVersion());
        }
        Optional<HospitalPackageContent> parsed = codec.parse(row.content());
        if (parsed.isEmpty()) {
            violations.add("content is not valid package JSON");
            return List.copyOf(violations);
        }
        HospitalPackageContent content = parsed.get();
        if (content.schemaVersion() != row.schemaVersion()) {
            violations.add("content schemaVersion disagrees with row schema_version");
        }
        List<String> names = content.tools().stream().map(HospitalToolSpec::name).toList();
        if (names.size() != UnifiedToolCatalog.SCHEMA_V1_TOOLS.size()
                || !new HashSet<>(names).equals(UnifiedToolCatalog.SCHEMA_V1_TOOL_SET)) {
            violations.add("schema v1 requires exactly the unified tool set "
                    + UnifiedToolCatalog.SCHEMA_V1_TOOLS);
        }
        for (HospitalToolSpec tool : content.tools()) {
            validateTool(tool, violations);
        }
        return List.copyOf(violations);
    }

    private static void validateTool(HospitalToolSpec tool, List<String> violations) {
        String label = "tool " + tool.name();
        if (isBlank(tool.description())) {
            violations.add(label + ": description must not be blank");
        }
        if (tool.parametersSchema() == null || !tool.parametersSchema().isObject()) {
            violations.add(label + ": parametersSchema must be a JSON object");
        }
        TransportBinding binding = tool.transport();
        if (binding == null || binding.type() == null) {
            violations.add(label + ": transport binding with a type is required");
            return;
        }
        switch (binding.type()) {
            case API, PORTAL -> {
                if (!isAbsoluteHttpUrl(binding.url())) {
                    violations.add(label + ": " + binding.type()
                            + " transport requires an absolute http(s) url");
                }
            }
            case HUMAN -> {
                if (isBlank(binding.channel())) {
                    violations.add(label + ": HUMAN transport requires a channel");
                }
            }
            case EMAIL -> {
                if (isBlank(binding.address())) {
                    violations.add(label + ": EMAIL transport requires an address");
                }
            }
            default -> violations.add(label + ": unknown transport type " + binding.type());
        }
    }

    private static boolean isAbsoluteHttpUrl(String url) {
        if (isBlank(url)) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            return uri.isAbsolute() && uri.getHost() != null
                    && ("http".equalsIgnoreCase(uri.getScheme())
                            || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
