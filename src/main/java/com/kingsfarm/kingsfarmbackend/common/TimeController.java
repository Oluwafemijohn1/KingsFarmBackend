package com.kingsfarm.kingsfarmbackend.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** The server's clock, so every screen shows the same (Nigerian) time instead of whatever each device's own clock says. */
@RestController
@RequestMapping("/api/v1/time")
public class TimeController {

    @GetMapping
    public Map<String, Object> now() {
        return Map.of("epochMillis", System.currentTimeMillis());
    }
}
