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
 * Generacion de links de invitacion y su vista previa publica.
 */
@AutoConfigureMockMvc
class InvitationCreationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private String tokenAna;
    private String tokenBeto;
    private long idBeto;
    private long grupo;

    @BeforeEach
    void prepararGrupo() throws Exception {
        JsonNode ana = registrar("Ana", "ana@test.com");
        tokenAna = ana.get("accessToken").asText();

        JsonNode beto = registrar("Beto", "beto@test.com");
        tokenBeto = beto.get("accessToken").asText();
        idBeto = beto.get("userId").asLong();

        grupo = crearGrupo(tokenAna);
    }

    @Nested
    @DisplayName("Creacion")
    class Creacion {

        @Test
        @DisplayName("una administradora obtiene un link con token y caducidad")
        void creaLink() throws Exception {
            String cuerpo = invitar(tokenAna, null)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.expiresAt").exists())
                    .andReturn().getResponse().getContentAsString();

            JsonNode invitacion = json.readTree(cuerpo);
            assertThat(invitacion.get("url").asText())
                    .startsWith("http://localhost:5173/invitaciones/")
                    .contains(invitacion.get("token").asText());
        }

        @Test
        @DisplayName("el token nunca se guarda en claro en la base")
        void seGuardaSoloElHash() throws Exception {
            String enClaro = json.readTree(invitar(tokenAna, null)
                    .andReturn().getResponse().getContentAsString())
                    .get("token").asText();

            // El link es la credencial: quien lo tenga entra al grupo, asi que
            // un volcado de esta tabla no debe bastar para colarse.
            Integer coincidencias = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM invitations WHERE token_hash = ?", Integer.class, enClaro);
            assertThat(coincidencias).isZero();

            assertThat(jdbc.queryForObject(
                    "SELECT token_hash FROM invitations LIMIT 1", String.class))
                    .hasSize(64)
                    .matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("dos invitaciones nunca comparten token")
        void tokensDistintos() throws Exception {
            String uno = tokenDe(invitar(tokenAna, null));
            String dos = tokenDe(invitar(tokenAna, null));

            assertThat(uno).isNotEqualTo(dos);
        }

        @Test
        @DisplayName("se puede dirigir a un email concreto")
        void invitacionDirigida() throws Exception {
            invitar(tokenAna, "nuevo@test.com")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value("nuevo@test.com"));
        }

        @Test
        @DisplayName("el email dirigido se normaliza a minusculas")
        void emailNormalizado() throws Exception {
            // Si no, una invitacion a "Nuevo@Test.com" nunca casaria con la
            // cuenta registrada como "nuevo@test.com".
            invitar(tokenAna, "Nuevo@Test.com")
                    .andExpect(jsonPath("$.email").value("nuevo@test.com"));
        }

        @Test
        @DisplayName("invitar a quien ya pertenece al grupo se rechaza")
        void yaEsMiembro() throws Exception {
            mvc.perform(post("/api/groups/{g}/members/{u}", grupo, idBeto)
                    .header("Authorization", bearer(tokenAna)));

            invitar(tokenAna, "beto@test.com")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Esa persona ya pertenece al grupo"));
        }

        @Test
        @DisplayName("un email con formato invalido se rechaza")
        void emailInvalido() throws Exception {
            invitar(tokenAna, "no-es-un-email")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").exists());
        }
    }

    @Nested
    @DisplayName("Permisos")
    class Permisos {

        @Test
        @DisplayName("un miembro corriente no puede invitar")
        void miembroNoInvita() throws Exception {
            mvc.perform(post("/api/groups/{g}/members/{u}", grupo, idBeto)
                    .header("Authorization", bearer(tokenAna)));

            // Incorporar gente altera la composicion del grupo y, con ella,
            // el reparto de todos los gastos futuros.
            invitar(tokenBeto, null).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("un extrano al grupo tampoco")
        void extranoNoInvita() throws Exception {
            invitar(tokenBeto, null).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("sin autenticar responde 401")
        void sinAutenticar() throws Exception {
            mvc.perform(post("/api/groups/{g}/invitations", grupo)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Vista previa publica")
    class VistaPrevia {

        @Test
        @DisplayName("funciona sin autenticacion: quien abre el link puede no tener cuenta")
        void esPublica() throws Exception {
            String token = tokenDe(invitar(tokenAna, null));

            mvc.perform(get("/api/invitations/{token}", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.groupName").value("Piso"))
                    .andExpect(jsonPath("$.invitedByName").value("Ana"))
                    .andExpect(jsonPath("$.valid").value(true));
        }

        @Test
        @DisplayName("no filtra datos economicos ni la lista de miembros")
        void exponeLoMinimo() throws Exception {
            crearGasto(tokenAna, grupo, "Alquiler", "900.00");
            String token = tokenDe(invitar(tokenAna, null));

            String cuerpo = mvc.perform(get("/api/invitations/{token}", token))
                    .andReturn().getResponse().getContentAsString();

            // Este endpoint se sirve sin autenticacion: cualquier dato de mas
            // queda expuesto a quien tenga el link.
            assertThat(cuerpo)
                    .doesNotContain("Alquiler", "900", "members", "balance", "email");
        }

        @Test
        @DisplayName("un token inexistente devuelve 404")
        void tokenInexistente() throws Exception {
            mvc.perform(get("/api/invitations/{token}", "token-que-nunca-existio"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("una invitacion caducada responde 200 con valid=false")
        void invitacionCaducada() throws Exception {
            String token = tokenDe(invitar(tokenAna, null));
            // Se retrasan ambas fechas: la restriccion ck_invitations_expiry
            // exige expires_at > created_at, asi que no se puede falsear solo
            // la caducidad. Se simula una invitacion creada hace diez dias.
            jdbc.update("UPDATE invitations SET created_at = now() - interval '10 days', "
                    + "expires_at = now() - interval '1 day'");

            // Se distingue de un 404 a proposito: el cliente necesita separar
            // "este link nunca existio" de "llegas tarde" para explicarselo
            // al usuario.
            mvc.perform(get("/api/invitations/{token}", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false))
                    .andExpect(jsonPath("$.groupName").value("Piso"));
        }
    }

    // --- utilidades ---

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private ResultActions invitar(String token, String email) throws Exception {
        String cuerpo = email == null ? "{}"
                : json.writeValueAsString(java.util.Map.of("email", email));

        return mvc.perform(post("/api/groups/{g}/invitations", grupo)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private String tokenDe(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString())
                .get("token").asText();
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

    private void crearGasto(String token, long groupId, String descripcion, String importe) throws Exception {
        mvc.perform(post("/api/groups/{id}/expenses", groupId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"%s","amount":%s,"expenseDate":"2026-08-20"}
                                """.formatted(descripcion, importe)))
                .andExpect(status().isOk());
    }
}
