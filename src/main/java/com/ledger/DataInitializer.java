package com.ledger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Initializes dummy sample data from classpath:seed-data.sql when the application boots up.
 * Controlled by the ledger.seed-data.enabled property (enabled in main app, disabled in unit tests).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ledger.seed-data.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM journal_entry")) {
            if (rs.next() && rs.getInt(1) > 0) {
                log.info("Database already initialized (journal_entry count={}), skipping seed-data.sql", rs.getInt(1));
                return;
            }
        } catch (Exception e) {
            log.warn("Could not check existing journal_entry count: {}", e.getMessage());
        }

        log.info("Loading sample dummy data from classpath:seed-data.sql ...");
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("seed-data.sql"));
            populator.setContinueOnError(false);
            populator.execute(dataSource);
            log.info("Successfully loaded sample dummy data for acme-corp and globex-corp.");
        } catch (Exception e) {
            log.error("Failed to load seed-data.sql", e);
        }
    }
}
