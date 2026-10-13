package com.example.tsumory.controller;

import com.example.tsumory.config.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    /** ログイン済みなら今日のつぶやきへ移す。未ログインなら principal は {@code null}。 */
    @GetMapping("/login")
    public String login(@AuthenticationPrincipal CustomUserDetails principal) {
        if (principal != null) {
            return "redirect:/";
        }
        return "login";
    }
}
