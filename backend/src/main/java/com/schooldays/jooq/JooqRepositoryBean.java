package com.schooldays.jooq;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.jooq.Table;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface JooqRepositoryBean {

    String table() default "";

    @SuppressWarnings("rawtypes")
    Class<? extends Table> tableClass() default Table.class;

    String idField() default "id";
}
