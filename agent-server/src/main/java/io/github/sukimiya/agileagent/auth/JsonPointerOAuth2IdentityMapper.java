package io.github.sukimiya.agileagent.auth;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import io.github.sukimiya.agileagent.config.OAuth2ClaimProperties;
import io.github.sukimiya.agileagent.config.OAuth2Properties;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads identity fields from arbitrary userinfo JSON using configured JSON Pointers.
 *
 * <p>When claim defaults are enabled, a missing or null tenant, primary-org, role, or org claim is
 * replaced by its configured default; present claims are still validated and always win. An
 * optional admin grant adds roles and org access to identities whose configured claim matches.
 */
@Component
public final class JsonPointerOAuth2IdentityMapper implements OAuth2IdentityMapper {

    private final OAuth2ClaimProperties claims;
    private final OAuth2Properties.Roles roles;
    private final OAuth2Properties.ClaimDefaults defaults;
    private final OAuth2Properties.AdminGrant adminGrant;

    public JsonPointerOAuth2IdentityMapper(OAuth2Properties properties) {
        this.claims = properties.claims();
        this.roles = properties.roles();
        this.defaults = properties.claimDefaults();
        this.adminGrant = properties.adminGrant();
    }

    @Override
    public IdentityContext map(JsonNode userInfo) {
        String subject = claims.subjectPrefix() + requiredSubject(userInfo, claims.subject());
        String displayName = displayName(userInfo);
        boolean useDefaults = defaults.enabled();
        String tenantId = optionalText(userInfo, "tenantId", claims.tenantId(),
                useDefaults ? defaults.tenantId() : null);
        String tenantName = optionalText(userInfo, "tenantName", claims.tenantName(),
                useDefaults ? defaults.tenantName() : null);
        String primaryOrgId = optionalText(userInfo, "primaryOrgId", claims.primaryOrgId(),
                useDefaults ? defaults.primaryOrgId() : null);
        String primaryOrgName = optionalText(userInfo, "primaryOrgName", claims.primaryOrgName(),
                useDefaults ? defaults.primaryOrgName() : null);
        Set<String> roleNames = requiredStringSet(userInfo, "roles", claims.roles(),
                useDefaults ? defaults.roles() : null);
        Set<String> manageableOrgIds = requiredStringSet(userInfo, "manageableOrgIds", claims.manageableOrgIds(),
                useDefaults ? defaults.manageableOrgIds() : null);
        Set<String> readableOrgIds = requiredStringSet(userInfo, "readableOrgIds", claims.readableOrgIds(),
                useDefaults ? defaults.readableOrgIds() : null);

        if (adminGrantMatches(userInfo)) {
            roleNames = union(roleNames, adminGrant.roles());
            manageableOrgIds = union(manageableOrgIds, adminGrant.manageableOrgIds());
            readableOrgIds = union(readableOrgIds, adminGrant.readableOrgIds());
        }

        return new IdentityContext(subject, displayName, tenantId, tenantName, primaryOrgId, primaryOrgName,
                roleNames, manageableOrgIds, readableOrgIds,
                roleNames.contains(roles.platformAdmin()), roleNames.contains(roles.tenantAdmin()));
    }

    /** A subject may be a non-blank string or an integral number (GitHub numeric {@code id}). */
    private static String requiredSubject(JsonNode userInfo, String pointer) {
        String value = scalarText(at(userInfo, "subject", pointer));
        if (value == null || value.isBlank()) {
            throw invalid("subject", pointer, "must be a non-blank string or integer");
        }
        return value;
    }

    private String displayName(JsonNode userInfo) {
        JsonNode value = at(userInfo, "displayName", claims.displayName());
        String fallback = claims.displayNameFallback();
        if (fallback != null && (absent(value) || (value.isTextual() && value.textValue().isBlank()))) {
            JsonNode alternative = at(userInfo, "displayName", fallback);
            if (absent(alternative) || !alternative.isTextual() || alternative.textValue().isBlank()) {
                throw invalid("displayName", fallback, "must be a non-blank string when used as fallback");
            }
            return alternative.textValue();
        }
        if (absent(value) || !value.isTextual()) {
            throw invalid("displayName", claims.displayName(), "must be a string");
        }
        return value.textValue();
    }

    private boolean adminGrantMatches(JsonNode userInfo) {
        if (!adminGrant.enabled()) {
            return false;
        }
        String value = scalarText(at(userInfo, "adminGrant", adminGrant.matchClaim()));
        return value != null && adminGrant.matchValues().contains(value);
    }

    private static String optionalText(JsonNode userInfo, String field, String pointer, String defaultValue) {
        JsonNode value = at(userInfo, field, pointer);
        if (absent(value)) {
            return defaultValue;
        }
        if (!value.isTextual()) {
            throw invalid(field, pointer, "must be a string when present");
        }
        return value.textValue();
    }

    private static Set<String> requiredStringSet(JsonNode userInfo, String field, String pointer,
                                                 List<String> defaultValue) {
        JsonNode value = at(userInfo, field, pointer);
        if (absent(value) && defaultValue != null) {
            return Set.copyOf(defaultValue);
        }
        if (absent(value) || !value.isArray()) {
            throw invalid(field, pointer, "must be an array of strings");
        }
        Set<String> result = new LinkedHashSet<>();
        for (JsonNode element : value) {
            if (!element.isTextual()) {
                throw invalid(field, pointer, "must be an array of strings");
            }
            result.add(element.textValue());
        }
        return Set.copyOf(result);
    }

    private static Set<String> union(Set<String> base, List<String> extra) {
        if (extra.isEmpty()) {
            return base;
        }
        Set<String> result = new LinkedHashSet<>(base);
        result.addAll(extra);
        return Set.copyOf(result);
    }

    /** Text value, or the decimal form of an integral number; null for anything else. */
    private static String scalarText(JsonNode value) {
        if (absent(value)) {
            return null;
        }
        if (value.isTextual()) {
            return value.textValue();
        }
        if (value.isIntegralNumber()) {
            return value.bigIntegerValue().toString();
        }
        return null;
    }

    private static boolean absent(JsonNode value) {
        return value == null || value.isMissingNode() || value.isNull();
    }

    private static JsonNode at(JsonNode userInfo, String field, String pointer) {
        try {
            return userInfo == null ? null : userInfo.at(JsonPointer.compile(pointer));
        } catch (IllegalArgumentException exception) {
            throw invalid(field, pointer, "is not a valid JSON Pointer");
        }
    }

    private static IdentityMappingException invalid(String field, String pointer, String reason) {
        return new IdentityMappingException(field, pointer, reason);
    }
}
