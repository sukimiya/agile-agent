package io.github.sukimiya.agileagent.auth;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;

/** Maps OAuth2 userinfo claims to the application identity contract. */
public interface OAuth2IdentityMapper {

    IdentityContext map(JsonNode userInfo);
}
