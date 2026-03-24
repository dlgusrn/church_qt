package com.church.qt.common;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component
@RequiredArgsConstructor
public class TeacherCheckAccessColumnInitializer {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initialize() {
        ensureColumn("teachers", "can_check_all_students", "boolean not null default false");
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
        } catch (SQLException e) {
            throw new IllegalStateException("can_check_all_students 칼럼 존재 여부를 확인할 수 없습니다.", e);
        }
    }

    private boolean hasColumn(DatabaseMetaData metaData, String catalog, String tableName, String columnName) throws SQLException {
        try (ResultSet columns = metaData.getColumns(catalog, null, tableName, columnName)) {
            return columns.next();
        }
    }
}
