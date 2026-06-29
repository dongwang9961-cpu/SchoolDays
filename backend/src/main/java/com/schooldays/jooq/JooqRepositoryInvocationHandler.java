package com.schooldays.jooq;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

public class JooqRepositoryInvocationHandler implements InvocationHandler {

    private final DSLContext dsl;
    private final Table<? extends Record> table;
    private final Field<?> idField;

    public JooqRepositoryInvocationHandler(
            DSLContext dsl,
            Table<? extends Record> table,
            Field<?> idField) {
        this.dsl = dsl;
        this.table = table;
        this.idField = idField;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }
        if (method.isDefault()) {
            return MethodHandles.privateLookupIn(method.getDeclaringClass(), MethodHandles.lookup())
                    .unreflectSpecial(method, method.getDeclaringClass())
                    .bindTo(proxy)
                    .invokeWithArguments(args == null ? new Object[0] : args);
        }

        String name = method.getName();
        if ("save".equals(name)) {
            return save((Record) args[0]);
        }
        if ("findById".equals(name)) {
            return findOne(idCondition(args[0]));
        }
        if ("findAll".equals(name)) {
            return dsl.selectFrom(table).fetch();
        }
        if ("existsById".equals(name)) {
            return exists(idCondition(args[0]));
        }
        if ("deleteById".equals(name)) {
            return dsl.deleteFrom(table).where(idCondition(args[0])).execute();
        }
        if (name.startsWith("findBy")) {
            return findBy(method, args);
        }
        if (name.startsWith("existsBy")) {
            return existsBy(method, args);
        }

        throw new UnsupportedOperationException("Unsupported jOOQ repository method: " + method);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Record save(Record record) {
        Object idValue = record.get((Field) idField);
        if (idValue == null) {
            return dsl.insertInto((Table) table)
                    .set(record)
                    .returning()
                    .fetchOne();
        }

        return dsl.insertInto((Table) table)
                .set(record)
                .onConflict((Field) idField)
                .doUpdate()
                .set(record)
                .returning()
                .fetchOne();
    }

    private Optional<? extends Record> findOne(Condition condition) {
        return Optional.ofNullable(dsl.selectFrom(table).where(condition).fetchOne());
    }

    private Object findBy(Method method, Object[] args) {
        Condition condition = conditionFromMethod(method.getName().substring("findBy".length()), args);
        List<? extends Record> records = dsl.selectFrom(table).where(condition).fetch();

        // Keep derived queries intentionally small. Complex queries belong in hand-written jOOQ repositories.
        Class<?> returnType = method.getReturnType();
        if (Optional.class.equals(returnType)) {
            return records.stream().findFirst();
        }
        if (List.class.isAssignableFrom(returnType)) {
            return records;
        }
        if (Record.class.isAssignableFrom(returnType)) {
            return records.isEmpty() ? null : records.get(0);
        }
        throw new UnsupportedOperationException("Unsupported findBy return type: " + returnType.getName());
    }

    private Object existsBy(Method method, Object[] args) {
        Condition condition = conditionFromMethod(method.getName().substring("existsBy".length()), args);
        return exists(condition);
    }

    private boolean exists(Condition condition) {
        return dsl.fetchExists(dsl.selectOne().from(table).where(condition));
    }

    private Condition idCondition(Object value) {
        return condition(idField, value);
    }

    private Condition conditionFromMethod(String criteria, Object[] args) {
        String[] parts = criteria.split("And");
        Object[] safeArgs = args == null ? new Object[0] : args;
        if (parts.length != safeArgs.length) {
            throw new IllegalArgumentException("Method criteria count does not match argument count: " + criteria);
        }

        Condition condition = null;
        for (int i = 0; i < parts.length; i++) {
            Field<?> field = field(parts[i]);
            Condition next = condition(field, safeArgs[i]);
            condition = condition == null ? next : condition.and(next);
        }
        return condition;
    }

    private Field<?> field(String propertyName) {
        String columnName = toSnakeCase(propertyName);
        Field<?> field = table.field(columnName);
        if (field == null) {
            throw new IllegalArgumentException("No field '" + columnName + "' on table " + table.getName());
        }
        return field;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Condition condition(Field<?> field, Object value) {
        return ((Field) field).eq(value);
    }

    private String toSnakeCase(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                result.append('_');
            }
            result.append(Character.toLowerCase(c));
        }
        return result.toString();
    }
}
