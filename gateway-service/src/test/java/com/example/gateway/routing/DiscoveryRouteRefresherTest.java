package com.example.gateway.routing;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springdoc.core.properties.SwaggerUiConfigProperties;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.util.ServletRequestPathUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscoveryRouteRefresherTest {

    private final DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
    private final GatewayRoutesFunction routes = new GatewayRoutesFunction();
    private final SwaggerUiConfigProperties swaggerUiConfig = new SwaggerUiConfigProperties();
    private DiscoveryRouteRefresher refresher;

    @BeforeEach
    void setUp() {
        refresher = new DiscoveryRouteRefresher(discoveryClient, routes, swaggerUiConfig);
    }

    @Test
    void routesOnlyServicesThatPublishGatewayPaths() {
        when(discoveryClient.getServices()).thenReturn(List.of("user-service", "secret-service"));
        when(discoveryClient.getInstances("user-service")).thenReturn(List.of(
                instance("user-service", Map.of(DiscoveryRouteRefresher.PATHS_METADATA_KEY, "/api/users/**"))));
        when(discoveryClient.getInstances("secret-service")).thenReturn(List.of(
                instance("secret-service", Map.of())));

        refresher.rebuild();

        assertThat(routeMatches("/api/users/42")).isTrue();
        assertThat(routeMatches("/api/secret/42")).isFalse();
        // an opted-in service also gets the aggregated-docs proxy route + a Swagger UI dropdown entry
        assertThat(routeMatches("/docs/user-service/v3/api-docs")).isTrue();
        assertThat(swaggerUiConfig.getUrls()).extracting("name").containsExactly("user-service");
        assertThat(swaggerUiConfig.getUrls()).extracting("url").containsExactly("/docs/user-service/v3/api-docs");
    }

    @Test
    void multiPrefixServiceGetsARoutePerCommaSeparatedPattern() {
        when(discoveryClient.getServices()).thenReturn(List.of("chat-service"));
        when(discoveryClient.getInstances("chat-service")).thenReturn(List.of(instance("chat-service",
                Map.of(DiscoveryRouteRefresher.PATHS_METADATA_KEY, "/api/chat/**,/api/conversations/**"))));

        refresher.rebuild();

        assertThat(routeMatches("/api/chat/messages")).isTrue();
        assertThat(routeMatches("/api/conversations/1")).isTrue();
    }

    @Test
    void docsRouteAndSwaggerEntrySuppressedWhenGatewayDocsFalse() {
        when(discoveryClient.getServices()).thenReturn(List.of("user-service"));
        when(discoveryClient.getInstances("user-service")).thenReturn(List.of(instance("user-service", Map.of(
                DiscoveryRouteRefresher.PATHS_METADATA_KEY, "/api/users/**",
                DiscoveryRouteRefresher.DOCS_METADATA_KEY, "false"))));

        refresher.rebuild();

        assertThat(routeMatches("/api/users/42")).isTrue();
        assertThat(routeMatches("/docs/user-service/v3/api-docs")).isFalse();
        assertThat(swaggerUiConfig.getUrls()).isNullOrEmpty();
    }

    @Test
    void emptyRegistryLeavesAnEmptyButUsableRouteTable() {
        when(discoveryClient.getServices()).thenReturn(List.of());

        refresher.rebuild();

        assertThat(routeMatches("/api/users/42")).isFalse();
    }

    private static ServiceInstance instance(String serviceId, Map<String, String> metadata) {
        return new DefaultServiceInstance(serviceId + "-1", serviceId, "127.0.0.1", 8080, false, metadata);
    }

    private boolean routeMatches(String path) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", path);
        ServletRequestPathUtils.parseAndCache(servletRequest);
        ServerRequest request = ServerRequest.create(servletRequest, List.of());
        Optional<HandlerFunction<ServerResponse>> handler = routes.route(request);
        return handler.isPresent();
    }
}
