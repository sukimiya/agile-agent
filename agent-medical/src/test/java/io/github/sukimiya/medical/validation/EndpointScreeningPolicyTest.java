package io.github.sukimiya.medical.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;

import org.junit.jupiter.api.Test;

class EndpointScreeningPolicyTest {

    private final EndpointScreeningPolicy policy = new EndpointScreeningPolicy(Set.of());

    @Test
    void rejectsNonHttpSchemes() {
        assertThat(policy.permits("ftp://demo.example/files")).isFalse();
        assertThat(policy.permits("file:///etc/passwd")).isFalse();
        assertThat(policy.permits("gopher://127.0.0.1:25/x")).isFalse();
    }

    @Test
    void rejectsLoopbackPrivateLinkLocalAndMetadataAddresses() {
        for (String url : new String[]{
                "http://127.0.0.1/x", "http://127.0.0.1:8080/x", "http://localhost/x",
                "http://10.0.0.5/x", "http://192.168.1.1/x", "http://172.16.0.1/x",
                "http://169.254.169.254/latest/meta-data", "http://[::1]/x"}) {
            assertThat(policy.permits(url)).as("url %s", url).isFalse();
        }
    }

    @Test
    void acceptsPublicAddresses() {
        var publicResolver = new EndpointScreeningPolicy(Set.of(),
                host -> new InetAddress[]{address("93.184.216.34")});
        assertThat(publicResolver.permits("https://example.com/hospital/api")).isTrue();
    }

    @Test
    void rejectsPublicHostnameResolvingToPrivateAddress() {
        // Review focus #3: DNS-rebinding shape — the resolved address must be re-checked.
        var rebindingResolver = new EndpointScreeningPolicy(Set.of(),
                host -> new InetAddress[]{address("10.1.2.3")});
        assertThat(rebindingResolver.permits("https://public-looking.example/x")).isFalse();

        var mixed = new EndpointScreeningPolicy(Set.of(),
                host -> new InetAddress[]{address("93.184.216.34"), address("127.0.0.1")});
        assertThat(mixed.permits("https://dual-a.example/x")).isFalse();
    }

    @Test
    void rejectsIpv6UniqueLocalAddresses() {
        var ula = new EndpointScreeningPolicy(Set.of(), host -> new InetAddress[]{address("fc00::1")});
        assertThat(ula.permits("http://[fd12:3456::1]/x")).isFalse();
    }

    @Test
    void allowlistBypassesScreeningForOperatorNamedHosts() {
        var allowlisted = new EndpointScreeningPolicy(Set.of("LOCALHOST", "internal.corp"));
        assertThat(allowlisted.permits("http://localhost:8080/x")).isTrue();
        // Case-insensitive, and the resolver is never consulted for an allowlisted host.
        var throwing = new EndpointScreeningPolicy(Set.of("internal.corp"), host -> {
            throw new IllegalStateException("resolver must not be consulted");
        });
        assertThat(throwing.permits("https://internal.corp/api")).isTrue();
    }

    @Test
    void rejectsUnresolvableHosts() {
        var noDns = new EndpointScreeningPolicy(Set.of(), host -> {
            throw new IllegalArgumentException(new UnknownHostException(host));
        });
        assertThat(noDns.permits("https://never-resolves.invalid/x")).isFalse();

        var empty = new EndpointScreeningPolicy(Set.of(), host -> new InetAddress[0]);
        assertThat(empty.permits("https://empty.example/x")).isFalse();
    }

    @Test
    void rejectsNullBlankRelativeAndMalformedUrls() {
        assertThat(policy.permits(null)).isFalse();
        assertThat(policy.permits("")).isFalse();
        assertThat(policy.permits("   ")).isFalse();
        assertThat(policy.permits("demo.example/no-scheme")).isFalse();
        assertThat(policy.permits("http://")).isFalse();
        assertThat(policy.permits("http://not a uri %%%")).isFalse();
    }

    @Test
    void permitsRejectsLoopbackWithoutAllowlistEntry() {
        // Review focus #2: an empty allowlist means default-deny, including local dev loopback.
        assertThat(new EndpointScreeningPolicy(Set.of()).permits("http://127.0.0.1:9999/x")).isFalse();
        assertThat(new EndpointScreeningPolicy(null).permits("http://127.0.0.1:9999/x")).isFalse();
    }

    private static InetAddress address(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }
}
