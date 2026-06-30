package com.schooldays.dao.child;

import java.util.List;
import java.util.UUID;

import com.schooldays.jooq.JooqRepository;
import com.schooldays.jooq.JooqRepositoryBean;
import com.schooldays.jooq.generated.tables.Children;
import com.schooldays.jooq.generated.tables.records.ChildrenRecord;

@JooqRepositoryBean(tableClass = Children.class)
public interface ChildRepository extends JooqRepository<ChildrenRecord, UUID> {

    List<ChildrenRecord> findByTenantIdAndParentUserId(UUID tenantId, UUID parentUserId);
}
