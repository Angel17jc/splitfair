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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Edicion y borrado de gastos, con el recalculo del reparto que conllevan.
 */
@AutoConfigureMockMvc
class ExpenseEditingTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private String tokenAna;
    private long idAna;
    private String tokenBeto;
    private long idBeto;
    private String tokenCarla;
    private long idCarla;
    private long grupo;

    @BeforeEach
    void prepararGrupo() throws Exception {
        JsonNode ana = registrar("Ana", "ana@test.com");
        tokenAna = ana.get("accessToken").asText();
        idAna = ana.get("userId").asLong();

        JsonNode beto = registrar("Beto", "beto@test.com");
        tokenBeto = beto.get("accessToken").asText();
        idBeto = beto.get("userId").asLong();

        JsonNode carla = registrar("Carla", "carla@test.com");
        tokenCarla = carla.get("accessToken").asText();
        idCarla = carla.get("userId").asLong();

        grupo = crearGrupo();
        anadirMiembro(idBeto);
        anadirMiembro(idCarla);
    }

    @Nested
    @DisplayName("Edicion")
    class Edicion {

        @Test
        @DisplayName("cambiar el importe recalcula las partes")
        void recalculaLasPartes() throws Exception {
            long gasto = crearGasto(tokenAna, "Cena", "90.00", null);

            editar(tokenAna, gasto, "Cena cara", "120.00", null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amount").value(120.00))
                    .andExpect(jsonPath("$.splits.length()").value(3))
                    .andExpect(jsonPath("$.splits[0].amountOwed").value(40.00));
        }

        @Test
        @DisplayName("cambiar los participantes rehace el reparto")
        void cambiaParticipantes() throws Exception {
            long gasto = crearGasto(tokenAna, "Cena", "90.00", null);

            editar(tokenAna, gasto, "Cena", "90.00", java.util.List.of(idAna, idBeto))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.splits.length()").value(2));

            // No deben quedar partes huerfanas de la version anterior.
            Integer partes = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM expense_splits WHERE expense_id = ?", Integer.class, gasto);
            assertThat(partes).isEqualTo(2);
        }

        @Test
        @DisplayName("tras editar, los balances siguen sumando cero")
        void balancesCuadran() throws Exception {
            long gasto = crearGasto(tokenAna, "Cena", "100.00", null);
            editar(tokenAna, gasto, "Cena", "100.00", java.util.List.of(idAna, idBeto));

            // 100 entre dos es exacto, pero la invariante debe cumplirse
            // tambien cuando no lo es: se comprueba de nuevo con 3.
            assertThat(sumaDeBalances()).isEqualByComparingTo("0.00");

            editar(tokenAna, gasto, "Cena", "100.00", null);
            assertThat(sumaDeBalances()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("se pueden cambiar descripcion, categoria y fecha")
        void cambiaMetadatos() throws Exception {
            long gasto = crearGasto(tokenAna, "Cena", "30.00", null);

            mvc.perform(put("/api/expenses/{id}", gasto)
                            .header("Authorization", bearer(tokenAna))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"description":"Comida","amount":30.00,"category":"COMIDA",
                                     "expenseDate":"2026-01-15"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.description").value("Comida"))
                    .andExpect(jsonPath("$.category").value("COMIDA"))
                    .andExpect(jsonPath("$.expenseDate").value("2026-01-15"));
        }

        @Test
        @DisplayName("un participante ajeno al grupo se rechaza")
        void participanteAjeno() throws Exception {
            long gasto = crearGasto(tokenAna, "Cena", "30.00", null);
            long idMallory = registrar("Mallory", "mallory@test.com").get("userId").asLong();

            // Ignorarlo en silencio repartiria el gasto entre menos gente de
            // la indicada, y el cliente creeria que se aplico su peticion.
            editar(tokenAna, gasto, "Cena", "30.00", java.util.List.of(idAna, idMallory))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Algun participante indicado no pertenece al grupo"));
        }

        @Test
        @DisplayName("un importe de cero se rechaza")
        void importeCero() throws Exception {
            long gasto = crearGasto(tokenAna, "Cena", "30.00", null);

            editar(tokenAna, gasto, "Cena", "0.00", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.amount").exists());
        }
    }

    @Nested
    @DisplayName("Borrado")
    class Borrado {

        @Test
        @DisplayName("borrar el gasto lo saca del listado")
        void borra() throws Exception {
            long gasto = crearGasto(tokenAna, "Cena", "30.00", null);

            mvc.perform(delete("/api/expenses/{id}", gasto).header("Authorization", bearer(tokenAna)))
                    .andExpect(status().isNoContent());

            mvc.perform(get("/api/groups/{g}/expenses", grupo).header("Authorization", bearer(tokenAna)))
                    .andExpect(jsonPath("$.content.length()").value(0));
        }

        @Test
        @DisplayName("las partes del gasto se van con el")
        void arrastraLasPartes() throws Exception {
            long gasto = crearGasto(tokenAna, "Cena", "30.00", null);

            mvc.perform(delete("/api/expenses/{id}", gasto).header("Authorization", bearer(tokenAna)))
                    .andExpect(status().isNoContent());

            Integer partes = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM expense_splits WHERE expense_id = ?", Integer.class, gasto);
            assertThat(partes).isZero();
        }

        @Test
        @DisplayName("los balances vuelven a cero al borrar el unico gasto")
        void balancesVuelvenACero() throws Exception {
            long gasto = crearGasto(tokenAna, "Cena", "90.00", null);
            assertThat(balanceDe(idAna)).isEqualByComparingTo("60.00");

            mvc.perform(delete("/api/expenses/{id}", gasto).header("Authorization", bearer(tokenAna)))
                    .andExpect(status().isNoContent());

            // Los balances se derivan de los gastos vigentes, no de un
            // acumulado que hubiera que corregir a mano.
            assertThat(balanceDe(idAna)).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("borrar un gasto inexistente devuelve 404")
        void inexistente() throws Exception {
            mvc.perform(delete("/api/expenses/{id}", 999_999L).header("Authorization", bearer(tokenAna)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Permisos")
    class Permisos {

        @Test
        @DisplayName("quien lo pago puede editarlo")
        void elPagadorEdita() throws Exception {
            long gasto = crearGasto(tokenBeto, "Taxi", "30.00", null);

            editar(tokenBeto, gasto, "Taxi largo", "45.00", null).andExpect(status().isOk());
        }

        @Test
        @DisplayName("un administrador puede editar gastos ajenos")
        void adminEditaAjenos() throws Exception {
            long gasto = crearGasto(tokenBeto, "Taxi", "30.00", null);

            editar(tokenAna, gasto, "Taxi corregido", "25.00", null).andExpect(status().isOk());
        }

        @Test
        @DisplayName("un miembro corriente no puede tocar el gasto de otro")
        void miembroNoEditaAjenos() throws Exception {
            long gasto = crearGasto(tokenBeto, "Taxi", "30.00", null);

            editar(tokenCarla, gasto, "Manipulado", "1.00", null)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(
                            "Solo quien registro el gasto o un administrador pueden modificarlo"));
        }

        @Test
        @DisplayName("un extrano al grupo no puede ni verlo ni editarlo")
        void extranoNoEdita() throws Exception {
            long gasto = crearGasto(tokenAna, "Cena", "30.00", null);
            String tokenMallory = registrar("Mallory", "mallory@test.com").get("accessToken").asText();

            // El identificador del gasto llega suelto en la URL: sin deducir
            // el grupo del propio gasto, bastaria iterar numeros para editar
            // los gastos de cualquiera.
            editar(tokenMallory, gasto, "Robado", "1.00", null).andExpect(status().isForbidden());

            mvc.perform(delete("/api/expenses/{id}", gasto).header("Authorization", bearer(tokenMallory)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("sin autenticar responde 401")
        void sinAutenticar() throws Exception {
            long gasto = crearGasto(tokenAna, "Cena", "30.00", null);

            mvc.perform(delete("/api/expenses/{id}", gasto)).andExpect(status().isUnauthorized());
        }
    }

    // --- utilidades ---

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private ResultActions editar(String token, long gastoId, String descripcion,
                                  String importe, java.util.List<Long> participantes) throws Exception {
        String split = participantes == null ? ""
                : ",\"splitBetweenUserIds\":[" + participantes.stream()
                .map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("") + "]";

        return mvc.perform(put("/api/expenses/{id}", gastoId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"description":"%s","amount":%s,"expenseDate":"2026-08-20"%s}
                        """.formatted(descripcion, importe, split)));
    }

    private BigDecimal sumaDeBalances() throws Exception {
        BigDecimal suma = BigDecimal.ZERO;
        for (JsonNode b : leerBalances()) {
            suma = suma.add(new BigDecimal(b.get("netBalance").asText()));
        }
        return suma;
    }

    private BigDecimal balanceDe(long userId) throws Exception {
        for (JsonNode b : leerBalances()) {
            if (b.get("userId").asLong() == userId) {
                return new BigDecimal(b.get("netBalance").asText());
            }
        }
        throw new AssertionError("Usuario " + userId + " ausente de los balances");
    }

    private JsonNode leerBalances() throws Exception {
        return json.readTree(mvc.perform(get("/api/groups/{g}/balances", grupo)
                        .header("Authorization", bearer(tokenAna)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .get("balances");
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

    private long crearGrupo() throws Exception {
        String body = mvc.perform(post("/api/groups")
                        .header("Authorization", bearer(tokenAna))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Piso","description":"test","currency":"EUR"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void anadirMiembro(long userId) throws Exception {
        mvc.perform(post("/api/groups/{g}/members/{u}", grupo, userId)
                        .header("Authorization", bearer(tokenAna)))
                .andExpect(status().isOk());
    }

    private long crearGasto(String token, String descripcion, String importe,
                             java.util.List<Long> participantes) throws Exception {
        String split = participantes == null ? ""
                : ",\"splitBetweenUserIds\":[" + participantes.stream()
                .map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("") + "]";

        String body = mvc.perform(post("/api/groups/{g}/expenses", grupo)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"%s","amount":%s,"expenseDate":"2026-08-20"%s}
                                """.formatted(descripcion, importe, split)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return json.readTree(body).get("id").asLong();
    }
}
