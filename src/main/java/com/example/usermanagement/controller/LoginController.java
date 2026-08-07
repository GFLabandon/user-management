package com.example.usermanagement.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    public static final String SESSION_USER_KEY = "user";

    private final String adminUsername;
    private final String adminPassword;

    public LoginController(@Value("${app.auth.username}") String adminUsername,
                           @Value("${app.auth.password}") String adminPassword) {
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @GetMapping("/login")
    public String login(HttpSession session) {
        if (session.getAttribute(SESSION_USER_KEY) != null) {
            return "redirect:/users/list";
        }
        return "login";
    }

    @PostMapping("/login")
    public String authenticate(@RequestParam String username,
                               @RequestParam String password,
                               Model model,
                               HttpSession session) {
        if (adminUsername.equals(username) && adminPassword.equals(password)) {
            session.setAttribute(SESSION_USER_KEY, username);
            return "redirect:/users/list";
        }

        model.addAttribute("error", "Incorrect username or password.");
        return "login";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
