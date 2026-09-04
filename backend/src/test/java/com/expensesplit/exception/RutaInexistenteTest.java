package com.expensesplit.exception;

import com.expensesplit.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Una ruta que no existe es un 404, no un error del servidor.
 *
 * <p>Salio al escribir los tests de las sondas de estado: pedir un endpoint de
 * Actuator no publicado devolvia <b>500</b>. La causa no tenia que ver con
 * Actuator. Spring senala una ruta desconocida con
 * {@link org.springframework.web.servlet.resource.NoResourceFoundException},
 * y esa excepcion caia en el manejador generico de
 * {@code GlobalExceptionHandler}, que existe como red de seguridad para
 * fallos imprevistos y convierte en 500 todo lo que le llega.
 *
 * <p>El alcance es acotado, y conviene no exagerarlo: para quien no ha
 * iniciado sesion, la cadena de seguridad responde <b>401 antes</b> de llegar
 * al enrutado, asi que un rastreador automatico nunca provoca esto. Lo sufre
 * el usuario autenticado, que son los dos casos que importan:
 *
 * <ol>
 *   <li>El cliente recibe "ha ocurrido un error interno" ante una URL mal
 *       escrita o un endpoint retirado, en vez de un 404 que le dice que ahi
 *       no hay nada. Un frontend razonable trata el 500 como servidor caido.</li>
 *   <li>Cada una de esas peticiones escribe un <b>stack trace completo a
 *       nivel ERROR</b>. Son errores que no lo son, y enturbian el log justo
 *       en el nivel que se vigila.</li>
 * </ol>
 */
@AutoConfigureMockMvc
class RutaInexistenteTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String token;

    @BeforeEach
    void registrarUsuario() throws Exception {
        String cuerpo = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana","email":"ana@test.com","password":"password123"}
                                """))
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(cuerpo).get("accessToken").asText();
    }

    @Test
    @DisplayName("una ruta de la API que no existe responde 404")
    void rutaDeApiInexistente() throws Exception {
        mvc.perform(get("/api/no-existe-esta-ruta").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("la respuesta no lleva traceId: no hay nada que investigar")
    void sinIdentificadorDeIncidencia() throws Exception {
        // El traceId existe para que un usuario pueda reportar un fallo
        // interno y alguien lo localice en el log. Una URL que no existe no es
        // un fallo interno: darle identificador invita a abrir una incidencia
        // por algo que se corrige escribiendo bien la direccion.
        mvc.perform(get("/api/tampoco-existe").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    @DisplayName("sin sesion la respuesta sigue siendo 401, no 404")
    void anonimoNoDescubreRutas() throws Exception {
        // Deliberado, y por eso se fija: responder 404 a un anonimo en las
        // rutas que no existen y 401 en las que si, convierte los codigos de
        // estado en un mapa de la API para quien quiera enumerarla.
        mvc.perform(get("/api/no-existe-esta-ruta")).andExpect(status().isUnauthorized());
        mvc.perform(get("/wp-login.php")).andExpect(status().isUnauthorized());
    }
}
