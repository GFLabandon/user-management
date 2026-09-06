package io.github.gflabandon.counselor.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Read-only compatibility entry; legacy writes are no longer mapped. */
@Controller
public class LegacyRouteController {
    @GetMapping({"/users", "/users/list"})
    public String directory() { return "redirect:/counselors"; }
}
