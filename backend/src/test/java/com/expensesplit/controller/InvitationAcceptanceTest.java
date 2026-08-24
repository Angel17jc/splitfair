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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aceptacion de invitaciones, con cuenta previa y sin ella.
 */
@AutoConfigureMockMvc
class InvitationAcceptanceTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private String tokenAna;
    private long grupo;

    @BeforeEach
    void prepararGrupo() throws Exception {
        tokenAna = registrar("Ana", "ana@test.com").get("accessToken").asText();
        grupo = crearGrupo(tokenAna);
    }

    @Nested
    @DisplayName("Usuario con cuenta previa")
    class ConCuenta {

        @Test
        @DisplayName("acepta el link y queda dentro del grupo como MEMBER")
        void aceptaYEntra() throws Exception {
            String tokenBeto = registrar("Beto", "beto@test.com").get("accessToken").asText();
            String invitacion = crearInvitacion(null);

            aceptar(tokenBeto, invitacion)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(grupo))
                    .andExpect(jsonPath("$.name").value("Piso"));

            // Y el grupo ya aparece en su listado, con el rol correcto.
            mvc.perform(get("/api/groups").header("Authorization", bearer(tokenBeto)))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].role").value("MEMBER"));
        }

        @Test
        @DisplayName("el link es de un solo uso: el segundo intento falla")
        void unSoloUso() throws Exception {
            String tokenBeto = registrar("Beto", "beto@test.com").get("accessToken").asText();
            String tokenCarla = registrar("Carla", "carla@test.com").get("accessToken").asText();
            String invitacion = crearInvitacion(null);

            aceptar(tokenBeto, invitacion).andExpect(status().isOk());

            // Un link reenviado por error a otro chat no debe dejar entrar a
            // nadie mas.
            aceptar(tokenCarla, invitacion)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Esta invitacion ya se ha utilizado"));
        }

        @Test
        @DisplayName("queda registrado quien la consumio y cuando")
        void dejaRastro() throws Exception {
            JsonNode beto = registrar("Beto", "beto@test.com");
            aceptar(beto.get("accessToken").asText(), crearInvitacion(null))
                    .andExpect(status().isOk());

            Long aceptadaPor = jdbc.queryForObject(
                    "SELECT accepted_by FROM invitations LIMIT 1", Long.class);
            assertThat(aceptadaPor).isEqualTo(beto.get("userId").asLong());
            assertThat(jdbc.queryForObject(
                    "SELECT accepted_at IS NOT NULL FROM invitations LIMIT 1", Boolean.class)).isTrue();
        }

        @Test
        @DisplayName("una invitacion caducada se rechaza")
        void caducada() throws Exception {
            String tokenBeto = registrar("Beto", "beto@test.com").get("accessToken").asText();
            String invitacion = crearInvitacion(null);

            jdbc.update("UPDATE invitations SET created_at = now() - interval '10 days', "
                    + "expires_at = now() - interval '1 day'");

            aceptar(tokenBeto, invitacion)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Esta invitacion ha caducado"));
        }

        @Test
        @DisplayName("quien ya pertenece al grupo no puede volver a entrar")
        void yaEsMiembro() throws Exception {
            aceptar(tokenAna, crearInvitacion(null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Ya perteneces a este grupo"));
        }

        @Test
        @DisplayName("un token inventado devuelve 404")
        void tokenInventado() throws Exception {
            String tokenBeto = registrar("Beto", "beto@test.com").get("accessToken").asText();

            aceptar(tokenBeto, "no-existe").andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("aceptar exige autenticacion")
        void sinAutenticar() throws Exception {
            mvc.perform(post("/api/invitations/{t}/accept", crearInvitacion(null)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Invitacion dirigida a un email concreto")
    class Dirigida {

        @Test
        @DisplayName("solo la acepta la direccion indicada")
        void soloElDestinatario() throws Exception {
            String invitacion = crearInvitacion("beto@test.com");
            String tokenCarla = registrar("Carla", "carla@test.com").get("accessToken").asText();

            // Reenviar el link a un tercero no sirve de nada.
            aceptar(tokenCarla, invitacion)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Esta invitacion no esta dirigida a tu cuenta"));
        }

        @Test
        @DisplayName("el rechazo no revela a que direccion iba dirigida")
        void noFiltraElDestinatario() throws Exception {
            String invitacion = crearInvitacion("secreto@test.com");
            String tokenCarla = registrar("Carla", "carla@test.com").get("accessToken").asText();

            String cuerpo = aceptar(tokenCarla, invitacion)
                    .andReturn().getResponse().getContentAsString();

            // Seria filtrar el email de un tercero a quien solo tiene el link.
            assertThat(cuerpo).doesNotContain("secreto@test.com");
        }

        @Test
        @DisplayName("el destinatario correcto si entra")
        void elDestinatarioEntra() throws Exception {
            String invitacion = crearInvitacion("beto@test.com");
            String tokenBeto = registrar("Beto", "beto@test.com").get("accessToken").asText();

            aceptar(tokenBeto, invitacion).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Registro con invitacion, en un solo paso")
    class RegistroConInvitacion {

        @Test
        @DisplayName("crea la cuenta y entra al grupo con una sola peticion")
        void registroYEntrada() throws Exception {
            String invitacion = crearInvitacion(null);

            String cuerpo = registrarCon("Nuevo", "nuevo@test.com", invitacion)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.joinedGroupId").value(grupo))
                    .andReturn().getResponse().getContentAsString();

            String token = json.readTree(cuerpo).get("accessToken").asText();
            mvc.perform(get("/api/groups/{id}", grupo).header("Authorization", bearer(token)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("un registro sin invitacion no lleva joinedGroupId")
        void registroNormal() throws Exception {
            String cuerpo = registrarCon("Solo", "solo@test.com", null)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            assertThat(cuerpo).doesNotContain("joinedGroupId");
        }

        @Test
        @DisplayName("si la invitacion es invalida, no se crea la cuenta")
        void atomicidad() throws Exception {
            registrarCon("Fantasma", "fantasma@test.com", "token-inventado")
                    .andExpect(status().isNotFound());

            // Con dos llamadas separadas, este usuario habria quedado
            // registrado pero fuera del grupo al que le invitaron, sin
            // entender por que. La transaccion del registro lo revierte todo.
            Integer existe = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE email = 'fantasma@test.com'", Integer.class);
            assertThat(existe).isZero();
        }

        @Test
        @DisplayName("si la invitacion va dirigida a otro email, tampoco se crea")
        void atomicidadConDestinatarioDistinto() throws Exception {
            String invitacion = crearInvitacion("esperado@test.com");

            registrarCon("Otro", "otro@test.com", invitacion)
                    .andExpect(status().isBadRequest());

            Integer existe = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE email = 'otro@test.com'", Integer.class);
            assertThat(existe).isZero();

            // Y la invitacion sigue disponible para su destinatario legitimo.
            String tokenEsperado = registrarCon("Esperado", "esperado@test.com", invitacion)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            assertThat(json.readTree(tokenEsperado).get("joinedGroupId").asLong()).isEqualTo(grupo);
        }
    }

    // --- utilidades ---

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private ResultActions aceptar(String accessToken, String invitationToken) throws Exception {
        return mvc.perform(post("/api/invitations/{t}/accept", invitationToken)
                .header("Authorization", bearer(accessToken)));
    }

    private String crearInvitacion(String email) throws Exception {
        String cuerpo = email == null ? "{}"
                : json.writeValueAsString(java.util.Map.of("email", email));

        String body = mvc.perform(post("/api/groups/{g}/invitations", grupo)
                        .header("Authorization", bearer(tokenAna))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return json.readTree(body).get("token").asText();
    }

    private ResultActions registrarCon(String nombre, String email, String invitationToken) throws Exception {
        var datos = new java.util.LinkedHashMap<String, String>();
        datos.put("name", nombre);
        datos.put("email", email);
        datos.put("password", "password123");
        if (invitationToken != null) {
            datos.put("invitationToken", invitationToken);
        }

        return mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(datos)));
    }

    private JsonNode registrar(String nombre, String email) throws Exception {
        return json.readTree(registrarCon(nombre, email, null)
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
}
