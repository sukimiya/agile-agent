package io.github.sukimiya.agileagent.auth;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.sukimiya.agileagent.config.OAuth2ClaimProperties;
import io.github.sukimiya.agileagent.config.OAuth2Properties;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import java.util.LinkedHashSet;
import java.util.Set;

/** Reads identity fields from arbitrary userinfo JSON using configured JSON Pointers. */
public final class JsonPointerOAuth2IdentityMapper implements OAuth2IdentityMapper {

    private final OAuth2ClaimProperties claims;
    private final OAuth2Properties.Roles roles;

    public JsonPointerOAuth2IdentityMapper(OAuth2Properties properties) {
        this.claims = properties.claims();
        this.roles = properties.roles();
    }

    @Override
    public IdentityContext map(JsonNode userInfo) {
        String subject = requiredText(userInfo, "subject", claims.subject(), true);
        String displayName = requiredText(userInfo, "displayName", claims.displayName(), false);
        String tenantId = optionalText(userInfo, "tenantId", claims.tenantId());
        String tenantName = optionalText(userInfo, "tenantName", claims.tenantName());
        String primaryOrgId = optionalText(userInfo, "primaryOrgId", claims.primaryOrgId());
        String primaryOrgName = optionalText(userInfo, "primaryOrgName", claims.primaryOrgName());
        Set<String> roleNames = requiredStringSet(userInfo, "roles", claims.roles());
        Set<String> manageableOrgIds = requiredStringSet(userInfo, "manageableOrgIds", claims.manageableOrgIds());
        Set<String> readableOrgIds = requiredStringSet(userInfo, "readableOrgIds", claims.readableOrgIds());

        return new IdentityContext(subject, displayName, tenantId, tenantName, primaryOrgId, primaryOrgName,
                roleNames, manageableOrgIds, readableOrgIds,
                roleNames.contains(roles.platformAdmin()), roleNames.contains(roles.tenantAdmin()));
    }

    private static String requiredText(JsonNode userInfo, String field, String pointer, boolean nonBlank) {
        JsonNode value = at(userInfo, field, pointer);
        if (value == null || value.isMissingNode() || value.isNull() || !value.isTextual()
                || (nonBlank && value.textValue().isBlank())) {
            throw invalid(field, pointer, nonBlank ? "must be a non-blank string" : "must be a string");
        }
        return value.textValue();
    }

    private static String optionalText(JsonNode userInfo, String field, String pointer) {
        JsonNode value = at(userInfo, field, pointer);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalid(field, pointer, "must be a string when present");
        }
        return value.textValue();
    }

    private static Set<String> requiredStringSet(JsonNode userInfo, String field, String pointer) {
        JsonNode value = at(userInfo, field, pointer);
        if (value == null || value.isMissingNode() || value.isNull() || !value.isArray()) {
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
