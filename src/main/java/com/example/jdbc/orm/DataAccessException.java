package com.example.jdbc.orm;

/**
 * 数据访问异常。
 *
 * <p>JDBCTool 内部把受检的 {@link java.sql.SQLException} 统一包装成本运行时异常，
 * 使调用方不必在每个方法上声明 {@code throws SQLException}；
 * 原始异常通过 {@link #getCause()} 保留，方便定位 SQL 问题。</p>
 *
 * @author homework
 */
public class DataAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
