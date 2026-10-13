package com.example.tsumory.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 対象がない、他人のもの、または今日と昨日以外のもののときに投げる（存在しない場合と区別しない）。
 *
 * <p>controller が受け、メッセージを付けてリダイレクトする。受け損ねた場合は 404 になる。
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " not found: id=" + id);
    }
}
