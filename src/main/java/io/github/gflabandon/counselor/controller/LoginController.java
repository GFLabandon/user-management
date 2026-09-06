package io.github.gflabandon.counselor.controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class LoginController {
    private final boolean demo;
    public LoginController(@Value("${app.demo:false}") boolean demo) { this.demo = demo; }
    @GetMapping("/login")
    public String login(Model model) { model.addAttribute("demo", demo); return "login"; }
}
