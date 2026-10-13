package com.example.tsumory.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PostController {

    // TODO 今日のつぶやき画面（投稿・一覧・編集モード）は phase1-spec.md「11. 実装の順番」の 3・4 で作る
    @GetMapping("/")
    public String index() {
        return "index";
    }
}
