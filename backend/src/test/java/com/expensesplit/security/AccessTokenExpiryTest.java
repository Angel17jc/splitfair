package com.expensesplit.security;

import com.expensesplit.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Caducidad del access token.
 *
 * <p>Vive en su propia clase porque necesita un tiempo de vida de un segundo,
 * y ese ajuste se aplica a todo el contexto de Spring. Tenerlo en la clase
 * general de codigos de estado volvia sensibles al reloj a tests que no
 * tienen nada que ver con la caducidad: bastaba que la suite se ralentizara
 * para que un token expirase a mitad de un escenario y el test fallara con un
 * 401 inesperado.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "jwt.access-token-expiration-ms=1000")
class AccessTokenExpiryTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    @DisplayName("Un token caducado se distingue de uno invalido")
    void tokenCaducado() throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana","email":"ana@test.com","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String accessToken = json.readTree(body).get("accessToken").asText();

        Thread.sleep(1100);

        mvc.perform(get("/api/groups").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("El token de acceso ha caducado"))
                // El cliente necesita saber que basta con refrescar: es una
                // situacion normal, no un intento de manipulacion.
                .andExpect(header().string("WWW-Authenticate",
                        "Bearer error=\"invalid_token\", error_description=\"The access token expired\""));
    }
}
