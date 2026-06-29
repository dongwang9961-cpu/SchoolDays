package com.schooldays.jooq;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.lang.NonNull;

public class JooqRepositoryFactoryBean<T> implements FactoryBean<T>, BeanFactoryAware {

    private final Class<T> repositoryInterface;
    private final Table<? extends Record> table;
    private final Field<?> idField;
    private BeanFactory beanFactory;

    public JooqRepositoryFactoryBean(
            Class<T> repositoryInterface,
            Table<? extends Record> table,
            Field<?> idField) {
        this.repositoryInterface = repositoryInterface;
        this.table = table;
        this.idField = idField;
    }

    @Override
    public void setBeanFactory(@NonNull BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public T getObject() {
        DSLContext dsl = beanFactory.getBean(DSLContext.class);
        return new JooqRepositoryFactory(dsl).createRepository(repositoryInterface, table, idField);
    }

    @Override
    public Class<?> getObjectType() {
        return repositoryInterface;
    }
}
