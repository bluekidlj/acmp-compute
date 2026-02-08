package com.acmp.compute.exception;

/** 资源不存在时抛出 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
