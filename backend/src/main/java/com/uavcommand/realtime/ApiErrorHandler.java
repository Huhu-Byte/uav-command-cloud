package com.uavcommand.realtime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException error) {
        int status = error.getStatusCode().value();
        String message = error.getReason() == null || error.getReason().isBlank()
                ? "请求暂时无法完成，请稍后重试"
                : error.getReason();
        return ResponseEntity.status(error.getStatusCode()).body(new ApiErrorResponse(status, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequest(HttpMessageNotReadableException error) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(400, "请求内容格式不正确，请刷新页面后重试"));
    }

    public record ApiErrorResponse(int status, String message) { }
}
