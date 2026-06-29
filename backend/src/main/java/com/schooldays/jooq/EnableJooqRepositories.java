package com.schooldays.jooq;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(JooqRepositoryRegistrar.class)
public @interface EnableJooqRepositories {

    String[] basePackages() default {};
}
