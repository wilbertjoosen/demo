package com.example.gateway.routing;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * The gateway's live route table, rebuilt from the Eureka registry by {@link DiscoveryRouteRefresher}.
 *
 * <p>This gateway build (spring-cloud-gateway-server-webmvc) has no {@code DiscoveryClient} route
 * locator — that is a WebFlux-gateway-only feature — so the dynamic route table is contributed as a
 * plain {@link RouterFunction} bean, which Spring MVC's {@code RouterFunctionMapping} picks up
 * alongside the (now empty) config-properties one.
 *
 * <p>{@code RouterFunctionMapping} calls {@link #route(ServerRequest)} on every request without
 * caching path &rarr; handler, so replacing {@link #delegate} (a {@code volatile} reference to a
 * fully-built function) atomically re-points the whole gateway with no restart and no locking on the
 * request path. This mirrors how Spring Cloud Gateway's own {@code DelegatingRouterFunction} keeps a
 * refreshable route table behind a stable bean.
 */
@Component
public class GatewayRoutesFunction implements RouterFunction<ServerResponse> {

    /**
     * Matches nothing — the state before the first registry fetch, and the fallback when no service
     * opts in. Keeping a non-null delegate means {@link #route} / {@link #accept} never branch.
     */
    private static final RouterFunction<ServerResponse> EMPTY =
            RouterFunctions.route(request -> false, request -> ServerResponse.notFound().build());

    private volatile RouterFunction<ServerResponse> delegate = EMPTY;

    void update(RouterFunction<ServerResponse> next) {
        this.delegate = (next != null) ? next : EMPTY;
    }

    @Override
    public Optional<HandlerFunction<ServerResponse>> route(ServerRequest request) {
        return delegate.route(request);
    }

    @Override
    public void accept(RouterFunctions.Visitor visitor) {
        delegate.accept(visitor);
    }
}
