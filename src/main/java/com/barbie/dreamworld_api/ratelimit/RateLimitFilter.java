package com.barbie.dreamworld_api.ratelimit;

import com.barbie.dreamworld_api.exceptions.ErrorJsonWriter;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Filtro que aplica rate limiting por IP em todas as rotas /api/**.
 *
 * Limites:
 *   GET              → 10 req/min por IP
 *   POST/PUT/PATCH/DELETE → 5 req/min por IP
 *
 * Headers de resposta:
 *   X-Rate-Limit-Remaining          → tokens restantes
 *   Retry-After                     → segundos para o próximo token (só no 429)
 *   X-Rate-Limit-Retry-After-Seconds → igual ao Retry-After
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of(
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name()
    );

    private final RateLimitConfig rateLimitConfig;

    /**
     * Só confia em X-Forwarded-For quando o deploy declara explicitamente que
     * existe um proxy reverso de confiança (Render). Por padrão, usa remoteAddr.
     */
    @Value("${app.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui")
                || path.startsWith("/api-docs")
                || path.startsWith("/h2-console")
                || path.startsWith("/actuator")
                || !path.startsWith("/api");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String ip = resolveIp(request);
        boolean isWrite = WRITE_METHODS.contains(request.getMethod().toUpperCase());

        Bucket bucket = rateLimitConfig.resolveBucket(ip, isWrite);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }

        long retryAfter = Math.max(1,
                (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
        log.warn("Rate limit excedido — ip={} method={} retryAfter={}s",
                ip, request.getMethod(), retryAfter);

        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(retryAfter));
        ErrorJsonWriter.write(response, request, HttpStatus.TOO_MANY_REQUESTS,
                "Limite de requisições excedido. Tente novamente em " + retryAfter + " segundo(s).");
    }

    private String resolveIp(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
