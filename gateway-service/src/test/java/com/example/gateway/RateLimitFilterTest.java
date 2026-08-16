package com.example.gateway;

import com.example.gateway.filter.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private static final String[] ALLOWED_ORIGINS = { "http://localhost:5173" };

    @Test
    void requestsWithinCapacity_areAllowedThrough() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(5, 5, 1, ALLOWED_ORIGINS);

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = apiRequest("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(chain.getRequest()).isNotNull();
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void requestBeyondCapacity_isRejectedWith429() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(3, 3, 60, ALLOWED_ORIGINS);

        for (int i = 0; i < 3; i++) {
            filter.doFilter(apiRequest("10.0.0.2"), new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletRequest request = apiRequest("10.0.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void differentClientIps_getIndependentBuckets() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 1, 60, ALLOWED_ORIGINS);

        MockHttpServletResponse firstClient = new MockHttpServletResponse();
        filter.doFilter(apiRequest("10.0.0.3"), firstClient, new MockFilterChain());
        MockHttpServletResponse secondClient = new MockHttpServletResponse();
        filter.doFilter(apiRequest("10.0.0.4"), secondClient, new MockFilterChain());

        assertThat(firstClient.getStatus()).isEqualTo(200);
        assertThat(secondClient.getStatus()).isEqualTo(200);
    }

    @Test
    void xForwardedForHeader_isUsedOverRemoteAddr() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 1, 60, ALLOWED_ORIGINS);

        MockHttpServletRequest first = apiRequest("192.168.1.1");
        first.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());

        // Same real client (203.0.113.5) behind a different proxy hop address — still shares the bucket.
        MockHttpServletRequest second = apiRequest("192.168.1.2");
        second.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(second, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void actuatorHealthEndpoint_bypassesRateLimit() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 1, 60, ALLOWED_ORIGINS);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setRemoteAddr("10.0.0.5");

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void optionsPreflight_bypassesRateLimit() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 1, 60, ALLOWED_ORIGINS);
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/orders");
        request.setRemoteAddr("10.0.0.6");

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void rejectedResponse_carriesCorsHeadersForAllowedOrigin() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 1, 60, ALLOWED_ORIGINS);
        MockHttpServletRequest first = apiRequest("10.0.0.7");
        first.addHeader("Origin", "http://localhost:5173");
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletRequest second = apiRequest("10.0.0.7");
        second.addHeader("Origin", "http://localhost:5173");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(second, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
    }

    private MockHttpServletRequest apiRequest(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
