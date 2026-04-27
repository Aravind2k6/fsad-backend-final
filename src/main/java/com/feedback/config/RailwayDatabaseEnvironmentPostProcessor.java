package com.feedback.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class RailwayDatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String DEFAULT_DATABASE_NAME = "Student_Feedback_System";
    private static final String DEFAULT_QUERY = "useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        RailwayConnectionDetails configuredConnection = RailwayConnectionDetails.fromMaybeMysqlUrl(
                environment.getProperty("spring.datasource.url")
        );
        RailwayConnectionDetails railwayConnection = RailwayConnectionDetails.resolve(environment);

        if (StringUtils.hasText(configuredConnection.jdbcUrl())) {
            defaults.put("spring.datasource.url", configuredConnection.jdbcUrl());
        } else if (!StringUtils.hasText(environment.getProperty("spring.datasource.url"))) {
            defaults.put(
                    "spring.datasource.url",
                    firstNonBlank(
                            railwayConnection.jdbcUrl(),
                            buildJdbcUrl("localhost", "3306", DEFAULT_DATABASE_NAME, null)
                    )
            );
        }

        if (!StringUtils.hasText(environment.getProperty("spring.datasource.username"))) {
            defaults.put(
                    "spring.datasource.username",
                    firstNonBlank(
                            environment.getProperty("DB_USERNAME"),
                            configuredConnection.username(),
                            railwayConnection.username(),
                            environment.getProperty("MYSQLUSER"),
                            environment.getProperty("MYSQL_USER"),
                            "root"
                    )
            );
        }

        if (!StringUtils.hasText(environment.getProperty("spring.datasource.password"))) {
            String password = firstNonBlank(
                    environment.getProperty("DB_PASSWORD"),
                    configuredConnection.password(),
                    railwayConnection.password(),
                    environment.getProperty("MYSQLPASSWORD"),
                    environment.getProperty("MYSQL_PASSWORD")
            );
            defaults.put(
                    "spring.datasource.password",
                    password != null ? password : ""
            );
        }

        if (!defaults.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("railwayDatabaseDefaults", defaults));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String buildJdbcUrl(String host, String port, String database, String rawQuery) {
        String resolvedHost = StringUtils.hasText(host) ? host.trim() : "localhost";
        String resolvedPort = StringUtils.hasText(port) ? port.trim() : "3306";
        String resolvedDatabase = StringUtils.hasText(database) ? database.trim() : DEFAULT_DATABASE_NAME;
        String query = mergeQuery(rawQuery);
        return "jdbc:mysql://" + resolvedHost + ":" + resolvedPort + "/" + resolvedDatabase + "?" + query;
    }

    private static String mergeQuery(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();

        if (StringUtils.hasText(rawQuery)) {
            for (String entry : rawQuery.split("&")) {
                if (!StringUtils.hasText(entry)) {
                    continue;
                }

                int separator = entry.indexOf('=');
                if (separator >= 0) {
                    String key = decode(entry.substring(0, separator));
                    String value = decode(entry.substring(separator + 1));
                    params.putIfAbsent(key, value);
                } else {
                    params.putIfAbsent(decode(entry), "");
                }
            }
        }

        params.putIfAbsent("useSSL", "false");
        params.putIfAbsent("serverTimezone", "UTC");
        params.putIfAbsent("allowPublicKeyRetrieval", "true");

        return params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "&" + right)
                .orElse(DEFAULT_QUERY);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private record RailwayConnectionDetails(String jdbcUrl, String username, String password) {

        private static RailwayConnectionDetails resolve(ConfigurableEnvironment environment) {
            String publicUrl = firstNonBlank(
                    environment.getProperty("MYSQL_PUBLIC_URL"),
                    environment.getProperty("MYSQL_PUBLIC_DATABASE_URL")
            );
            if (StringUtils.hasText(publicUrl)) {
                return fromMysqlUrl(publicUrl);
            }

            String internalUrl = firstNonBlank(environment.getProperty("MYSQL_URL"));
            if (StringUtils.hasText(internalUrl)) {
                return fromMysqlUrl(internalUrl);
            }

            String host = firstNonBlank(
                    environment.getProperty("MYSQLHOST"),
                    environment.getProperty("MYSQL_HOST")
            );
            if (!StringUtils.hasText(host)) {
                return new RailwayConnectionDetails(null, null, null);
            }

            String port = firstNonBlank(
                    environment.getProperty("MYSQLPORT"),
                    environment.getProperty("MYSQL_PORT"),
                    "3306"
            );
            String database = firstNonBlank(
                    environment.getProperty("MYSQLDATABASE"),
                    environment.getProperty("MYSQL_DATABASE"),
                    DEFAULT_DATABASE_NAME
            );
            String username = firstNonBlank(
                    environment.getProperty("MYSQLUSER"),
                    environment.getProperty("MYSQL_USER")
            );
            String password = firstNonBlank(
                    environment.getProperty("MYSQLPASSWORD"),
                    environment.getProperty("MYSQL_PASSWORD")
            );

            return new RailwayConnectionDetails(buildJdbcUrl(host, port, database, null), username, password);
        }

        private static RailwayConnectionDetails fromMaybeMysqlUrl(String value) {
            if (!StringUtils.hasText(value) || !value.trim().startsWith("mysql://")) {
                return new RailwayConnectionDetails(null, null, null);
            }
            return fromMysqlUrl(value);
        }

        private static RailwayConnectionDetails fromMysqlUrl(String mysqlUrl) {
            URI uri = URI.create(mysqlUrl.trim());
            String username = null;
            String password = null;

            if (StringUtils.hasText(uri.getUserInfo())) {
                String[] userInfoParts = uri.getUserInfo().split(":", 2);
                username = decode(userInfoParts[0]);
                if (userInfoParts.length > 1) {
                    password = decode(userInfoParts[1]);
                }
            }

            String database = uri.getPath();
            if (StringUtils.hasText(database) && database.startsWith("/")) {
                database = database.substring(1);
            }

            return new RailwayConnectionDetails(
                    buildJdbcUrl(uri.getHost(), uri.getPort() > 0 ? String.valueOf(uri.getPort()) : null, database, uri.getQuery()),
                    username,
                    password
            );
        }
    }
}
