package com.church.qt.common;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

@Component
@RequiredArgsConstructor
public class DevotionCheckColumnInitializer {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void ensureDevotionCheckColumns() {
        ensureColumn("devotion_checks", "attitude_checked", "boolean not null default false");
        ensureColumn("devotion_checks", "note_count", "integer not null default 0");
        jdbcTemplate.execute("""
                update devotion_checks
                set note_count = case when note_checked = true then 1 else 0 end
                where note_count is null or (note_count = 0 and note_checked = true)
                """);
    }

    private void ensureColumn(String tableName, String columnName, String columnDefinition) {
        if (columnExists(tableName, columnName)) {
            return;
        }
        jdbcTemplate.execute("alter table " + tableName + " add column " + columnName + " " + columnDefinition);
    }

    private boolean columnExists(String tableName, String columnName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            if (hasColumn(metaData, connection.getCatalog(), tableName, columnName)) {
                return true;
            }
            return hasColumn(metaData, connection.getCatalog(), tableName.toUpperCase(), columnName.toUpperCase());
        } catch (Exception e) {
            throw new IllegalStateException("devotion_checks 칼럼 상태를 확인할 수 없습니다.", e);
        }
    }

    private boolean hasColumn(DatabaseMetaData metaData, String catalog, String tableName, String columnName) throws Exception {
        try (ResultSet columns = metaData.getColumns(catalog, null, tableName, columnName)) {
            return columns.next();
        }
    }
}
