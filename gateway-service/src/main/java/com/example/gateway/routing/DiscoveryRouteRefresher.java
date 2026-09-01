package com.example.gateway.routing;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties.SwaggerUrl;
import org.springdoc.core.properties.SwaggerUiConfigProperties;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.client.discovery.event.HeartbeatMonitor;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

/**
 * Builds the gateway's route table from the Eureka registry and pushes it into
 * {@link GatewayRoutesFunction}.
 *
 * <p>Each backend service declares the public path prefix(es) it owns as Eureka instance metadata:
 * <pre>
 * eureka:
 *   instance:
 *     metadata-map:
 *       "gateway.paths": /api/users/**          # comma-separated for services that own several
 * </pre>
 * For every registered service that publishes {@code gateway.paths} this creates a load-balanced
 * proxy route per pattern ({@code lb://<service-id>}), plus — unless the service also sets
 * {@code "gateway.docs": false} — a {@code /docs/<service-id>/v3/api-docs} route that rewrites onto
 * the service's own {@code /v3/api-docs}, and a matching entry in this gateway's aggregated Swagger UI
 * dropdown. Services with no {@code gateway.paths} metadata (infra, the WebSocket-only
 * notification-service) are deliberately left unrouted.
 *
 * <p>Rebuilt eagerly at startup, again on {@link ApplicationReadyEvent} /
 * {@link InstanceRegisteredEvent}, on every Eureka registry cache refresh ({@link HeartbeatEvent}),
 * and on {@code POST /actuator/refresh} ({@link RefreshScopeRefreshedEvent}) — so adding, removing or
 * re-pathing a service never touches the gateway.
 */
@Component
public class DiscoveryRouteRefresher implements InitializingBean {

    static final String PATHS_METADATA_KEY = "gateway.paths";
    static final String PATHS_METADATA_KEY_ALT = "gateway-paths";
    static final String DOCS_METADATA_KEY = "gateway.docs";

    private static final String DOCS_ROUTE_PREFIX = "/docs/";
    private static final String DOCS_ROUTE_SUFFIX = "/v3/api-docs";
    private static final String SERVICE_DOCS_PATH = "/v3/api-docs";

    private final Log log = LogFactory.getLog(getClass());
    private final HeartbeatMonitor heartbeatMonitor = new HeartbeatMonitor();

    private final DiscoveryClient discoveryClient;
    private final GatewayRoutesFunction routes;
    private final SwaggerUiConfigProperties swaggerUiConfig;

    DiscoveryRouteRefresher(DiscoveryClient discoveryClient, GatewayRoutesFunction routes,
            SwaggerUiConfigProperties swaggerUiConfig) {
        this.discoveryClient = discoveryClient;
        this.routes = routes;
        this.swaggerUiConfig = swaggerUiConfig;
    }

    @Override
    public void afterPropertiesSet() {
        // Eager first build so the route table exists the moment the web server accepts traffic. The
        // registry cache may still be empty this early; the events below fill it in.
        rebuild();
    }

    @EventListener({ ApplicationReadyEvent.class, InstanceRegisteredEvent.class, RefreshScopeRefreshedEvent.class })
    void onLifecycleEvent() {
        rebuild();
    }

    @EventListener
    void onHeartbeat(HeartbeatEvent event) {
        // HeartbeatEvent fires on every Eureka registry-cache refresh; only rebuild when the registry
        // state token actually changed.
        if (heartbeatMonitor.update(event.getValue())) {
            rebuild();
        }
    }

    synchronized void rebuild() {
        try {
            List<RouterFunction<ServerResponse>> built = new ArrayList<>();
            Set<SwaggerUrl> swaggerUrls = new LinkedHashSet<>();

            List<String> serviceIds = new ArrayList<>(discoveryClient.getServices());
            serviceIds.sort(String::compareTo);

            for (String serviceId : serviceIds) {
                List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
                if (instances.isEmpty()) {
                    continue;
                }
                Map<String, String> metadata = instances.get(0).getMetadata();
                String rawPaths = firstNonBlank(metadata.get(PATHS_METADATA_KEY), metadata.get(PATHS_METADATA_KEY_ALT));
                if (rawPaths == null) {
                    continue; // explicit opt-in: no gateway.paths metadata -> not exposed through the gateway
                }

                for (String pattern : rawPaths.split(",")) {
                    String trimmed = pattern.trim();
                    if (!trimmed.isEmpty()) {
                        built.add(route(serviceId + " " + trimmed)
                                .route(path(trimmed), http())
                                .filter(lb(serviceId))
                                .build());
                    }
                }

                if (!"false".equalsIgnoreCase(metadata.getOrDefault(DOCS_METADATA_KEY, "true"))) {
                    String docsRoute = DOCS_ROUTE_PREFIX + serviceId + DOCS_ROUTE_SUFFIX;
                    built.add(route(serviceId + " docs")
                            .route(path(docsRoute), http())
                            .before(setPath(SERVICE_DOCS_PATH))
                            .filter(lb(serviceId))
                            .build());
                    swaggerUrls.add(new SwaggerUrl(serviceId, docsRoute, serviceId));
                }
            }

            RouterFunction<ServerResponse> composite = null;
            for (RouterFunction<ServerResponse> rf : built) {
                composite = (composite == null) ? rf : composite.and(rf);
            }

            routes.update(composite);
            swaggerUiConfig.setUrls(swaggerUrls);
            log.info("Gateway route table rebuilt from Eureka: " + built.size() + " route(s), "
                    + swaggerUrls.size() + " documented service(s)");
        }
        catch (RuntimeException ex) {
            // A registry hiccup must not tear down the routes we are already serving.
            log.warn("Failed to rebuild gateway routes from discovery; keeping the current table", ex);
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return (b != null && !b.isBlank()) ? b : null;
    }
}
