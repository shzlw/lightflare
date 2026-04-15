package com.lightflare.server.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebUiConfig {

    @GetMapping({"/", "/login", "/workspace", "/workspace/**"})
    public String forwardWebUi() {
        return "forward:/index.html";
    }
}
