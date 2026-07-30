package com.mdau.ushirika.config;

import org.hibernate.dialect.PostgreSQLDialect;

/**
 * Overrides CREATE INDEX to use IF NOT EXISTS so repeated deployments
 * against a populated schema don't corrupt the JDBC connection state.
 * Hibernate 6 generates bare CREATE INDEX (no IF NOT EXISTS), which causes
 * PostgreSQL to abort the transaction on duplicate index names, cascading
 * into a full Spring context startup failure even with halt_on_error=false.
 */
public class PostgresDialect extends PostgreSQLDialect {

    @Override
    public String getCreateIndexString(boolean unique) {
        return "create " + (unique ? "unique " : "") + "index if not exists";
    }
}
