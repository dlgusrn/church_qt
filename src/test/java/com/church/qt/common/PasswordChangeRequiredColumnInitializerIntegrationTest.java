package com.church.qt.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PasswordChangeRequiredColumnInitializerIntegrationTest {

    @Autowired
    private PasswordChangeRequiredColumnInitializer passwordChangeRequiredColumnInitializer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("password_change_required 칼럼이 없으면 teachers 테이블에 다시 추가한다")
    void ensurePasswordChangeRequiredColumn_addsMissingColumn() {
        jdbcTemplate.execute("alter table teachers drop column password_change_required");

        passwordChangeRequiredColumnInitializer.ensurePasswordChangeRequiredColumn();

        assertEquals(1, countColumn("teachers", "password_change_required"));
    }

    private Integer countColumn(String tableName, String columnName) {
        return jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.columns
                where upper(table_name) = upper(?)
                  and upper(column_name) = upper(?)
                """,
                Integer.class,
                tableName,
                columnName
        );
    }
}
