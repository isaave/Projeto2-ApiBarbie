package com.barbie.dreamworld_api.versioning;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Interceptor que lê o header X-API-Version e:
 *  - Define v2 como padrão quando o header está ausente
 *  - Rejeita versões desconhecidas com 400
 *  - Ecoa a versão utilizada no header de resposta
 */
@Slf4j
@Component
public class ApiVersionInterceptor implements HandlerInterceptor {

    private static final Set<String> SUPPORTED = Set.of("v1", "v2");

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String version = request.getHeader("X-API-Version");

        if (version == null || version.isBlank()) {
            response.setHeader("X-API-Version", "v2");
            return true;
        }

        if (!SUPPORTED.contains(version.toLowerCase())) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                "{\"status\":400,\"error\":\"Bad Request\",\"message\":\"Versão inválida: '"
                + version + "'. Versões suportadas: v1, v2.\"}"
            );
            return false;
        }

        log.info("X-API-Version={} path={}", version, request.getRequestURI());
        response.setHeader("X-API-Version", version.toLowerCase());
        return true;
    }
}
