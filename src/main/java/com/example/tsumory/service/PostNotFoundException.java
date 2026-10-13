package com.example.tsumory.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** つぶやきがない、他人のもの、または今日と昨日以外のもののときに投げる。 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class PostNotFoundException extends RuntimeException {

    public PostNotFoundException(Long postId) {
        super("Post not found: id=" + postId);
    }
}
