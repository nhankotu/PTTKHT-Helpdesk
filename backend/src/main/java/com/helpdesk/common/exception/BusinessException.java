package com.helpdesk.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode errorCode;
    private final Map<String, Object> properties;

    public BusinessException(HttpStatus status, ErrorCode errorCode, String message) {
        this(status, errorCode, message, Collections.emptyMap());
    }

    public BusinessException(HttpStatus status, ErrorCode errorCode, String message, Map<String, Object> properties) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.properties = properties == null ? Collections.emptyMap() : Map.copyOf(properties);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public static BusinessException notFound(String resourceType, Object resourceId) {
        return new BusinessException(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                resourceType + " không tồn tại với mã: " + resourceId,
                Map.of("resourceType", resourceType, "resourceId", String.valueOf(resourceId))
        );
    }

    public static BusinessException forbidden(ErrorCode errorCode, String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, errorCode, message);
    }

    public static BusinessException conflict(ErrorCode errorCode, String message) {
        return new BusinessException(HttpStatus.CONFLICT, errorCode, message);
    }

    public static BusinessException unprocessable(ErrorCode errorCode, String message) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, errorCode, message);
    }
}
