package com.lightflare.server.tools.httpclient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
public class HttpClientService {

    private final RestClient restClient;
    private final HttpClientProperties properties;

    public HttpClientService(RestClient restClient, HttpClientProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public HttpResponse executeGet(String url, Map<String, String> headers) {
        return execute(HttpMethod.GET, url, null, headers);
    }

    public HttpResponse executePost(String url, String body, Map<String, String> headers) {
        return execute(HttpMethod.POST, url, body, headers);
    }

    public HttpResponse executePut(String url, String body, Map<String, String> headers) {
        return execute(HttpMethod.PUT, url, body, headers);
    }

    public HttpResponse executeDelete(String url, String body, Map<String, String> headers) {
        return execute(HttpMethod.DELETE, url, body, headers);
    }

    public HttpResponse execute(HttpMethod method, String url, String body, Map<String, String> headers) {
        URI uri = validateUri(url);
        try {
            var request = restClient.method(method)
                    .uri(uri)
                    .headers((httpHeaders) -> addHeaders(httpHeaders, headers));

            if (body != null && !body.isEmpty()) {
                if (!hasContentType(headers)) {
                    request = request.contentType(MediaType.APPLICATION_JSON);
                }
                request = request.body(body);
            }

            return request.exchange((clientRequest, response) -> new HttpResponse(
                    response.getStatusCode().value(),
                    StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8)
            ));
        } catch (Exception e) {
            log.error("{} request error: {}", method, url, e);
            return new HttpResponse(500, "Error: " + e.getMessage());
        }
    }

    private void addHeaders(HttpHeaders httpHeaders, Map<String, String> headers) {
        if (headers != null) {
            headers.forEach(httpHeaders::set);
        }
        if (!httpHeaders.containsHeader(HttpHeaders.ACCEPT)) {
            httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
        }
    }

    private boolean hasContentType(Map<String, String> headers) {
        return headers != null && headers.keySet().stream()
                .anyMatch(header -> HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(header));
    }

    private URI validateUri(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("url must be a valid HTTP or HTTPS URL", e);
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("url must use http or https");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("url must not include user info");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("url must include a host");
        }
        String normalizedHost = IDN.toASCII(host).toLowerCase(Locale.ROOT);
        validateAllowedHost(normalizedHost);
        validateNetworkTarget(normalizedHost);
        return uri;
    }

    private void validateAllowedHost(String normalizedHost) {
        if (properties.allowedHosts().isEmpty()) {
            return;
        }
        boolean allowed = properties.allowedHosts().stream()
                .anyMatch(allowedHost -> hostMatches(normalizedHost, allowedHost));
        if (!allowed) {
            throw new IllegalArgumentException("url host is not allowed: " + normalizedHost);
        }
    }

    private boolean hostMatches(String host, String allowedHost) {
        if (allowedHost.startsWith("*.")) {
            String suffix = allowedHost.substring(1);
            return host.endsWith(suffix) && host.length() > suffix.length();
        }
        return host.equals(allowedHost);
    }

    private void validateNetworkTarget(String normalizedHost) {
        if (properties.allowPrivateNetwork()) {
            return;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(normalizedHost)) {
                if (isPrivateAddress(address)) {
                    throw new IllegalArgumentException("url host resolves to a private or local address: " + normalizedHost);
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("url host could not be resolved: " + normalizedHost, e);
        }
    }

    private boolean isPrivateAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isSharedIpv4Address(address)
                || isUniqueLocalIpv6Address(address)
                || isCloudMetadataAddress(address);
    }

    private boolean isSharedIpv4Address(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 4
                && (bytes[0] & 0xff) == 100
                && (bytes[1] & 0xc0) == 64;
    }

    private boolean isUniqueLocalIpv6Address(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    private boolean isCloudMetadataAddress(InetAddress address) {
        return Set.of("169.254.169.254", "100.100.100.200").contains(address.getHostAddress());
    }

    public record HttpResponse(int statusCode, String body) {
    }
}
