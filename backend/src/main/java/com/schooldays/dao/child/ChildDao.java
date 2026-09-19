package com.schooldays.dao.child;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.schooldays.jooq.generated.tables.Children.CHILDREN;

import com.schooldays.jooq.generated.tables.records.ChildrenRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class ChildDao {

    private final ChildRepository childRepository;
    private final DSLContext dsl;

    public ChildDao(ChildRepository childRepository, DSLContext dsl) {
        this.childRepository = childRepository;
        this.dsl = dsl;
    }

    public List<ChildrenRecord> listChildren(UUID tenantId, UUID parentUserId) {
        return childRepository.findByTenantIdAndParentUserId(tenantId, parentUserId);
    }

    public Optional<ChildrenRecord> findById(UUID childId) {
        return childRepository.findById(childId);
    }

    public ChildrenRecord save(ChildrenRecord record, OffsetDateTime now) {
        record.setUpdatedAt(now);
        return childRepository.save(record);
    }

    public ChildrenRecord update(ChildrenRecord record, OffsetDateTime now) {
        return dsl.update(CHILDREN)
                .set(CHILDREN.FIRST_NAME, record.getFirstName())
                .set(CHILDREN.LAST_NAME, record.getLastName())
                .set(CHILDREN.DATE_OF_BIRTH, record.getDateOfBirth())
                .set(CHILDREN.METADATA, record.getMetadata())
                .set(CHILDREN.UPDATED_AT, now)
                .where(CHILDREN.ID.eq(record.getId()))
                .and(CHILDREN.TENANT_ID.eq(record.getTenantId()))
                .and(CHILDREN.PARENT_USER_ID.eq(record.getParentUserId()))
                .returning()
                .fetchOne();
    }
}
