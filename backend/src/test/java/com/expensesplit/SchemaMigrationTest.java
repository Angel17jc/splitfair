package com.expensesplit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica que la migracion V1 produce el esquema que las entidades JPA
 * esperan, y que las reglas declaradas en la base se cumplen de verdad.
 *
 * El hecho de que este contexto arranque ya es la primera asercion: con
 * ddl-auto=validate, Hibernate compara cada entidad contra las tablas
 * reales y falla el arranque si algo no encaja.
 */
class SchemaMigrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Flyway aplica la migracion V1 y crea las seis tablas del modelo")
    void migracionAplicada() {
        List<String> tablas = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                        "WHERE table_schema = 'public' ORDER BY table_name",
                String.class);

        assertThat(tablas).contains(
                "users", "groups", "group_members",
                "expenses", "expense_splits", "settlements");

        Integer aplicadas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(aplicadas).isPositive();
    }

    @Test
    @DisplayName("Los importes son NUMERIC(12,2), no punto flotante")
    void importesConPrecisionDecimalExacta() {
        List<String> tipos = jdbc.queryForList(
                "SELECT data_type FROM information_schema.columns " +
                        "WHERE table_name IN ('expenses', 'expense_splits', 'settlements') " +
                        "AND column_name IN ('amount', 'amount_owed')",
                String.class);

        assertThat(tipos).isNotEmpty().allMatch("numeric"::equals);
    }

    @Test
    @DisplayName("Un usuario no puede pertenecer dos veces al mismo grupo")
    void miembroDuplicadoRechazado() {
        long userId = insertarUsuario("ana@test.com");
        long groupId = insertarGrupo("Piso", userId);

        jdbc.update("INSERT INTO group_members (group_id, user_id, role, joined_at) " +
                "VALUES (?, ?, 'MEMBER', now())", groupId, userId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO group_members (group_id, user_id, role, joined_at) " +
                        "VALUES (?, ?, 'MEMBER', now())", groupId, userId))
                .hasMessageContaining("uk_group_members_group_user");
    }

    @Test
    @DisplayName("Un gasto con importe cero o negativo es rechazado por la base")
    void gastoConImporteNoPositivoRechazado() {
        long userId = insertarUsuario("beto@test.com");
        long groupId = insertarGrupo("Viaje", userId);

        assertThatThrownBy(() -> insertarGasto(groupId, userId, "0.00"))
                .hasMessageContaining("ck_expenses_amount_positive");

        assertThatThrownBy(() -> insertarGasto(groupId, userId, "-10.00"))
                .hasMessageContaining("ck_expenses_amount_positive");
    }

    @Test
    @DisplayName("Una liquidacion de un usuario hacia si mismo es rechazada")
    void liquidacionHaciaUnoMismoRechazada() {
        long userId = insertarUsuario("carla@test.com");
        long groupId = insertarGrupo("Cena", userId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO settlements (group_id, paid_by, paid_to, amount, status, created_at) " +
                        "VALUES (?, ?, ?, 10.00, 'PENDING', now())", groupId, userId, userId))
                .hasMessageContaining("ck_settlements_distinct_parties");
    }

    @Test
    @DisplayName("La moneda del grupo debe ser un codigo de tres mayusculas")
    void monedaConFormatoInvalidoRechazada() {
        long userId = insertarUsuario("elena@test.com");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO groups (name, created_by, currency, created_at) " +
                        "VALUES ('Malo', ?, 'eur', now())", userId))
                .hasMessageContaining("ck_groups_currency");
    }

    @Test
    @DisplayName("El tipo de reparto de un gasto debe ser uno de los conocidos")
    void tipoDeRepartoDesconocidoRechazado() {
        long userId = insertarUsuario("fran@test.com");
        long groupId = insertarGrupo("Reparto", userId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO expenses (group_id, paid_by, description, amount, split_type, " +
                        "category, expense_date, created_at) " +
                        "VALUES (?, ?, 'test', 10.00, 'APORTACION', 'OTROS', current_date, now())",
                groupId, userId))
                .hasMessageContaining("ck_expenses_split_type");
    }

    @Test
    @DisplayName("Una liquidacion PENDING no puede tener fecha de confirmacion")
    void estadoYFechaDeLiquidacionCoherentes() {
        long pagador = insertarUsuario("gema@test.com");
        long receptor = insertarUsuario("hugo@test.com");
        long groupId = insertarGrupo("Cuentas", pagador);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO settlements (group_id, paid_by, paid_to, amount, status, " +
                        "created_at, settled_at) " +
                        "VALUES (?, ?, ?, 10.00, 'PENDING', now(), now())",
                groupId, pagador, receptor))
                .hasMessageContaining("ck_settlements_settled_consistent");
    }

    @Test
    @DisplayName("Borrar un grupo arrastra sus miembros, gastos y splits")
    void borradoDeGrupoEnCascada() {
        long userId = insertarUsuario("diego@test.com");
        long groupId = insertarGrupo("Temporal", userId);

        jdbc.update("INSERT INTO group_members (group_id, user_id, role, joined_at) " +
                "VALUES (?, ?, 'ADMIN', now())", groupId, userId);
        long expenseId = insertarGasto(groupId, userId, "50.00");
        jdbc.update("INSERT INTO expense_splits (expense_id, user_id, amount_owed) " +
                "VALUES (?, ?, 50.00)", expenseId, userId);

        jdbc.update("DELETE FROM groups WHERE id = ?", groupId);

        assertThat(contar("group_members", "group_id", groupId)).isZero();
        assertThat(contar("expenses", "group_id", groupId)).isZero();
        assertThat(contar("expense_splits", "expense_id", expenseId)).isZero();

        // El usuario sobrevive: su historial contable en otros grupos no se toca.
        assertThat(contar("users", "id", userId)).isEqualTo(1);
    }

    // --- utilidades ---

    private long insertarUsuario(String email) {
        return jdbc.queryForObject(
                "INSERT INTO users (name, email, password_hash, created_at) " +
                        "VALUES (?, ?, 'x', now()) RETURNING id",
                Long.class, email.split("@")[0], email);
    }

    private long insertarGrupo(String nombre, long creadorId) {
        return jdbc.queryForObject(
                "INSERT INTO groups (name, created_by, currency, created_at) " +
                        "VALUES (?, ?, 'USD', now()) RETURNING id",
                Long.class, nombre, creadorId);
    }

    private long insertarGasto(long groupId, long paidBy, String importe) {
        return jdbc.queryForObject(
                "INSERT INTO expenses (group_id, paid_by, description, amount, split_type, " +
                        "category, expense_date, created_at) " +
                        "VALUES (?, ?, 'test', ?::numeric, 'EQUAL', 'OTROS', current_date, now()) RETURNING id",
                Long.class, groupId, paidBy, importe);
    }

    private int contar(String tabla, String columna, long valor) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tabla + " WHERE " + columna + " = ?", Integer.class, valor);
    }
}
