package com.expensesplit.service;

import com.expensesplit.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba el calculo de balances de extremo a extremo, sobre PostgreSQL
 * real y a traves de la API.
 *
 * <p>Cubre las tres regresiones que motivaron la reescritura de
 * {@link BalanceService}: los miembros sin gastos desaparecian del resultado,
 * recorrer las colecciones perezosas disparaba una consulta por gasto y por
 * split, y hacerlo sin transaccion abierta reventaba al servir peticiones
 * reales.
 */
@AutoConfigureMockMvc
class BalanceCalculationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private String tokenAna;
    private long idAna;
    private long idBeto;
    private long idCarla;
    private long grupo;

    @BeforeEach
    void prepararGrupoDeTres() throws Exception {
        jdbc.execute("TRUNCATE expense_splits, expenses, settlements, " +
                "group_members, groups, users RESTART IDENTITY CASCADE");

        JsonNode ana = registrar("Ana", "ana@test.com");
        tokenAna = ana.get("token").asText();
        idAna = ana.get("userId").asLong();
        idBeto = registrar("Beto", "beto@test.com").get("userId").asLong();
        idCarla = registrar("Carla", "carla@test.com").get("userId").asLong();

        grupo = crearGrupo(tokenAna);
        anadirMiembro(grupo, idBeto);
        anadirMiembro(grupo, idCarla);
    }

    @Test
    @DisplayName("Un miembro que no participo en ningun gasto aparece con balance 0.00")
    void miembroSinGastosApareceEnCero() throws Exception {
        // Ana paga 60 y se reparte solo entre Ana y Beto: Carla queda fuera.
        crearGasto("Cena", "60.00", List.of(idAna, idBeto));

        JsonNode balances = leerBalances();

        assertThat(balances).hasSize(3);
        assertThat(balanceDe(balances, idCarla)).isEqualByComparingTo("0.00");
        assertThat(balanceDe(balances, idAna)).isEqualByComparingTo("30.00");
        assertThat(balanceDe(balances, idBeto)).isEqualByComparingTo("-30.00");
    }

    @Test
    @DisplayName("Los balances de un grupo siempre suman cero")
    void losBalancesSumanCero() throws Exception {
        crearGasto("Alquiler", "900.00", null);
        crearGasto("Compra", "100.00", List.of(idAna, idBeto, idCarla));
        crearGasto("Taxi", "33.33", List.of(idBeto, idCarla));

        BigDecimal suma = BigDecimal.ZERO;
        for (JsonNode b : leerBalances()) {
            suma = suma.add(new BigDecimal(b.get("netBalance").asText()));
        }

        // Esta es la invariante que el reparto con descuadre rompia: si los
        // splits no suman el total del gasto, los balances no suman cero y
        // el grupo nunca puede quedar a paz y salvo.
        assertThat(suma).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Un importe que no divide en partes iguales no descuadra el grupo")
    void importeIndivisibleNoDescuadra() throws Exception {
        // 100.00 entre 3 no es exacto: antes producia 33.33 x3 = 99.99
        crearGasto("Indivisible", "100.00", null);

        JsonNode balances = leerBalances();

        BigDecimal suma = BigDecimal.ZERO;
        for (JsonNode b : balances) {
            suma = suma.add(new BigDecimal(b.get("netBalance").asText()));
        }
        assertThat(suma).isEqualByComparingTo("0.00");

        // Ana pago 100 y le corresponde una de las tres partes (33.34 o 33.33)
        assertThat(balanceDe(balances, idAna)).isBetween(
                new BigDecimal("66.66"), new BigDecimal("66.67"));
    }

    @Test
    @DisplayName("El calculo no crece con el numero de gastos (sin N+1)")
    void sinConsultasNMasUno() throws Exception {
        for (int i = 0; i < 15; i++) {
            crearGasto("Gasto " + i, "30.00", null);
        }

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        leerBalances();

        long consultas = stats.getPrepareStatementCount();

        // Son 5 y no dependen del numero de gastos: dos agregaciones, la lista
        // de miembros y las dos del control de acceso. Con el recorrido
        // perezoso anterior pasaban de 30 para 15 gastos y seguian creciendo.
        // El umbral deja margen para 2 consultas mas: cualquier regresion que
        // introduzca una consulta por gasto o por miembro lo rompe.
        assertThat(consultas)
                .as("consultas ejecutadas al calcular balances de 15 gastos")
                .isLessThanOrEqualTo(7);
    }

    @Test
    @DisplayName("Las liquidaciones sugeridas saldan exactamente a los acreedores")
    void liquidacionesSugeridasCuadran() throws Exception {
        crearGasto("Hotel", "300.00", null);

        JsonNode sugerencias = leerJson(get("/api/groups/{id}/settlements", grupo));

        BigDecimal totalTransferido = BigDecimal.ZERO;
        for (JsonNode s : sugerencias) {
            totalTransferido = totalTransferido.add(new BigDecimal(s.get("amount").asText()));
            assertThat(s.get("toUserId").asLong()).isEqualTo(idAna);
        }

        // Ana adelanto 300 y le corresponden 100: debe recuperar 200.
        assertThat(totalTransferido).isEqualByComparingTo("200.00");
    }

    // --- utilidades ---

    private JsonNode leerBalances() throws Exception {
        return leerJson(get("/api/groups/{id}/balances", grupo));
    }

    private JsonNode leerJson(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req)
            throws Exception {
        String body = mvc.perform(req.header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private BigDecimal balanceDe(JsonNode balances, long userId) {
        for (JsonNode b : balances) {
            if (b.get("userId").asLong() == userId) {
                return new BigDecimal(b.get("netBalance").asText());
            }
        }
        throw new AssertionError("El usuario " + userId + " no aparece en los balances");
    }

    private JsonNode registrar(String nombre, String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","email":"%s","password":"password123"}
                                """.formatted(nombre, email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private long crearGrupo(String token) throws Exception {
        String body = mvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Piso","description":"test"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void anadirMiembro(long groupId, long userId) throws Exception {
        mvc.perform(post("/api/groups/{groupId}/members/{userId}", groupId, userId)
                        .header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isOk());
    }

    private void crearGasto(String descripcion, String importe, List<Long> participantes) throws Exception {
        String split = participantes == null ? ""
                : ",\"splitBetweenUserIds\":[%s]".formatted(
                participantes.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(""));

        mvc.perform(post("/api/groups/{id}/expenses", grupo)
                        .header("Authorization", "Bearer " + tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"%s","amount":%s,"expenseDate":"2026-08-20"%s}
                                """.formatted(descripcion, importe, split)))
                .andExpect(status().isOk());
    }
}
