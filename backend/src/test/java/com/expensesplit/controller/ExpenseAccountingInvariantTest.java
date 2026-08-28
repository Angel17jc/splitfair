package com.expensesplit.controller;

import com.expensesplit.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Invariantes contables del modulo de gastos, de extremo a extremo.
 *
 * <p>Las clases anteriores comprueban cada pieza por separado. Esta comprueba
 * lo que tiene que seguir siendo cierto cuando se combinan, que es donde
 * aparecen los fallos que ninguna prueba unitaria ve:
 *
 * <ol>
 *   <li>las partes de un gasto suman exactamente su importe;</li>
 *   <li>los balances del grupo suman exactamente cero;</li>
 *   <li>aplicar las liquidaciones sugeridas deja a todo el mundo a cero.</li>
 * </ol>
 *
 * <p>Si la primera se rompe, el dinero aparece o desaparece. Si se rompe la
 * segunda, el algoritmo de simplificacion nunca converge. La tercera es la
 * promesa que le hace la aplicacion al usuario.
 */
@AutoConfigureMockMvc
class ExpenseAccountingInvariantTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String tokenAna;
    private long idAna;
    private String tokenBeto;
    private long idBeto;
    private long idCarla;
    private long idDiego;
    private long grupo;

    @BeforeEach
    void prepararGrupoDeCuatro() throws Exception {
        JsonNode ana = registrar("Ana", "ana@test.com");
        tokenAna = ana.get("accessToken").asText();
        idAna = ana.get("userId").asLong();

        JsonNode beto = registrar("Beto", "beto@test.com");
        tokenBeto = beto.get("accessToken").asText();
        idBeto = beto.get("userId").asLong();

        idCarla = registrar("Carla", "carla@test.com").get("userId").asLong();
        idDiego = registrar("Diego", "diego@test.com").get("userId").asLong();

        grupo = crearGrupo();
        anadirMiembro(idBeto);
        anadirMiembro(idCarla);
        anadirMiembro(idDiego);
    }

    @Nested
    @DisplayName("Cada modo de reparto conserva las invariantes")
    class PorModoDeReparto {

        @ParameterizedTest(name = "{0} de {1}")
        @CsvSource({
                // Importes elegidos para que la division no sea exacta.
                "EQUAL,      100.00",
                "EQUAL,      0.07",
                "EQUAL,      99.99",
                "EXACT,      100.00",
                "EXACT,      0.07",
                "PERCENTAGE, 100.00",
                "PERCENTAGE, 0.07",
                "PERCENTAGE, 1234.56",
                "SHARES,     100.00",
                "SHARES,     0.07",
                "SHARES,     1234.56",
        })
        @DisplayName("las partes suman el importe y los balances suman cero")
        void invariantes(String tipo, String importe) throws Exception {
            crearGasto(tokenAna, importe, tipo, splitsPara(tipo, importe))
                    .andExpect(status().isOk());

            assertThat(sumaDePartes())
                    .as("suma de las partes de un gasto %s de %s", tipo, importe)
                    .isEqualByComparingTo(importe);

            assertThat(sumaDeBalances())
                    .as("suma de balances con un gasto %s de %s", tipo, importe)
                    .isEqualByComparingTo("0.00");
        }

        /** Reparto entre tres de los cuatro miembros, deliberadamente desigual. */
        private String splitsPara(String tipo, String importe) {
            return switch (tipo) {
                case "EQUAL" -> """
                        [{"userId":%d},{"userId":%d},{"userId":%d}]
                        """.formatted(idAna, idBeto, idCarla);
                case "EXACT" -> exactosQueSuman(importe);
                case "PERCENTAGE" -> """
                        [{"userId":%d,"value":33.33},{"userId":%d,"value":33.33},{"userId":%d,"value":33.34}]
                        """.formatted(idAna, idBeto, idCarla);
                case "SHARES" -> """
                        [{"userId":%d,"value":3},{"userId":%d,"value":2},{"userId":%d,"value":1}]
                        """.formatted(idAna, idBeto, idCarla);
                default -> throw new IllegalArgumentException(tipo);
            };
        }

        /** Reparte el importe en tres trozos exactos que cuadran. */
        private String exactosQueSuman(String importe) {
            BigDecimal total = new BigDecimal(importe);
            BigDecimal uno = total.divide(new BigDecimal("3"), 2, java.math.RoundingMode.DOWN);
            BigDecimal resto = total.subtract(uno.multiply(new BigDecimal("2")));

            return """
                    [{"userId":%d,"value":%s},{"userId":%d,"value":%s},{"userId":%d,"value":%s}]
                    """.formatted(idAna, uno, idBeto, uno, idCarla, resto);
        }
    }

    @Nested
    @DisplayName("Escenario completo de un viaje")
    class Viaje {

        @Test
        @DisplayName("con cinco gastos y cuatro modos distintos, las cuentas cuadran")
        void viajeCompleto() throws Exception {
            // Alojamiento: Ana adelanta, pero Diego duerme en habitacion
            // doble y paga el doble que los demas.
            crearGasto(tokenAna, "600.00", "SHARES", """
                    [{"userId":%d,"value":1},{"userId":%d,"value":1},{"userId":%d,"value":1},{"userId":%d,"value":2}]
                    """.formatted(idAna, idBeto, idCarla, idDiego));

            // Cena entre todos, a partes iguales, con importe indivisible.
            crearGasto(tokenBeto, "100.00", null, null);

            // Entradas a un museo: Carla no entro.
            crearGasto(tokenAna, "45.00", "EQUAL", """
                    [{"userId":%d},{"userId":%d},{"userId":%d}]
                    """.formatted(idAna, idBeto, idDiego));

            // Gasolina repartida por uso real.
            crearGasto(tokenBeto, "83.33", "PERCENTAGE", """
                    [{"userId":%d,"value":40},{"userId":%d,"value":35},{"userId":%d,"value":25}]
                    """.formatted(idAna, idBeto, idCarla));

            // Un capricho que paga solo quien lo pidio.
            crearGasto(tokenAna, "12.50", "EXACT", """
                    [{"userId":%d,"value":12.50}]
                    """.formatted(idCarla));

            assertThat(sumaDeBalances())
                    .as("los balances del grupo tras cinco gastos")
                    .isEqualByComparingTo("0.00");

            // Y las liquidaciones sugeridas dejan a todos a cero.
            assertThat(saldosTrasAplicarLiquidaciones())
                    .as("saldos tras aplicar las liquidaciones sugeridas")
                    .allSatisfy((usuario, saldo) ->
                            assertThat(saldo).isEqualByComparingTo("0.00"));
        }

        @Test
        @DisplayName("nunca se sugieren mas transacciones que miembros menos uno")
        void transaccionesAcotadas() throws Exception {
            crearGasto(tokenAna, "600.00", null, null);
            crearGasto(tokenBeto, "100.00", null, null);
            crearGasto(tokenAna, "45.00", null, null);

            assertThat(liquidaciones().size())
                    .as("transacciones sugeridas para un grupo de 4")
                    .isLessThanOrEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Las invariantes sobreviven al ciclo de vida completo")
    class CicloDeVida {

        @Test
        @DisplayName("crear, editar cambiando de modo, y borrar")
        void altaEdicionBorrado() throws Exception {
            // Alta con reparto igual entre cuatro: 100 no divide exacto.
            long gasto = idDe(crearGasto(tokenAna, "100.00", null, null));
            assertThat(sumaDeBalances()).isEqualByComparingTo("0.00");

            // Edicion cambiando a porcentajes con decimales.
            mvc.perform(put("/api/expenses/{id}", gasto)
                            .header("Authorization", bearer(tokenAna))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"description":"Editado","amount":100.00,"expenseDate":"2026-08-20",
                                     "splitType":"PERCENTAGE",
                                     "splits":[{"userId":%d,"value":33.33},{"userId":%d,"value":33.33},
                                               {"userId":%d,"value":33.34}]}
                                    """.formatted(idAna, idBeto, idCarla)))
                    .andExpect(status().isOk());

            assertThat(sumaDePartes()).isEqualByComparingTo("100.00");
            assertThat(sumaDeBalances()).isEqualByComparingTo("0.00");

            // Segunda edicion, ahora a importes exactos.
            mvc.perform(put("/api/expenses/{id}", gasto)
                            .header("Authorization", bearer(tokenAna))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"description":"Editado","amount":100.00,"expenseDate":"2026-08-20",
                                     "splitType":"EXACT",
                                     "splits":[{"userId":%d,"value":70.00},{"userId":%d,"value":30.00}]}
                                    """.formatted(idAna, idBeto)))
                    .andExpect(status().isOk());

            assertThat(sumaDeBalances()).isEqualByComparingTo("0.00");

            // Borrado: todo vuelve a cero.
            mvc.perform(delete("/api/expenses/{id}", gasto).header("Authorization", bearer(tokenAna)))
                    .andExpect(status().isNoContent());

            assertThat(balances().size()).as("todos los miembros siguen apareciendo").isEqualTo(4);
            assertThat(sumaDeBalances()).isEqualByComparingTo("0.00");
            assertThat(liquidaciones()).as("sin gastos no hay nada que liquidar").isEmpty();
        }
    }

    // --- utilidades ---

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /**
     * Aplica las liquidaciones sugeridas sobre los balances actuales y
     * devuelve el saldo resultante de cada usuario.
     */
    private Map<Long, BigDecimal> saldosTrasAplicarLiquidaciones() throws Exception {
        Map<Long, BigDecimal> saldos = new HashMap<>();
        for (JsonNode b : balances()) {
            saldos.put(b.get("userId").asLong(), new BigDecimal(b.get("netBalance").asText()));
        }

        for (JsonNode t : liquidaciones()) {
            BigDecimal importe = new BigDecimal(t.get("amount").asText());
            // Quien paga sube su balance; quien cobra lo baja.
            saldos.merge(t.get("fromUserId").asLong(), importe, BigDecimal::add);
            saldos.merge(t.get("toUserId").asLong(), importe.negate(), BigDecimal::add);
        }
        return saldos;
    }

    private JsonNode balances() throws Exception {
        return json.readTree(mvc.perform(get("/api/groups/{g}/balances", grupo)
                        .header("Authorization", bearer(tokenAna)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .get("balances");
    }

    private JsonNode liquidaciones() throws Exception {
        return json.readTree(mvc.perform(get("/api/groups/{g}/settlements", grupo)
                        .header("Authorization", bearer(tokenAna)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private BigDecimal sumaDeBalances() throws Exception {
        BigDecimal suma = BigDecimal.ZERO;
        for (JsonNode b : balances()) {
            suma = suma.add(new BigDecimal(b.get("netBalance").asText()));
        }
        return suma;
    }

    private BigDecimal sumaDePartes() throws Exception {
        JsonNode gastos = json.readTree(mvc.perform(get("/api/groups/{g}/expenses", grupo)
                        .header("Authorization", bearer(tokenAna)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .get("content");

        BigDecimal suma = BigDecimal.ZERO;
        for (JsonNode split : gastos.get(0).get("splits")) {
            suma = suma.add(new BigDecimal(split.get("amountOwed").asText()));
        }
        return suma;
    }

    private ResultActions crearGasto(String token, String importe, String tipo, String splitsJson)
            throws Exception {
        StringBuilder cuerpo = new StringBuilder("{\"description\":\"Gasto\",\"amount\":")
                .append(importe).append(",\"expenseDate\":\"2026-08-20\"");

        if (tipo != null) {
            cuerpo.append(",\"splitType\":\"").append(tipo).append('"');
        }
        if (splitsJson != null) {
            cuerpo.append(",\"splits\":").append(splitsJson.trim());
        }
        cuerpo.append('}');

        return mvc.perform(post("/api/groups/{g}/expenses", grupo)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo.toString()));
    }

    private long idDe(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString())
                .get("id").asLong();
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
                                {"name":"Viaje","description":"test","currency":"EUR"}
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
}
