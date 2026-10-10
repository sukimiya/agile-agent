package io.github.sukimiya.agileagent.mockoauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Deterministic, generic identities used by the standalone mock provider. */
public final class MockIdentityCatalog {
    private final Map<String, Identity> identities;

    public MockIdentityCatalog() {
        this(List.of(
                new Identity("demo-user", "Demo User", "tenant-demo", "Demo Tenant",
                        "org-demo", "Demo Organization", Set.of("USER"),
                        Set.of("org-demo"), Set.of("org-demo", "org-demo-readonly")),
                new Identity("analyst-user", "Analyst User", "tenant-research", "Research Tenant",
                        "org-lab-a", "Laboratory A", Set.of("ANALYST"),
                        Set.of("org-lab-a"), Set.of("org-lab-a", "org-lab-b", "org-lab-c"))));
    }

    public MockIdentityCatalog(List<Identity> identities) {
        this.identities = identities.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Identity::subject, identity -> identity));
    }

    public List<Identity> identities() {
        return identities.values().stream().sorted(java.util.Comparator.comparing(Identity::subject)).toList();
    }

    public Optional<Identity> findBySubject(String subject) {
        return subject == null ? Optional.empty() : Optional.ofNullable(identities.get(subject));
    }

    /** Returns the default userinfo claim shape consumed by the server mapper. */
    public Optional<JsonNode> userInfo(String subject) {
        if (subject == null) {
            return Optional.empty();
        }
        Identity identity = identities.get(subject);
        if (identity == null) {
            return Optional.empty();
        }
        var json = JsonNodeFactory.instance.objectNode();
        json.put("sub", identity.subject());
        json.put("name", identity.displayName());
        json.put("tenant_id", identity.tenantId());
        json.put("tenant_name", identity.tenantName());
        json.putObject("primary_org")
                .put("id", identity.primaryOrgId())
                .put("name", identity.primaryOrgName());
        json.set("roles", strings(identity.roles()));
        json.set("manageable_org_ids", strings(identity.manageableOrgIds()));
        json.set("readable_org_ids", strings(identity.readableOrgIds()));
        return Optional.of(json);
    }

    private static ArrayNode strings(Set<String> values) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        values.stream().sorted().forEach(array::add);
        return array;
    }

    public record Identity(String subject, String displayName, String tenantId, String tenantName,
                           String primaryOrgId, String primaryOrgName, Set<String> roles,
                           Set<String> manageableOrgIds, Set<String> readableOrgIds) {
        public Identity {
            roles = Set.copyOf(roles);
            manageableOrgIds = Set.copyOf(manageableOrgIds);
            readableOrgIds = Set.copyOf(readableOrgIds);
        }
    }
}
