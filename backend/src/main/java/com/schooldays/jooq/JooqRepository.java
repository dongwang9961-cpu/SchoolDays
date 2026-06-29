package com.schooldays.jooq;

import java.util.List;
import java.util.Optional;

import org.jooq.Record;

public interface JooqRepository<R extends Record, ID> {

    R save(R record);

    Optional<R> findById(ID id);

    List<R> findAll();

    boolean existsById(ID id);

    int deleteById(ID id);
}
