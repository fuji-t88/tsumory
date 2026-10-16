package com.example.tsumory.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * URL の値を変換できないとき（{@code /posts/abc/edit}、{@code /diary/2026-13-01} など）を、400 ではなく 404 にする。
 *
 * <p>URL の間違いは「ページが見つかりません」として扱う（phase1-spec.md の「8. エラー処理」）。応答は {@code sendError} で返し、
 * {@code DiaryNotFoundException} などと同じエラー画面に任せる。
 */
@ControllerAdvice
public class NotFoundExceptionHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public void handleTypeMismatch(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value());
    }
}
