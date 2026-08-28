package com.expensesplit.controller;

import com.expensesplit.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perfil del usuario autenticado y cambio de contrasena.
 */
@AutoConfigureMockMvc
class UserProfileTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String accessToken;
    private String refreshToken;
    private long userId;

    @BeforeEach
    void registrarUsuario() throws Exception {
        MockHttpServletResponse alta = registrar("Ana", "ana@test.com");
        JsonNode credenciales = json.readTree(alta.getContentAsString());
        accessToken = credenciales.get("accessToken").asText();
        refreshToken = refrescoDe(alta);
        userId = credenciales.get("userId").asLong();
    }

    @Nested
    @DisplayName("Consulta del perfil")
    class Consulta {

        @Test
        @DisplayName("devuelve los datos del usuario autenticado")
        void devuelveElPerfil() throws Exception {
            mvc.perform(get("/api/users/me").header("Authorization", bearer(accessToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(userId))
                    .andExpect(jsonPath("$.name").value("Ana"))
                    .andExpect(jsonPath("$.email").value("ana@test.com"))
                    .andExpect(jsonPath("$.createdAt").exists());
        }

        @Test
        @DisplayName("nunca expone el hash de la contrasena")
        void noExponeElHash() throws Exception {
            String cuerpo = mvc.perform(get("/api/users/me").header("Authorization", bearer(accessToken)))
                    .andReturn().getResponse().getContentAsString();

            assertThat(cuerpo)
                    .doesNotContain("passwordHash", "password", "$2a$", "$2b$");
        }

        @Test
        @DisplayName("sin autenticar responde 401")
        void sinAutenticar() throws Exception {
            mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Actualizacion del perfil")
    class Actualizacion {

        @Test
        @DisplayName("cambia el nombre")
        void cambiaElNombre() throws Exception {
            mvc.perform(patch("/api/users/me")
                            .header("Authorization", bearer(accessToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Ana Maria"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Ana Maria"));

            mvc.perform(get("/api/users/me").header("Authorization", bearer(accessToken)))
                    .andExpect(jsonPath("$.name").value("Ana Maria"));
        }

        @Test
        @DisplayName("un nombre vacio se rechaza")
        void nombreVacio() throws Exception {
            mvc.perform(patch("/api/users/me")
                            .header("Authorization", bearer(accessToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"   "}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.name").exists());
        }

        @Test
        @DisplayName("el email no se puede cambiar por esta via")
        void elEmailNoCambia() throws Exception {
            mvc.perform(patch("/api/users/me")
                    .header("Authorization", bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"Ana","email":"otra@test.com"}
                            """));

            // El campo sobrante se ignora: cambiar el email es un flujo con
            // verificacion propia, no un PATCH del perfil.
            mvc.perform(get("/api/users/me").header("Authorization", bearer(accessToken)))
                    .andExpect(jsonPath("$.email").value("ana@test.com"));
        }
    }

    @Nested
    @DisplayName("Cambio de contrasena")
    class CambioDeContrasena {

        @Test
        @DisplayName("con la contrasena actual correcta, se aplica")
        void cambioCorrecto() throws Exception {
            cambiarContrasena("password123", "nueva-password-456")
                    .andExpect(status().isNoContent());

            // La nueva sirve.
            login("ana@test.com", "nueva-password-456").andExpect(status().isOk());
            // La antigua ya no.
            login("ana@test.com", "password123").andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("exige la contrasena actual aunque ya estes autenticado")
        void exigeLaContrasenaActual() throws Exception {
            // Si alguien roba un access token, no debe poder apropiarse de la
            // cuenta cambiando la contrasena sin conocer la anterior.
            cambiarContrasena("no-es-la-actual", "nueva-password-456")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("La contrasena actual no es correcta"));

            login("ana@test.com", "password123").andExpect(status().isOk());
        }

        @Test
        @DisplayName("rechaza reutilizar la contrasena actual")
        void rechazaLaMisma() throws Exception {
            cambiarContrasena("password123", "password123")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "La contrasena nueva debe ser distinta de la actual"));
        }

        @Test
        @DisplayName("rechaza contrasenas demasiado cortas")
        void rechazaCortas() throws Exception {
            cambiarContrasena("password123", "corta")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
        }

        @Test
        @DisplayName("cierra todas las sesiones abiertas")
        void cierraLasSesiones() throws Exception {
            // Se abre una segunda sesion, como si fuera otro dispositivo.
            String otraSesion = refrescoDe(login("ana@test.com", "password123")
                    .andReturn().getResponse());

            cambiarContrasena("password123", "nueva-password-456")
                    .andExpect(status().isNoContent());

            // Quien cambia la contrasena suele hacerlo porque sospecha que
            // alguien mas tiene acceso. Si las sesiones existentes
            // sobrevivieran, el intruso conservaria un refresh token valido
            // durante treinta dias y el cambio no serviria de nada.
            refrescar(refreshToken).andExpect(status().isUnauthorized());
            refrescar(otraSesion).andExpect(status().isUnauthorized());
        }
    }

    // --- utilidades ---

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private MockHttpServletResponse registrar(String nombre, String email) throws Exception {
        return mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","email":"%s","password":"password123"}
                                """.formatted(nombre, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private ResultActions refrescar(String token) throws Exception {
        return mvc.perform(post("/api/auth/refresh").cookie(cookieDeSesion(token)));
    }

    private ResultActions cambiarContrasena(String actual, String nueva) throws Exception {
        return mvc.perform(post("/api/users/me/password")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of(
                        "currentPassword", actual, "newPassword", nueva))));
    }
}
