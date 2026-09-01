package com.expensesplit.config;

import com.expensesplit.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La politica CORS que el frontend necesita para funcionar.
 *
 * <p>Estas garantias no las cubre ningun otro test porque no se ven desde el
 * servidor: la peticion se procesa igual de bien con CORS mal configurado, y
 * es el <b>navegador</b> quien decide despues que puede leer el script. Un
 * fallo aqui no rompe ninguna respuesta; rompe el cliente, y en silencio.
 */
@AutoConfigureMockMvc
class CorsPolicyTest extends AbstractIntegrationTest {

    private static final String FRONTEND = "http://localhost:5173";

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("El navegador puede leer Retry-After de un 429")
    void exponeRetryAfter() throws Exception {
        // Solo unas pocas cabeceras son legibles por defecto entre origenes
        // distintos, y Retry-After no esta entre ellas. Sin exponerla, el
        // cliente recibe el 429 pero no puede decir cuanto hay que esperar.
        mvc.perform(post("/api/auth/login")
                        .header("Origin", FRONTEND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nadie@test.com","password":"loquesea"}
                                """))
                .andExpect(header().string("Access-Control-Expose-Headers",
                        containsString("Retry-After")));
    }

    @Test
    @DisplayName("Se permiten credenciales: sin eso la cookie de sesion no viaja")
    void permiteCredenciales() throws Exception {
        // El refresh token va en una cookie HttpOnly. Si la respuesta no
        // declara allow-credentials, el navegador descarta la cookie y todo
        // refresco falla con 401, con el sintoma de que la sesion se cae
        // exactamente cuando caduca el access token.
        mvc.perform(options("/api/auth/refresh")
                        .header("Origin", FRONTEND)
                        .header("Access-Control-Request-Method", HttpMethod.POST.name()))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND));
    }

    @Test
    @DisplayName("Un origen que no esta configurado se rechaza")
    void rechazaOrigenAjeno() throws Exception {
        // Con allowCredentials no se puede usar el comodin "*", asi que el
        // origen debe casar exactamente. Este test lo fija: relajarlo daria a
        // cualquier sitio la capacidad de llamar a la API con la cookie del
        // usuario adjunta.
        mvc.perform(options("/api/auth/refresh")
                        .header("Origin", "https://sitio-ajeno.example")
                        .header("Access-Control-Request-Method", HttpMethod.POST.name()))
                .andExpect(status().isForbidden());
    }
}
