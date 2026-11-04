package io.slayyyyer.orders.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Runs Flyway and exits, with no web server. This is what the Cloud Run migration job
 * invokes (%s), so the schema change is a separate, observable step from the rollout.
 */
@Component
@ConditionalOnProperty(name = "app.migrate-only", havingValue = "true")
public class MigrateOnlyRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrateOnlyRunner.class);

    private final Flyway flyway;
    private final ApplicationContext context;

    public MigrateOnlyRunner(Flyway flyway, ApplicationContext context) {
        this.flyway = flyway;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        MigrateResult result = flyway.migrate();
        log.info(
                "migrate-only: applied {} migration(s), schema now at version {}",
                result.migrationsExecuted,
                result.targetSchemaVersion);

        int exitCode = SpringApplication.exit(context, () -> 0);
        System.exit(exitCode);
    }
}
