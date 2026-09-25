package com.expensesplit.controller;

import com.expensesplit.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El reparto devuelve lo que se indico, no solo lo que salio.
 *
 * <p>Antes solo se guardaba el importe resultante de cada parte. El dato que
 * lo produjo —el porcentaje, el numero de partes— se descartaba, y eso hace
 * imposible reeditar un gasto sin degradarlo: con un reparto al 70/30, cambiar
 * el importe total exige los porcentajes para recalcularlo. Sin ellos, o sale
 * mal o hay que convertir el gasto a "cantidades exactas" y perder la
 * intencion, que es justo lo que se quiso evitar al persistir el tipo de
 * reparto.
 *
 * <p>El caso de las <b>partes</b> es el que demuestra que el valor se guarda y
 * no se deduce: 2/1/1 sobre 60,00 da 30/15/15, y de esos importes no hay forma
 * de recuperar los enteros originales. Un test sobre porcentajes redondos
 * pasaria igual con una implementacion que los dedujera dividiendo.
 */
@AutoConfigureMockMvc
class ExpenseSplitValueTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String token;
    private long ana;
    private long beto;
    private long carla;
    private long grupo;

    @BeforeEach
    void grupoDeTres() throws Exception {
        JsonNode primera = registrar("Ana", "ana@test.com");
        token = primera.get("accessToken").asText();
        ana = primera.get("userId").asLong();
        beto = registrar("Beto", "beto@test.com").get("userId").asLong();
        carla = registrar("Carla", "carla@test.com").get("userId").asLong();

        grupo = json.readTree(mvc.perform(post("/api/groups")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Piso\",\"currency\":\"EUR\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        for (long miembro : new long[] { beto, carla }) {
            mvc.perform(post("/api/groups/{g}/members/{u}", grupo, miembro)
                    .header("Authorization", bearer()));
        }
    }

    @Test
    @DisplayName("las partes vuelven tal cual se indicaron, no deducidas del importe")
    void conservaLasPartes() throws Exception {
        JsonNode gasto = crear("SHARES", """
                [{"userId":%d,"value":2},{"userId":%d,"value":1},{"userId":%d,"value":1}]
                """.formatted(ana, beto, carla), "60.00");

        // Los importes son los de siempre: 2/1/1 de 60,00.
        assertThat(importeDe(gasto, ana)).isEqualByComparingTo("30.00");
        assertThat(importeDe(gasto, beto)).isEqualByComparingTo("15.00");

        // Y ademas vuelve el valor original. De 30/15/15 no hay forma de
        // recuperar el "2, 1, 1" que escribio quien creo el gasto.
        assertThat(valorDe(gasto, ana)).isEqualByComparingTo("2");
        assertThat(valorDe(gasto, beto)).isEqualByComparingTo("1");
        assertThat(valorDe(gasto, carla)).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("los porcentajes vuelven como porcentajes")
    void conservaLosPorcentajes() throws Exception {
        JsonNode gasto = crear("PERCENTAGE", """
                [{"userId":%d,"value":50},{"userId":%d,"value":30},{"userId":%d,"value":20}]
                """.formatted(ana, beto, carla), "90.00");

        assertThat(importeDe(gasto, ana)).isEqualByComparingTo("45.00");
        assertThat(valorDe(gasto, ana)).isEqualByComparingTo("50");
        assertThat(valorDe(gasto, beto)).isEqualByComparingTo("30");
        assertThat(valorDe(gasto, carla)).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("a partes iguales no hay valor que devolver")
    void enPartesIgualesNoHayValor() throws Exception {
        JsonNode gasto = json.readTree(mvc.perform(post("/api/groups/{g}/expenses", grupo)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Cena\",\"amount\":30.00,"
                                + "\"expenseDate\":\"2026-09-25\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        // Nulo y no cero: en EQUAL nadie indico nada, y un cero se leeria como
        // "le toca una parte de cero", que es otra cosa.
        for (JsonNode parte : gasto.get("splits")) {
            assertThat(parte.get("value").isNull())
                    .as("la parte de %s no deberia llevar valor", parte.get("userName").asText())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("al editar un gasto se vuelve a guardar el valor nuevo")
    void alEditarSeActualiza() throws Exception {
        JsonNode gasto = crear("PERCENTAGE", """
                [{"userId":%d,"value":50},{"userId":%d,"value":50}]
                """.formatted(ana, beto), "100.00");

        String cuerpo = """
                {"description":"Compra","amount":100.00,"expenseDate":"2026-09-25",
                 "splitType":"PERCENTAGE",
                 "splits":[{"userId":%d,"value":70},{"userId":%d,"value":30}]}
                """.formatted(ana, beto);

        JsonNode editado = json.readTree(mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .put("/api/expenses/{id}", gasto.get("id").asLong())
                                .header("Authorization", bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        // El reparto se rehace por completo, asi que el valor viejo no debe
        // sobrevivir en ninguna parte.
        assertThat(valorDe(editado, ana)).isEqualByComparingTo("70");
        assertThat(valorDe(editado, beto)).isEqualByComparingTo("30");
        assertThat(importeDe(editado, ana)).isEqualByComparingTo("70.00");
    }

    // --- utilidades ---

    private JsonNode crear(String tipo, String splits, String importe) throws Exception {
        String cuerpo = """
                {"description":"Compra","amount":%s,"expenseDate":"2026-09-25",
                 "splitType":"%s","splits":%s}
                """.formatted(importe, tipo, splits);

        return json.readTree(mvc.perform(post("/api/groups/{g}/expenses", grupo)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private java.math.BigDecimal importeDe(JsonNode gasto, long userId) {
        return parteDe(gasto, userId).get("amountOwed").decimalValue();
    }

    private java.math.BigDecimal valorDe(JsonNode gasto, long userId) {
        return parteDe(gasto, userId).get("value").decimalValue();
    }

    private JsonNode parteDe(JsonNode gasto, long userId) {
        for (JsonNode parte : gasto.get("splits")) {
            if (parte.get("userId").asLong() == userId) {
                return parte;
            }
        }
        throw new AssertionError("no hay parte para el usuario " + userId);
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private JsonNode registrar(String nombre, String email) throws Exception {
        String cuerpo = "{\"name\":\"" + nombre + "\",\"email\":\"" + email
                + "\",\"password\":\"password123\"}";

        return json.readTree(mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andReturn().getResponse().getContentAsString());
    }
}
