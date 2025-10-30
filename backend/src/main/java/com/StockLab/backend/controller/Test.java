package com.StockLab.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Test {

    @GetMapping("/api/test")
    public String test() {
        return "Spring Boot is running successfully!";
    }
}
