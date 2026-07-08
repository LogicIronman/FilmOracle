package com.filmoracle.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据库连接池工具——基于HikariCP
 * 从环境变量读取连接配置，提供全局连接池
 */
public class DatabaseUtil {

    private static final HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        String dbUrl = System.getenv("DB_URL");
        if (dbUrl == null || dbUrl.isEmpty()) {
            dbUrl = "jdbc:mysql://localhost:3307/filmoracle?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8";
        }
        String dbUser = System.getenv("DB_USER");
        if (dbUser == null || dbUser.isEmpty()) {
            dbUser = "filmoracle";
        }
        String dbPass = System.getenv("DB_PASSWORD");
        if (dbPass == null || dbPass.isEmpty()) {
            dbPass = "filmoracle";
        }

        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUser);
        config.setPassword(dbPass);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(600000);
        config.setPoolName("FilmOracle-Hikari");

        dataSource = new HikariDataSource(config);
        System.out.println("[DB] HikariCP pool initialized: " + dbUrl);
    }

    /**
     * 获取数据库连接
     */
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * 关闭连接池（应用卸载时调用）
     */
    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("[DB] HikariCP pool closed");
        }
    }
}
