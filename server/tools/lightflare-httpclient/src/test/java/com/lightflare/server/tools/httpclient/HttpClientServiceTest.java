package com.lightflare.server.tools.httpclient;

import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpClientServiceTest {

    @Test
    void preservesErrorStatusAndBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.com/missing"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound().body("not found"));
        HttpClientService service = service(builder.build(), allowAnyHostForMockServer());

        HttpClientService.HttpResponse response = service.executeGet("https://example.com/missing", Map.of());

        assertEquals(404, response.statusCode());
        assertEquals("not found", response.body());
        server.verify();
    }

    @Test
    void sendsObjectHeadersAndBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.com/items"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token"))
                .andExpect(header("Content-Type", "text/plain"))
                .andExpect(content().string("payload"))
                .andRespond(withSuccess("created", MediaType.TEXT_PLAIN));
        HttpClientService service = service(builder.build(), allowAnyHostForMockServer());
        HttpPostTool tool = new HttpPostTool(service);

        ToolResult result = tool.execute(List.of(
                ToolArgument.builder().name("url").value("https://example.com/items").build(),
                ToolArgument.builder().name("body").value("payload").build(),
                ToolArgument.builder().name("headers").value(Map.of(
                        "Authorization", "Bearer token",
                        "Content-Type", "text/plain"
                )).build()
        ), null);

        assertTrue(result.success());
        assertEquals("Status: 200\nResponse:\ncreated", result.content());
        server.verify();
    }

    @Test
    void rejectsMalformedHeaderJson() {
        HttpGetTool tool = new HttpGetTool(service(RestClient.create(), allowAnyHostForMockServer()));

        ToolResult result = tool.execute(List.of(
                ToolArgument.builder().name("url").value("https://example.com").build(),
                ToolArgument.builder().name("headers").value("{bad-json").build()
        ), null);

        assertFalse(result.success());
        assertEquals("headers must be a JSON object of string values", result.content());
    }

    @Test
    void rejectsPrivateNetworkTargetsByDefault() {
        HttpClientService service = service(RestClient.create(), validateNetworkTargets());

        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.executeGet("http://127.0.0.1:8080/internal", Map.of())
        );

        assertEquals("url host resolves to a private or local address: 127.0.0.1", error.getMessage());
    }

    @Test
    void rejectsHostsOutsideAllowList() {
        HttpClientService service = service(
                RestClient.create(),
                new HttpClientProperties(true, List.of(), Duration.ofSeconds(1), Duration.ofSeconds(1), true, List.of("api.example.com"))
        );

        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.executeGet("https://example.com", Map.of())
        );

        assertEquals("url host is not allowed: example.com", error.getMessage());
    }

    @Test
    void exposesIntegrationMetadata() {
        HttpClientProperties properties = new HttpClientProperties(true, List.of("http-get"), null, null, false, null);
        HttpClientIntegration integration = new HttpClientIntegration(properties);

        assertEquals("httpclient", integration.definition().getId());
        assertEquals("HTTP Client", integration.definition().getDisplayName());
        assertTrue(integration.definition().isEnabled());
        assertEquals("httpclient", properties.integrationId());
        assertEquals(Duration.ofSeconds(5), properties.connectTimeout());
        assertEquals(Duration.ofSeconds(30), properties.readTimeout());
    }

    private HttpClientService service(RestClient restClient, HttpClientProperties properties) {
        return new HttpClientService(restClient, properties);
    }

    private HttpClientProperties allowAnyHostForMockServer() {
        return new HttpClientProperties(true, List.of(), Duration.ofSeconds(1), Duration.ofSeconds(1), true, List.of());
    }

    private HttpClientProperties validateNetworkTargets() {
        return new HttpClientProperties(true, List.of(), Duration.ofSeconds(1), Duration.ofSeconds(1), false, List.of());
    }
}
