package com.schooldays.jooq;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

public class JooqRepositoryFactory {

    private final DSLContext dsl;

    public JooqRepositoryFactory(DSLContext dsl) {
        this.dsl = dsl;
    }

    public <T> T createRepository(
            Class<T> repositoryInterface,
            Table<? extends Record> table,
            Field<?> idField) {
        InvocationHandler handler = new JooqRepositoryInvocationHandler(dsl, table, idField);
        Object proxy = Proxy.newProxyInstance(
                repositoryInterface.getClassLoader(),
                new Class<?>[]{repositoryInterface},
                handler
        );
        return repositoryInterface.cast(proxy);
    }
}
