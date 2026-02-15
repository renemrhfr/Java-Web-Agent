package com.renemrhfr.javawebagent.security;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates navigation targets to prevent SSRF and access to internal networks.
 *
 * <p>Blocks non-HTTP(S) schemes, localhost, IP literals, local/internal
 * domain suffixes, and domains that resolve to private or loopback
 * addresses.</p>
 *
 */
public final class NavigationSecurity {
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Set<String> BLOCKED_HOST_SUFFIXES = Set.of(".localhost", ".local", ".internal", ".localdomain", ".home", ".lan");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("^(?=.{1,253}$)(?!-)[a-z0-9-]{1,63}(?<!-)(\\.(?!-)[a-z0-9-]{1,63}(?<!-))+$");
    private static final Pattern IPV4_PATTERN = Pattern.compile("^\\d{1,3}(?:\\.\\d{1,3}){3}$");

    private NavigationSecurity() {
    }

    /**
     * Validates that the given URL is safe to navigate to.
     *
     * @param rawUrl the URL to validate
     * @throws IllegalArgumentException if the URL is blocked
     */
    public static void assertAllowed(String rawUrl) {
        URI uri;
        try {
            uri = URI.create(rawUrl == null ? "" : rawUrl.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid url");
        }

        String scheme = normalize(uri.getScheme());
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new IllegalArgumentException("only http/https urls are allowed");
        }

        String host = normalize(uri.getHost());
        if (host.isBlank()) {
            throw new IllegalArgumentException("url host must be a valid domain");
        }
        if (host.equals("localhost") || isIpLiteral(host)) {
            throw new IllegalArgumentException("direct ip and localhost targets are blocked");
        }
        for (String suffix : BLOCKED_HOST_SUFFIXES) {
            if (host.endsWith(suffix)) {
                throw new IllegalArgumentException("local/internal domains are blocked");
            }
        }

        String asciiHost;
        try {
            asciiHost = IDN.toASCII(host).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("url host must be a valid domain");
        }
        if (!DOMAIN_PATTERN.matcher(asciiHost).matches()) {
            throw new IllegalArgumentException("url host must be a valid domain");
        }

        ensureNotPrivateResolution(asciiHost);
    }

    private static void ensureNotPrivateResolution(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("cannot resolve target domain");
        }
        if (addresses.length == 0) {
            throw new IllegalArgumentException("cannot resolve target domain");
        }
        for (InetAddress address : addresses) {
            if (isPrivateAddress(address)) {
                throw new IllegalArgumentException("private or local network targets are blocked");
            }
        }
    }

    private static boolean isPrivateAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address && bytes.length == 4) {
            int b0 = Byte.toUnsignedInt(bytes[0]);
            int b1 = Byte.toUnsignedInt(bytes[1]);
            if (b0 == 0 || b0 == 10 || b0 == 127) {
                return true;
            }
            if (b0 == 169 && b1 == 254) {
                return true;
            }
            if (b0 == 172 && b1 >= 16 && b1 <= 31) {
                return true;
            }
            if (b0 == 192 && b1 == 168) {
                return true;
            }
            if (b0 == 100 && b1 >= 64 && b1 <= 127) {
                return true;
            }
            return b0 == 198 && (b1 == 18 || b1 == 19);
        }

        if (address instanceof Inet6Address && bytes.length == 16) {
            int first = Byte.toUnsignedInt(bytes[0]);
            // fc00::/7 Unique local address space.
            return (first & 0xFE) == 0xFC;
        }
        return false;
    }

    private static boolean isIpLiteral(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        if (IPV4_PATTERN.matcher(host).matches()) {
            String[] parts = host.split("\\.");
            if (parts.length != 4) {
                return false;
            }
            for (String part : parts) {
                try {
                    int n = Integer.parseInt(part);
                    if (n < 0 || n > 255) {
                        return false;
                    }
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return true;
        }
        return host.contains(":");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
