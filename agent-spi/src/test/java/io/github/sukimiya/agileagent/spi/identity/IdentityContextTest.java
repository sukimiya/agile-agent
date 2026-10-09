package io.github.sukimiya.agileagent.spi.identity;

import static org.assertj.core.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class IdentityContextTest {

    @Test
    void takesImmutableDefensiveCopiesOfEverySet() {
        var roles = new HashSet<>(Set.of("reader"));
        var manageable = new HashSet<>(Set.of("org-1"));
        var readable = new HashSet<>(Set.of("org-2"));
        var identity = identity("subject-1", roles, manageable, readable);

        roles.clear();
        manageable.clear();
        readable.clear();

        assertThat(identity.roles()).containsExactly("reader");
        assertThat(identity.manageableOrgIds()).containsExactly("org-1");
        assertThat(identity.readableOrgIds()).containsExactly("org-2");
        assertThatThrownBy(() -> identity.roles().add("admin"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> identity.manageableOrgIds().add("org-3"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> identity.readableOrgIds().add("org-3"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullSets() {
        assertThatNullPointerException().isThrownBy(() -> identity("subject-1", null, Set.of(), Set.of()));
        assertThatNullPointerException().isThrownBy(() -> identity("subject-1", Set.of(), null, Set.of()));
        assertThatNullPointerException().isThrownBy(() -> identity("subject-1", Set.of(), Set.of(), null));
    }

    @Test
    void rejectsMissingOrBlankSubjects() {
        for (String subject : new String[] {null, "", " \t\n"}) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> identity(subject, Set.of(), Set.of(), Set.of()));
        }
    }

    @Test
    void permitsUnmappedTenantAndPrimaryOrganization() {
        var identity = identity("subject-1", Set.of(), Set.of(), Set.of());

        assertThat(identity.tenantId()).isNull();
        assertThat(identity.tenantName()).isNull();
        assertThat(identity.primaryOrgId()).isNull();
        assertThat(identity.primaryOrgName()).isNull();
    }

    private static IdentityContext identity(String subject, Set<String> roles,
                                            Set<String> manageable, Set<String> readable) {
        return new IdentityContext(subject, "User", null, null, null, null,
                roles, manageable, readable, false, false);
    }
}
