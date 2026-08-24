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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Salida y expulsion de miembros.
 *
 * <p>La regla que gobierna esta operacion es que nadie sale con saldo
 * pendiente. Al dejar de ser miembro, sus gastos siguen en la base pero
 * desaparecen del informe de balances, que se construye a partir de la lista
 * de miembros. Si su saldo no era cero, los balances de quienes quedan dejan
 * de sumar cero y el dinero se evapora del sistema.
 */
@AutoConfigureMockMvc
class GroupMemberRemovalTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

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

        grupo = crearGrupo(tokenAna);
        anadirMiembro(idBeto);
        anadirMiembro(idCarla);
    }

    @Nested
    @DisplayName("Nadie sale con saldo pendiente")
    class SaldoPendiente {

        @Test
        @DisplayName("un deudor no puede abandonar el grupo")
        void deudorNoSale() throws Exception {
            // Ana adelanta 90 entre tres: Beto queda debiendo 30.
            crearGasto(tokenAna, "Alquiler", "90.00");

            salir(tokenBeto, idBeto)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("deuda pendiente de 30.00")));
        }

        @Test
        @DisplayName("un acreedor tampoco: se quedaria sin cobrar")
        void acreedorNoSale() throws Exception {
            crearGasto(tokenAna, "Alquiler", "90.00");

            salir(tokenAna, idAna)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("cobro de 60.00")));
        }

        @Test
        @DisplayName("un administrador tampoco puede expulsar a alguien con deuda")
        void adminNoExpulsaConDeuda() throws Exception {
            crearGasto(tokenAna, "Alquiler", "90.00");

            salir(tokenAna, idBeto).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("con saldo cero si puede salir")
        void conSaldoCeroSale() throws Exception {
            // Carla no participa en el gasto, asi que su saldo es cero.
            crearGastoEntre(tokenAna, "Cena", "50.00", idAna, idBeto);

            salir(tokenCarla, idCarla).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("los balances de quienes quedan siguen sumando cero")
        void losBalancesSiguenCuadrando() throws Exception {
            crearGastoEntre(tokenAna, "Cena", "50.00", idAna, idBeto);
            salir(tokenCarla, idCarla).andExpect(status().isNoContent());

            // Esta es la invariante que la regla protege. Si se hubiera
            // permitido salir a alguien con saldo distinto de cero, la suma
            // de los balances restantes no daria cero y habria dinero
            // desaparecido del sistema.
            BigDecimal suma = BigDecimal.ZERO;
            for (JsonNode b : leerBalances()) {
                suma = suma.add(new BigDecimal(b.get("netBalance").asText()));
            }
            assertThat(suma).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("Por que existe la regla")
    class Justificacion {

        @Test
        @DisplayName("saltandose la validacion, el dinero desaparece del sistema")
        void sinLaReglaElDineroSeEvapora() throws Exception {
            // Ana adelanta 90 entre tres: le deben 60, Beto y Carla deben 30
            // cada uno. Los balances suman cero, como debe ser.
            crearGasto(tokenAna, "Alquiler", "90.00");
            assertThat(sumaDeBalances()).isEqualByComparingTo("0.00");

            // Se saca a Beto por SQL directo, evitando la comprobacion de
            // saldo que hace el servicio. Sus gastos y sus partes siguen en la
            // base, pero deja de aparecer en el informe de balances, que se
            // construye a partir de la lista de miembros.
            jdbc.update("DELETE FROM group_members WHERE group_id = ? AND user_id = ?", grupo, idBeto);

            // Los 30 que debia Beto se han esfumado: Ana sigue esperando 60
            // que ya nadie debe. Esto es exactamente lo que la regla impide.
            assertThat(sumaDeBalances())
                    .as("suma de balances tras sacar a un deudor sin saldar")
                    .isNotEqualByComparingTo("0.00")
                    .isEqualByComparingTo("30.00");
        }

        private BigDecimal sumaDeBalances() throws Exception {
            BigDecimal suma = BigDecimal.ZERO;
            for (JsonNode b : leerBalances()) {
                suma = suma.add(new BigDecimal(b.get("netBalance").asText()));
            }
            return suma;
        }
    }

    @Nested
    @DisplayName("Permisos")
    class Permisos {

        @Test
        @DisplayName("cualquiera puede irse por su cuenta")
        void salirUnoMismo() throws Exception {
            salir(tokenBeto, idBeto).andExpect(status().isNoContent());

            mvc.perform(get("/api/groups").header("Authorization", bearer(tokenBeto)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("un administrador puede expulsar")
        void adminExpulsa() throws Exception {
            salir(tokenAna, idBeto).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("un miembro corriente no puede expulsar a otro")
        void miembroNoExpulsa() throws Exception {
            salir(tokenBeto, idCarla)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(
                            "Solo un administrador puede expulsar a otros miembros"));
        }

        @Test
        @DisplayName("un extrano al grupo no puede tocar a nadie")
        void extranoNoExpulsa() throws Exception {
            String tokenMallory = registrar("Mallory", "mallory@test.com").get("accessToken").asText();

            salir(tokenMallory, idBeto).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("sacar a quien no pertenece al grupo devuelve 404")
        void objetivoAjeno() throws Exception {
            long idMallory = registrar("Mallory", "mallory@test.com").get("userId").asLong();

            salir(tokenAna, idMallory)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("El usuario no pertenece al grupo"));
        }

        @Test
        @DisplayName("sin autenticar responde 401")
        void sinAutenticar() throws Exception {
            mvc.perform(delete("/api/groups/{g}/members/{u}", grupo, idBeto))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("El grupo nunca se queda sin administrador")
    class SiempreHayAdmin {

        @Test
        @DisplayName("el unico administrador no puede irse dejando gente detras")
        void unicoAdminNoSeVa() throws Exception {
            salir(tokenAna, idAna)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("unico administrador")));
        }

        @Test
        @DisplayName("si promueve a otro antes, ya puede irse")
        void trasPromoverSiSeVa() throws Exception {
            mvc.perform(patch("/api/groups/{g}/members/{u}/role", grupo, idBeto)
                            .header("Authorization", bearer(tokenAna))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"role":"ADMIN"}
                                    """))
                    .andExpect(status().isOk());

            salir(tokenAna, idAna).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("el ultimo miembro si puede salir: no deja a nadie huerfano")
        void ultimoMiembroSale() throws Exception {
            salir(tokenBeto, idBeto).andExpect(status().isNoContent());
            salir(tokenCarla, idCarla).andExpect(status().isNoContent());

            // Ana es la unica que queda: ya no hay nadie a quien dejar sin
            // administrador, asi que la regla deja de aplicarse.
            salir(tokenAna, idAna).andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("Historial")
    class Historial {

        @Test
        @DisplayName("salir del grupo no borra los gastos que registro")
        void conservaElHistorial() throws Exception {
            crearGastoEntre(tokenAna, "Cena", "50.00", idAna, idBeto);
            salir(tokenCarla, idCarla).andExpect(status().isNoContent());

            // Perder la pertenencia no debe reescribir la contabilidad
            // pasada del grupo.
            mvc.perform(get("/api/groups/{g}/expenses", grupo)
                            .header("Authorization", bearer(tokenAna)))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].description").value("Cena"));
        }

        @Test
        @DisplayName("quien sale pierde el acceso al grupo")
        void pierdeElAcceso() throws Exception {
            salir(tokenBeto, idBeto).andExpect(status().isNoContent());

            mvc.perform(get("/api/groups/{g}", grupo).header("Authorization", bearer(tokenBeto)))
                    .andExpect(status().isForbidden());
        }
    }

    // --- utilidades ---

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private ResultActions salir(String token, long userId) throws Exception {
        return mvc.perform(delete("/api/groups/{g}/members/{u}", grupo, userId)
                .header("Authorization", bearer(token)));
    }

    private JsonNode leerBalances() throws Exception {
        return json.readTree(mvc.perform(get("/api/groups/{g}/balances", grupo)
                        .header("Authorization", bearer(tokenAna)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
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

    private void anadirMiembro(long userId) throws Exception {
        mvc.perform(post("/api/groups/{g}/members/{u}", grupo, userId)
                        .header("Authorization", bearer(tokenAna)))
                .andExpect(status().isOk());
    }

    private void crearGasto(String token, String descripcion, String importe) throws Exception {
        crearGastoEntre(token, descripcion, importe);
    }

    private void crearGastoEntre(String token, String descripcion, String importe, long... participantes)
            throws Exception {
        String split = participantes.length == 0 ? ""
                : ",\"splitBetweenUserIds\":[" + java.util.Arrays.stream(participantes)
                .mapToObj(String::valueOf)
                .reduce((a, b) -> a + "," + b).orElse("") + "]";

        mvc.perform(post("/api/groups/{g}/expenses", grupo)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"%s","amount":%s,"expenseDate":"2026-08-20"%s}
                                """.formatted(descripcion, importe, split)))
                .andExpect(status().isOk());
    }
}
