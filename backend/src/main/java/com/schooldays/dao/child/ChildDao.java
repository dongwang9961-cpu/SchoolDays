package com.schooldays.dao.child;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.schooldays.jooq.generated.tables.records.ChildrenRecord;
import org.springframework.stereotype.Repository;

@Repository
public class ChildDao {

    private final ChildRepository childRepository;

    public ChildDao(ChildRepository childRepository) {
        this.childRepository = childRepository;
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
}
