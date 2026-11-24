package com.helpdesk.common.exception;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_TYPE_BASE = "https://helpdesk.local/errors/";

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException ex, HttpServletRequest request) {
        ProblemDetail pd = createProblemDetail(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request);
        ex.getProperties().forEach(pd::setProperty);
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, Object>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> Map.<String, Object>of(
                        "field", err.getField(),
                        "message", err.getDefaultMessage() != null ? err.getDefaultMessage() : "Giá trị không hợp lệ",
                        "rejectedValue", err.getRejectedValue() != null ? err.getRejectedValue().toString() : ""
                ))
                .toList();
        ProblemDetail pd = createProblemDetail(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "Dữ liệu đầu vào không hợp lệ.",
                request
        );
        pd.setProperty("fieldErrors", fieldErrors);
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<Map<String, Object>> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> Map.<String, Object>of(
                        "field", v.getPropertyPath().toString(),
                        "message", v.getMessage()
                ))
                .toList();
        ProblemDetail pd = createProblemDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ErrorCode.VALIDATION_ERROR,
                "Tham số không hợp lệ.",
                request
        );
        pd.setProperty("fieldErrors", fieldErrors);
        return pd;
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                ErrorCode.MISSING_HEADER,
                "Thiếu header bắt buộc: " + ex.getHeaderName(),
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMalformedJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                ErrorCode.MALFORMED_REQUEST,
                "Nội dung JSON không hợp lệ hoặc sai kiểu dữ liệu.",
                request
        );
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ProblemDetail handleOptimisticLock(Exception ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.CONFLICT,
                ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                "Dữ liệu đã được cập nhật bởi một thao tác khác. Vui lòng tải lại bản mới nhất.",
                request
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ErrorCode.ATTACHMENT_TOO_LARGE,
                "Kích thước tệp đính kèm vượt quá giới hạn cho phép (25MB).",
                request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.FORBIDDEN,
                ErrorCode.FORBIDDEN,
                "Bạn không có quyền thực hiện thao tác này.",
                request
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED,
                "Phiên đăng nhập không hợp lệ hoặc đã hết hạn.",
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR,
                "Đã xảy ra lỗi hệ thống không mong muốn.",
                request
        );
    }

    private ProblemDetail createProblemDetail(
            HttpStatus status,
            ErrorCode errorCode,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setType(URI.create(ERROR_TYPE_BASE + errorCode.name().toLowerCase().replace('_', '-')));
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("errorCode", errorCode.name());
        pd.setProperty("timestamp", Instant.now(clock).toString());
        String requestId = request.getHeader("X-Request-Id");
        pd.setProperty("traceId", (requestId != null && !requestId.isBlank()) ? requestId : "req-" + UUID.randomUUID().toString().substring(0, 8));
        return pd;
    }
}
