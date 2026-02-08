package com.acmp.compute.exception;

/** 无权限访问时抛出 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
