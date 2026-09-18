package com.ledger.config;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.Locale;

/**
 * Optional guard that turns any UPDATE/DELETE against the append-only split
 * tables into an immediate failure. Enabled only via
 * {@code ledger.write-guard.enabled=true} (tests). It proves at the SQL level
 * that confirmed split records are never rewritten.
 */
public class ImmutableTablesInspector implements StatementInspector {

    public static volatile boolean enabled = false;

    private static final String[] GUARDED_TABLES = {
            "split_bill", "split_participant", "split_event", "settlement", "ledger_entry"
    };

    @Override
    public String inspect(String sql) {
        if (!enabled) {
            return sql;
        }
        if (sql == null) {
            return sql;
        }
        String normalised = sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        boolean mutates = normalised.startsWith("update ") || normalised.startsWith("delete ");
        if (mutates) {
            for (String table : GUARDED_TABLES) {
                if (normalised.contains(table)) {
                    throw new ImmutableWriteAttemptException(sql);
                }
            }
        }
        return sql;
    }
}
