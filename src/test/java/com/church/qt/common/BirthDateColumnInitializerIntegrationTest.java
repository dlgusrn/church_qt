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
class BirthDateColumnInitializerIntegrationTest {

    @Autowired
    private BirthDateColumnInitializer birthDateColumnInitializer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("birth_date 칼럼이 없으면 teachers와 students 테이블에 다시 추가한다")
    void ensureBirthDateColumns_addsMissingColumns() {
        jdbcTemplate.execute("alter table teachers drop column birth_date");
        jdbcTemplate.execute("alter table students drop column birth_date");

        birthDateColumnInitializer.ensureBirthDateColumns();

        assertEquals(1, countColumn("teachers", "birth_date"));
        assertEquals(1, countColumn("students", "birth_date"));
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
