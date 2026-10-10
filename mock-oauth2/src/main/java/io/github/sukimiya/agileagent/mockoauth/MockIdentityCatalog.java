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
                new Identity("platform-admin", "Platform Admin", "tenant-platform", "Platform Tenant",
                        "platform-root", "Platform Root", Set.of("platform_admin"),
                        Set.of("platform-root", "tenant-a-root", "tenant-b-root"),
                        Set.of("platform-root", "tenant-a-root", "tenant-a-team-1", "tenant-b-root",
                                "tenant-b-team-1")),
                new Identity("tenant-a-admin", "Tenant A Admin", "tenant-a", "Tenant A",
                        "tenant-a-team-1", "Tenant A Team 1", Set.of("tenant_admin"),
                        Set.of("tenant-a-root", "tenant-a-team-1", "tenant-a-team-2"),
                        Set.of("tenant-a-root", "tenant-a-team-1", "tenant-a-team-2")),
                new Identity("tenant-a-user", "Tenant A User", "tenant-a", "Tenant A",
                        "tenant-a-team-1", "Tenant A Team 1", Set.of("user"),
                        Set.of("tenant-a-team-1"), Set.of("tenant-a-team-1", "tenant-a-team-2")),
                new Identity("tenant-b-admin", "Tenant B Admin", "tenant-b", "Tenant B",
                        "tenant-b-team-1", "Tenant B Team 1", Set.of("tenant_admin"),
                        Set.of("tenant-b-root", "tenant-b-team-1", "tenant-b-team-2"),
                        Set.of("tenant-b-root", "tenant-b-team-1", "tenant-b-team-2")),
                new Identity("tenant-b-user", "Tenant B User", "tenant-b", "Tenant B",
                        "tenant-b-team-1", "Tenant B Team 1", Set.of("user"),
                        Set.of("tenant-b-team-1"), Set.of("tenant-b-team-1", "tenant-b-team-2"))));
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
