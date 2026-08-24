package io.github.noahhhx.mimos.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MimosApiApplicationTests extends ApiIntegrationTestSupport {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void contextLoads() {}

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
        Integer applied =
                jdbc.queryForObject("select count(*) from flyway_schema_history where version = '1'", Integer.class);

        assertThat(applied).isEqualTo(1);
    }
}
