package com.lightflare.server.tools.playwright;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

public class PlaywrightUrlPolicy {

    private final PlaywrightProperties properties;

    public PlaywrightUrlPolicy(PlaywrightProperties properties) {
        this.properties = properties;
    }

    public URI validateNavigationTarget(String rawUrl) {
        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid URL");
        }

        String scheme = normalized(uri.getScheme());
        if (!properties.getAllowedSchemes().contains(scheme)) {
            throw new IllegalArgumentException("URL scheme is not allowed: " + scheme);
        }

        String host = normalized(uri.getHost());
        if (host == null) {
            throw new IllegalArgumentException("URL host is required");
        }

        List<String> allowedHosts = properties.getAllowedHosts().stream()
                .map(this::normalized)
                .toList();
        if (!allowedHosts.isEmpty() && allowedHosts.stream().noneMatch(allowedHost -> hostMatches(host, allowedHost))) {
            throw new IllegalArgumentException("URL host is not allowed: " + host);
        }

        if (properties.isBlockPrivateNetworkTargets()) {
            rejectPrivateAddressHost(host);
        }

        return uri;
    }

    public boolean isAllowedRequestUrl(String rawUrl) {
        try {
            validateNavigationTarget(rawUrl);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void rejectPrivateAddressHost(String host) {
        if ("localhost".equals(host) || host.endsWith(".localhost") || host.endsWith(".local")) {
            throw new IllegalArgumentException("Local network targets are not allowed: " + host);
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isBlockedAddress(address)) {
                    throw new IllegalArgumentException("Private network targets are not allowed: " + host);
                }
            }
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Unable to resolve URL host: " + host, exception);
        }
    }

    private boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        String hostAddress = normalized(address.getHostAddress());
        if (hostAddress == null) {
            return true;
        }

        if (hostAddress.startsWith("100.64.")) {
            return true;
        }

        if (address instanceof Inet6Address) {
            return hostAddress.equals("::1")
                    || hostAddress.startsWith("fc")
                    || hostAddress.startsWith("fd")
                    || hostAddress.startsWith("fe80:");
        }

        return false;
    }

    private boolean hostMatches(String host, String allowedHost) {
        return host.equals(allowedHost) || host.endsWith("." + allowedHost);
    }

    private String normalized(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
