package com.saafhawa.api.ratelimit;

import com.saafhawa.api.key.ApiClient;
import com.saafhawa.api.key.ApiKeyService;
import com.saafhawa.common.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token-bucket rate limiting (FR-6.2). Anonymous clients are limited per hour by IP; keyed clients
 * per minute by key (with per-key overrides). Emits standard X-RateLimit-* headers and 429 +
 * Retry-After when exhausted.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ApiKeyService keyService;
    private final AppProperties.RateLimit limits;
    private final MeterRegistry metrics;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(ApiKeyService keyService, AppProperties props, MeterRegistry metrics) {
        this.keyService = keyService;
        this.limits = props.rateLimit();
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String apiKey = req.getHeader("X-API-Key");
        Optional<ApiClient> client = keyService.resolve(apiKey);

        String bucketKey;
        long capacity;
        Duration window;
        if (client.isPresent()) {
            ApiClient c = client.get();
            bucketKey = "key:" + c.getId();
            capacity = c.getRateLimitOverride() != null ? c.getRateLimitOverride() : limits.keyedPerMinute();
            window = Duration.ofMinutes(1);
        } else {
            bucketKey = "anon:" + clientIp(req);
            capacity = limits.anonymousPerHour();
            window = Duration.ofHours(1);
        }

        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> newBucket(capacity, window));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        resp.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
        resp.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, probe.getRemainingTokens())));

        if (!probe.isConsumed()) {
            long retryAfter = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
            resp.setHeader("Retry-After", String.valueOf(retryAfter));
            resp.setStatus(429);
            resp.setContentType("application/problem+json");
            resp.getWriter().write("""
                    {"type":"https://saafhawa.dev/problems/429","title":"Too Many Requests",\
                    "status":429,"detail":"Rate limit exceeded. Retry after %d seconds."}"""
                    .formatted(retryAfter));
            metrics.counter("saafhawa.api.ratelimit.hits").increment();
            return;
        }
        chain.doFilter(req, resp);
    }

    private Bucket newBucket(long capacity, Duration window) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, window)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
