package com.example.oms.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Builds a pooled {@link DataSource}. Pooling (rather than opening a raw
 * JDBC connection per request) is what lets the service layer sustain
 * concurrent read/write load without exhausting database connections.
 *
 * Configuration precedence: environment variables (used by the Docker
 * deployment) override src/main/resources/application.properties (used
 * for local development), which is itself a fallback with sane defaults.
 */
public final class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);
    private static volatile HikariDataSource dataSource;

    private DatabaseConfig() {
    }

    public static DataSource getDataSource() {
        if (dataSource == null) {
            synchronized (DatabaseConfig.class) {
                if (dataSource == null) {
                    dataSource = build();
                }
            }
        }
        return dataSource;
    }

    private static HikariDataSource build() {
        Properties props = loadDefaults();

        String host = env("DB_HOST", props.getProperty("db.host", "localhost"));
        String port = env("DB_PORT", props.getProperty("db.port", "3306"));
        String name = env("DB_NAME", props.getProperty("db.name", "oms_db"));
        String user = env("DB_USER", props.getProperty("db.user", "oms_user"));
        String pass = env("DB_PASSWORD", props.getProperty("db.password", "oms_password"));

        String jdbcUrl = "jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                .formatted(host, port, name);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(Integer.parseInt(env("DB_POOL_SIZE", "10")));
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10_000);
        config.setPoolName("oms-pool");
        // MySQL-recommended defaults for statement caching over a pooled connection.
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        log.info("Initializing connection pool -> {} (pool size={})", jdbcUrl, config.getMaximumPoolSize());
        return new HikariDataSource(config);
    }

    private static Properties loadDefaults() {
        Properties props = new Properties();
        try (InputStream is = DatabaseConfig.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            log.warn("Could not load application.properties, relying on environment/defaults", e);
        }
        return props;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /** Used by tests/shutdown hooks; not required in normal operation. */
    public static void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
