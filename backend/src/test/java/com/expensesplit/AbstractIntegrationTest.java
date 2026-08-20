package com.expensesplit;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
