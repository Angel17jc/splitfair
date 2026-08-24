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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Separacion de roles dentro de un grupo y la invariante que la sostiene:
 * un grupo nunca puede quedarse sin administrador.
 */
@AutoConfigureMockMvc
class GroupRoleTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    /** Ana crea el grupo, luego es su administradora. */
    private String tokenAna;
    private long idAna;

    /** Beto entra como miembro corriente. */
    private String tokenBeto;
    private long idBeto;

    private long grupo;

    @BeforeEach
    void prepararGrupo() throws Exception {
        JsonNode ana = registrar("Ana", "ana@test.com");
        tokenAna = ana.get("accessToken").asText();
        idAna = ana.get("userId").asLong();

        JsonNode beto = registrar("Beto", "beto@test.com");
        tokenBeto = beto.get("accessToken").asText();
        idBeto = beto.get("userId").asLong();

        grupo = crearGrupo(tokenAna);
        anadirMiembro(tokenAna, idBeto);
    }

    @Nested
    @DisplayName("Edicion del grupo")
    class Edicion {

        @Test
        @DisplayName("un administrador puede cambiar nombre y descripcion")
        void adminEdita() throws Exception {
            mvc.perform(patch("/api/groups/{id}", grupo)
                            .header("Authorization", bearer(tokenAna))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Piso nuevo","description":"Actualizado"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Piso nuevo"))
                    .andExpect(jsonPath("$.description").value("Actualizado"));
        }

        @Test
        @DisplayName("un miembro corriente no puede: son datos de todo el grupo")
        void miembroNoEdita() throws Exception {
            mvc.perform(patch("/api/groups/{id}", grupo)
                            .header("Authorization", bearer(tokenBeto))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Secuestrado"}
                                    """))
                    .andExpect(status().isForbidden());

            mvc.perform(get("/api/groups/{id}", grupo).header("Authorization", bearer(tokenAna)))
                    .andExpect(jsonPath("$.name").value("Piso"));
        }

        @Test
        @DisplayName("un extrano al grupo tampoco")
        void extranoNoEdita() throws Exception {
            String tokenMallory = registrar("Mallory", "mallory@test.com").get("accessToken").asText();

            mvc.perform(patch("/api/groups/{id}", grupo)
                            .header("Authorization", bearer(tokenMallory))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Secuestrado"}
                                    """))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Cambio de rol")
    class CambioDeRol {

        @Test
        @DisplayName("un administrador promueve a otro miembro")
        void promover() throws Exception {
            cambiarRol(tokenAna, idBeto, "ADMIN").andExpect(status().isOk());

            // Beto ya puede ejercer de administrador.
            mvc.perform(patch("/api/groups/{id}", grupo)
                            .header("Authorization", bearer(tokenBeto))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Editado por Beto"}
                                    """))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("un miembro corriente no puede promoverse a si mismo")
        void miembroNoSeAutopromueve() throws Exception {
            cambiarRol(tokenBeto, idBeto, "ADMIN").andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("degradar a un administrador es posible si queda otro")
        void degradarConOtroAdmin() throws Exception {
            cambiarRol(tokenAna, idBeto, "ADMIN").andExpect(status().isOk());

            // Ahora hay dos: Ana puede retirarse del rol sin dejar el grupo huerfano.
            cambiarRol(tokenAna, idAna, "MEMBER").andExpect(status().isOk());

            mvc.perform(get("/api/groups").header("Authorization", bearer(tokenAna)))
                    .andExpect(jsonPath("$.content[0].role").value("MEMBER"));
        }

        @Test
        @DisplayName("pedir el rol que ya se tiene es idempotente, no un error")
        void idempotente() throws Exception {
            cambiarRol(tokenAna, idBeto, "MEMBER").andExpect(status().isOk());
            cambiarRol(tokenAna, idAna, "ADMIN").andExpect(status().isOk());
        }

        @Test
        @DisplayName("un usuario que no pertenece al grupo devuelve 404")
        void usuarioAjeno() throws Exception {
            long idMallory = registrar("Mallory", "mallory@test.com").get("userId").asLong();

            cambiarRol(tokenAna, idMallory, "ADMIN")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("El usuario no pertenece al grupo"));
        }

        @Test
        @DisplayName("un rol inexistente se rechaza")
        void rolInvalido() throws Exception {
            cambiarRol(tokenAna, idBeto, "SUPERADMIN").andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Invariante: el grupo nunca se queda sin administrador")
    class SiempreHayAdmin {

        @Test
        @DisplayName("el unico administrador no puede degradarse a si mismo")
        void unicoAdminNoSeDegrada() throws Exception {
            cambiarRol(tokenAna, idAna, "MEMBER")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("el grupo se quedaria sin ninguno")));
        }

        @Test
        @DisplayName("tras el intento fallido, sigue siendo administradora")
        void elRolNoCambia() throws Exception {
            cambiarRol(tokenAna, idAna, "MEMBER").andExpect(status().isBadRequest());

            // Si el rechazo hubiera dejado el cambio a medias, el grupo
            // quedaria congelado: nadie podria invitar, expulsar ni editar.
            mvc.perform(patch("/api/groups/{id}", grupo)
                            .header("Authorization", bearer(tokenAna))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Sigo mandando"}
                                    """))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("con dos administradores, el segundo si puede retirarse")
        void elSegundoAdminSePuedeRetirar() throws Exception {
            cambiarRol(tokenAna, idBeto, "ADMIN").andExpect(status().isOk());
            cambiarRol(tokenBeto, idBeto, "MEMBER").andExpect(status().isOk());

            // Y ahora Ana vuelve a ser la unica: no puede degradarse.
            cambiarRol(tokenAna, idAna, "MEMBER").andExpect(status().isBadRequest());
        }
    }

    // --- utilidades ---

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private ResultActions cambiarRol(String token, long userId, String rol) throws Exception {
        return mvc.perform(patch("/api/groups/{id}/members/{userId}/role", grupo, userId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"role":"%s"}
                        """.formatted(rol)));
    }

    private JsonNode registrar(String nombre, String email) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","email":"%s","password":"password123"}
                                """.formatted(nombre, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private long crearGrupo(String token) throws Exception {
        String body = mvc.perform(post("/api/groups")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Piso","description":"test"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void anadirMiembro(String token, long userId) throws Exception {
        mvc.perform(post("/api/groups/{groupId}/members/{userId}", grupo, userId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }
}
