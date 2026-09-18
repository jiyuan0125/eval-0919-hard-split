package com.ledger.config;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Registers the append-only-table statement inspector on every Hibernate
 * session. The inspector is a no-op unless
 * {@code ledger.write-guard.enabled=true} flips its flag (tests use it to
 * prove split rows are never updated/deleted).
 */
@Configuration
public class JpaGuardConfig {

    @Bean
    HibernatePropertiesCustomizer inspectorCustomizer(Environment environment) {
        ImmutableTablesInspector.enabled =
                environment.getProperty("ledger.write-guard.enabled", Boolean.class, false);
        return properties -> properties.put("hibernate.session_factory.statement_inspector",
                new ImmutableTablesInspector());
    }
}
