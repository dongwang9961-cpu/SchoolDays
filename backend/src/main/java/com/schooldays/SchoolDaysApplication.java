package com.schooldays;

import com.schooldays.jooq.EnableJooqRepositories;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableJooqRepositories(basePackages = "com.schooldays")
@SpringBootApplication
public class SchoolDaysApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchoolDaysApplication.class, args);
    }
}
