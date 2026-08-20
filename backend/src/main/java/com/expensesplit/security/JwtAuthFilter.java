package com.expensesplit.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Autentica la peticion a partir del access token de la cabecera
 * Authorization.
 *
 * <p>El filtro no rechaza por su cuenta: cuando el token no sirve, deja el
 * contexto de seguridad vacio y anota el motivo para que
 * {@link RestAuthenticationEntryPoint} construya la respuesta. Asi los 401
 * salen de un unico sitio, con un unico formato, y las rutas publicas siguen
 * atendiendose aunque llegue un token invalido.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    /** Clave con la que se transporta el motivo del fallo hasta el entry point. */
    private static final String FAILURE_ATTRIBUTE = "jwt.auth.failure";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token == null) {
            // Sin credenciales. Puede ser una ruta publica, asi que se sigue
            // y sera la configuracion de seguridad quien decida.
            request.setAttribute(FAILURE_ATTRIBUTE, AuthFailure.MISSING);
            filterChain.doFilter(request, response);
            return;
        }

        switch (jwtTokenProvider.validate(token)) {
            case VALID -> autenticar(request, token);
            case EXPIRED -> request.setAttribute(FAILURE_ATTRIBUTE, AuthFailure.EXPIRED);
            case INVALID -> request.setAttribute(FAILURE_ATTRIBUTE, AuthFailure.INVALID);
        }

        filterChain.doFilter(request, response);
    }

    private void autenticar(HttpServletRequest request, String token) {
        try {
            UserDetails userDetails = userDetailsService
                    .loadUserByUsername(jwtTokenProvider.getEmailFromToken(token));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (UsernameNotFoundException e) {
            // Token bien firmado de una cuenta que ya no existe: por ejemplo
            // borrada mientras su token seguia vigente.
            request.setAttribute(FAILURE_ATTRIBUTE, AuthFailure.INVALID);
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);

        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /** Motivo por el que la peticion no quedo autenticada. */
    public enum AuthFailure {

        MISSING("Se requiere autenticacion para acceder a este recurso", "Bearer"),

        /**
         * Situacion normal, no un ataque: el cliente debe renovar con su
         * refresh token y reintentar.
         */
        EXPIRED("El token de acceso ha caducado",
                "Bearer error=\"invalid_token\", error_description=\"The access token expired\""),

        INVALID("El token de acceso no es valido",
                "Bearer error=\"invalid_token\"");

        private final String message;
        private final String challenge;

        AuthFailure(String message, String challenge) {
            this.message = message;
            this.challenge = challenge;
        }

        public String message() {
            return message;
        }

        /** Valor de la cabecera WWW-Authenticate, segun RFC 6750. */
        public String challenge() {
            return challenge;
        }
    }

    /**
     * Recupera el motivo anotado durante el filtrado. Si no hay ninguno, la
     * peticion no paso por aqui con credenciales.
     */
    public static AuthFailure failureOf(HttpServletRequest request) {
        Object failure = request.getAttribute(FAILURE_ATTRIBUTE);
        return failure instanceof AuthFailure af ? af : AuthFailure.MISSING;
    }
}
