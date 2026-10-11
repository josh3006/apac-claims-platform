package com.example.claims;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseMigrationTest {
    @Test
    void freshDatabaseMigratesAndSubsequentStartupIsANoOp() {
        var datasource = datasource();
        var flyway = Flyway.configure().dataSource(datasource).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThatThrownBy(flyway::clean).isInstanceOf(FlywayException.class);
    }

    @Test
    void versionOneUpgradePreservesClaimsHistoryAndPendingEvents() {
        var datasource = datasource();
        Flyway.configure().dataSource(datasource).target("1").load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        UUID id = seed(jdbc);
        var upgraded = Flyway.configure().dataSource(datasource).load();
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
        assertPreserved(jdbc, id);
    }

    @Test
    void unversionedLegacySchemaRequiresExplicitBaselineAndPreservesData() {
        var datasource = datasource();
        // Generate the actual legacy Hibernate schema, including H2's native enum column types.
        try (var schema = new org.hibernate.cfg.Configuration()
            .addAnnotatedClass(Claim.class).addAnnotatedClass(OutboxEvent.class)
            .setProperty("hibernate.connection.url", datasource.getUrl())
            .setProperty("hibernate.connection.username", "sa")
            .setProperty("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
            .setProperty("hibernate.hbm2ddl.auto", "create")
            .buildSessionFactory()) {
            assertThat(schema.isOpen()).isTrue();
        }
        var jdbc = new JdbcTemplate(datasource);
        UUID id = seed(jdbc);
        assertThatThrownBy(() -> Flyway.configure().dataSource(datasource).load().migrate()).isInstanceOf(FlywayException.class);
        var adopted = Flyway.configure().dataSource(datasource).baselineOnMigrate(true).baselineVersion("1").load();
        assertThat(adopted.migrate().migrationsExecuted).isEqualTo(1);
        assertPreserved(jdbc, id);
        assertThat(adopted.info().current().getVersion().getVersion()).isEqualTo("2");
    }

    private DriverManagerDataSource datasource() {
        return new DriverManagerDataSource("jdbc:h2:mem:migration_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    private UUID seed(JdbcTemplate jdbc) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO claims(id, version, claimant, type, market, currency, description, estimated_amount, status) VALUES (?, 0, 'legacy-user', 'MOTOR', 'MY', 'MYR', 'Existing claim', 123.45, 'SUBMITTED')", id);
        jdbc.update("INSERT INTO claim_history(claim_id, sequence_number, actor, status, message) VALUES (?, 0, 'legacy-user', 'SUBMITTED', 'Original history')", id);
        jdbc.update("INSERT INTO outbox_events(id, claim_id, claim_version, status) VALUES (?, ?, 0, 'SUBMITTED')", UUID.randomUUID(), id);
        return id;
    }

    private void assertPreserved(JdbcTemplate jdbc, UUID id) {
        assertThat(jdbc.queryForObject("SELECT description FROM claims WHERE id = ?", String.class, id)).isEqualTo("Existing claim");
        assertThat(jdbc.queryForObject("SELECT estimated_amount FROM claims WHERE id = ?", java.math.BigDecimal.class, id)).isEqualByComparingTo("123.45");
        assertThat(jdbc.queryForObject("SELECT message FROM claim_history WHERE claim_id = ?", String.class, id)).isEqualTo("Original history");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE claim_id = ? AND published_at IS NULL", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM claim_submissions", Long.class)).isZero();
    }
}
