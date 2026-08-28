package com.expensesplit;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base para tests de integracion sobre un PostgreSQL real.
 *
 * Se usa PostgreSQL y no H2 a proposito: H2 tolera SQL que PostgreSQL
 * rechaza, no implementa igual NUMERIC ni las palabras reservadas, y
 * dejaria pasar migraciones que fallarian en produccion. Un test que
 * miente es peor que no tener test.
 *
 * El contenedor sigue el patron singleton (arranque en bloque estatico,
 * sin @Testcontainers) para que todas las clases de test compartan una
 * sola instancia en la misma JVM en vez de levantar una por clase.
 * Testcontainers lo destruye al terminar mediante su contenedor Ryuk.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("expense_split_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        POSTGRES.start();
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Cada test arranca con la base vacia.
     *
     * <p>Los tests que van por HTTP no pueden apoyarse en la reversion
     * automatica de @Transactional: la peticion se atiende en su propia
     * transaccion, que confirma antes de que el test termine. Sin esta
     * limpieza los datos se acumulan entre clases y aparecen fallos que
     * dependen del orden de ejecucion.
     *
     * <p>Se ejecuta antes que el @BeforeEach de las subclases, de modo que
     * estas pueden preparar su escenario sobre una base ya limpia.
     */
    @BeforeEach
    void limpiarBaseDeDatos() {
        jdbcTemplate.execute("TRUNCATE expense_splits, expenses, settlements, " +
                "group_members, groups, users RESTART IDENTITY CASCADE");
    }

    /**
     * Nombre de la cookie de sesion. Se repite aqui a proposito en vez de
     * leerlo de la configuracion: si alguien lo cambia, estos tests deben
     * fallar y obligar a revisar el cambio, porque renombrar la cookie deja
     * fuera a todas las sesiones abiertas.
     */
    protected static final String COOKIE_REFRESCO = "refresh_token";

    /** El refresh token que la respuesta acaba de sellar, o null si no sello ninguno. */
    protected static String refrescoDe(MockHttpServletResponse response) {
        Cookie cookie = response.getCookie(COOKIE_REFRESCO);
        return cookie == null ? null : cookie.getValue();
    }

    /** La cookie que enviaria el navegador en la siguiente peticion. */
    protected static Cookie cookieDeSesion(String refreshToken) {
        return new Cookie(COOKIE_REFRESCO, refreshToken);
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
