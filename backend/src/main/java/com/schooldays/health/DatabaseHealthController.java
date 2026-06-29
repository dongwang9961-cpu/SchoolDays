package com.schooldays.health;

import java.util.Map;

import org.jooq.DSLContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class DatabaseHealthController {

    private final DSLContext dsl;

    public DatabaseHealthController(DSLContext dsl) {
        this.dsl = dsl;
    }

    @GetMapping("/database")
    public Map<String, Object> database() {
        Integer result = dsl.selectOne().fetchOne(0, Integer.class);
        return Map.of(
                "status", "UP",
                "database", "PostgreSQL",
                "dataAccess", "jOOQ",
                "result", result
        );
    }
}
