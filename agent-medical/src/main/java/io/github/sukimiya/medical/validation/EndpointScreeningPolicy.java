package io.github.sukimiya.medical.validation;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * SSRF screening of curated endpoints, shared by approval (CapabilityGate) and runtime
 * (HttpApiTransportExecutor) so a host that cannot pass review also cannot be called
 * (spec §14 resolution 4). Fail closed: unparsable URLs, non-http(s) schemes, unresolvable
 * hosts, and loopback/private/link-local/multicast/ULA addresses are all refused unless the
 * host is explicitly listed in the operator-configured allowlist — the escape hatch for
 * local development. The approval system itself must never become an SSRF springboard.
 */
public final class EndpointScreeningPolicy {

    private final Set<String> allowedHosts;
    private final Function<String, InetAddress[]> resolver;

    public EndpointScreeningPolicy(Set<String> allowedHosts) {
        this(allowedHosts, EndpointScreeningPolicy::resolveAll);
    }

    /** Test seam: inject a resolver to exercise DNS-rebinding shapes without live DNS. */
    EndpointScreeningPolicy(Set<String> allowedHosts, Function<String, InetAddress[]> resolver) {
        this.allowedHosts = allowedHosts == null ? Set.of() : allowedHosts.stream()
                .map(host -> host.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.resolver = resolver;
    }

    private static InetAddress[] resolveAll(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("unresolvable host: " + host, e);
        }
    }

    /** True only for absolute http(s) URLs whose every resolved address is public. */
    public boolean permits(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!uri.isAbsolute() || uri.getHost() == null) {
            return false;
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (allowedHosts.contains(host)) {
            return true;
        }
        // Screen IP literals by address directly — never through the resolver seam or DNS —
        // so a rebinding hostname or injected resolver cannot launder an internal literal.
        String literal = stripIpv6Brackets(host);
        InetAddress[] addresses;
        if (isIpLiteral(literal)) {
            try {
                addresses = new InetAddress[]{InetAddress.getByName(literal)};
            } catch (UnknownHostException e) {
                return false;
            }
        } else {
            try {
                addresses = resolver.apply(host);
            } catch (RuntimeException e) {
                return false;
            }
        }
        if (addresses == null || addresses.length == 0) {
            return false;
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                return false;
            }
        }
        return true;
    }

    private static String stripIpv6Brackets(String host) {
        return (host.startsWith("[") && host.endsWith("]"))
                ? host.substring(1, host.length() - 1)
                : host;
    }

    private static boolean isIpLiteral(String host) {
        return host.matches("\\d{1,3}(\\.\\d{1,3}){3}") || host.indexOf(':') >= 0;
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        // IPv6 unique-local (fc00::/7): isSiteLocalAddress only covers the IPv4 shapes.
        return !(bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC);
    }
}
