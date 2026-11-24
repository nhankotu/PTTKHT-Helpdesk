package com.helpdesk.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class cho toàn bộ Integration Tests chạy trên MySQL 8.0 thật (Testcontainers).
 * Dùng mô hình Singleton Container để tất cả Integration Test chia sẻ 1 MySQL instance duy nhất,
 * giúp rút ngắn thời gian chạy test và tái sử dụng Spring ApplicationContext.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractMySqlIntegrationTest {

    static final MySQLContainer<?> MYSQL;

    static {
        MYSQL = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("helpdesk_test")
                .withUsername("test_user")
                .withPassword("test_pass")
                .withCommand(
                        "--character-set-server=utf8mb4",
                        "--collation-server=utf8mb4_unicode_ci",
                        "--default-time-zone=+00:00"
                );
        MYSQL.start();
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                MYSQL.getJdbcUrl() + "?useUnicode=true&characterEncoding=utf8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        // Tăng chu kỳ scheduler trong test để tránh background thread chạy xen vào assertion
        registry.add("app.outbox.poll-ms", () -> "600000");
        registry.add("app.sla.scan-ms", () -> "600000");
    }
}
