package com.example.oms.exception;

/** Wraps low-level java.sql.SQLException so callers never handle JDBC checked exceptions directly. */
public class DataAccessException extends OmsException {
    public DataAccessException(String message, Throwable cause) {
        super("DATA_ACCESS_ERROR", message, cause);
    }
}
