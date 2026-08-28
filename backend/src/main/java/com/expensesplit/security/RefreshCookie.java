package com.expensesplit.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * La cookie que transporta el refresh token, en un unico sitio.
 *
 * <h2>Por que una cookie y no el cuerpo JSON</h2>
 * Antes el refresh token viajaba en la respuesta y el cliente tenia que
 * guardarlo en algun lado. La unica opcion que sobrevive a una recarga de
 * pagina es {@code localStorage}, que <b>cualquier XSS puede leer</b>. Y lo
 * que se lleva no es un access token de 15 minutos: es una credencial de
 * treinta dias, renovable. Guardar el access token en memoria mientras el
 * refresh esta en localStorage no protege de nada; solo lo parece.
 *
 * <p>Marcada {@code HttpOnly}, el token deja de ser accesible desde
 * JavaScript. Un XSS puede seguir haciendo peticiones en nombre del usuario
 * mientras la pagina este abierta, pero ya no puede <b>exfiltrar</b> una
 * credencial de larga duracion y volver mas tarde desde otra maquina.
 *
 * <h2>Por que se acota el Path</h2>
 * {@code /api/auth} es el unico sitio donde hace falta. El resto de la API se
 * autentica con el header Authorization, asi que la cookie no se envia en
 * ninguna otra peticion: menos superficie y menos posibilidades de filtrarla
 * en un log o un proxy.
 *
 * <h2>Por que SameSite es suficiente contra CSRF</h2>
 * Una cookie viaja sola, sin que el cliente la pida, asi que un sitio ajeno
 * podria provocar peticiones a {@code /api/auth/*} en nombre del usuario.
 * {@code SameSite} lo corta en el navegador: la cookie no se adjunta a
 * peticiones originadas en otro sitio. Ademas la respuesta del refresco no es
 * legible por el atacante (CORS), y el peor efecto que lograria seria rotar un
 * token, que es una molestia, no un acceso.
 *
 * <p>Se configura por entorno: en desarrollo el frontend habla por HTTP plano
 * contra otro puerto, y una cookie {@code Secure} no se enviaria nunca.
 */
@Component
public class RefreshCookie {

    private final String name;
    private final String path;
    private final boolean secure;
    private final String sameSite;
    private final Duration maxAge;

    public RefreshCookie(
            @Value("${app.auth.refresh-cookie.name}") String name,
            @Value("${app.auth.refresh-cookie.path}") String path,
            @Value("${app.auth.refresh-cookie.secure}") boolean secure,
            @Value("${app.auth.refresh-cookie.same-site}") String sameSite,
            @Value("${jwt.refresh-token-expiration-days}") long expirationDays) {

        this.name = name;
        this.path = path;
        this.secure = secure;
        this.sameSite = sameSite;

        // La cookie no debe sobrevivir al token que transporta: si caducara
        // despues, el navegador seguiria enviando una credencial muerta y el
        // usuario veria un 401 inexplicable en vez de la pantalla de acceso.
        this.maxAge = Duration.ofDays(expirationDays);
    }

    public String getName() {
        return name;
    }

    /** Cookie de sesion abierta. */
    public ResponseCookie issue(String token) {
        return base(token).maxAge(maxAge).build();
    }

    /**
     * Cookie de borrado.
     *
     * <p>Debe repetir name, path y atributos del original: el navegador
     * identifica la cookie por esa combinacion y, si alguno no coincide,
     * en vez de sustituirla anade una segunda y la sesion no se cierra.
     */
    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    /** Lee el token de la peticion, si viene y no esta vacio. */
    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(valor -> valor != null && !valor.isBlank())
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(name, valor)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path);
    }
}
