package io.github.noahhhx.mimos.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class MimosApiApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine"));

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void contextLoads() {
    }

    @Test
    void healthIsUpIncludingDatabase() {
        String body = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build()
                .get()
                .uri("/actuator/health")
                .retrieve()
                .body(String.class);

        assertThat(body).contains("\"status\":\"UP\"");
        assertThat(body).contains("\"db\"");
    }

    @Test
    void baselineMigrationApplied() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '1'",
                Integer.class);

        assertThat(applied).isEqualTo(1);
    }
}
