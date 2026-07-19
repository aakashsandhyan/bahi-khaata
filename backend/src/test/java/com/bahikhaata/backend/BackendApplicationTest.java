package com.bahikhaata.backend;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "bahikhaata.db.path=build/test-context.db")
class BackendApplicationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("The application starts against SQLite with ddl-auto=validate")
    void contextLoads() throws Exception {
        // Reaching this line means Hibernate validated its (currently empty) mappings
        // against the database rather than modifying it. Once entities exist, a
        // mismatch here aborts startup — which is the behaviour we want.
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("SQLite");
        }
    }
}
