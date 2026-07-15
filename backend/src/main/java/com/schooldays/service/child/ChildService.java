package com.schooldays.service.child;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.schooldays.dao.child.ChildDao;
import com.schooldays.dto.child.ChildListResponse;
import com.schooldays.dto.child.ChildRequest;
import com.schooldays.dto.child.ChildResponse;
import com.schooldays.jooq.generated.tables.records.ChildrenRecord;
import com.schooldays.service.cache.SchoolDataCacheService;
import org.jooq.JSONB;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChildService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChildDao childDao;
    private final SchoolDataCacheService cacheService;

    @Autowired
    public ChildService(ChildDao childDao, SchoolDataCacheService cacheService) {
        this.childDao = childDao;
        this.cacheService = cacheService;
    }

    ChildService(ChildDao childDao) {
        this(childDao, new SchoolDataCacheService());
    }

    @Transactional(readOnly = true)
    public ChildListResponse listChildren(UUID tenantId, UUID parentUserId) {
        List<ChildResponse> children = childDao.listChildren(tenantId, parentUserId).stream()
                .map(ChildResponse::fromRecord)
                .toList();
        return new ChildListResponse(children);
    }

    @Transactional
    public ChildResponse createChild(UUID parentUserId, ChildRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        ChildrenRecord record = new ChildrenRecord()
                .setTenantId(request.tenantId())
                .setParentUserId(parentUserId)
                .setFirstName(request.firstName())
                .setLastName(request.lastName())
                .setDateOfBirth(request.dateOfBirth())
                .setStatus("active")
                .setMetadata(JSONB.valueOf(metadataJson(request)))
                .setUpdatedAt(now);
        return ChildResponse.fromRecord(childDao.save(record, now));
    }

    @Transactional
    public ChildResponse updateChild(UUID parentUserId, UUID childId, ChildRequest request) {
        ChildrenRecord record = childDao.findById(childId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Child was not found"));
        if (!record.getParentUserId().equals(parentUserId) || !record.getTenantId().equals(request.tenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Child does not belong to this parent");
        }
        record.setFirstName(request.firstName());
        record.setLastName(request.lastName());
        record.setDateOfBirth(request.dateOfBirth());
        record.setMetadata(JSONB.valueOf(metadataJson(request)));
        ChildrenRecord saved = childDao.save(record, OffsetDateTime.now());
        cacheService.clearAttendanceCaches(record.getTenantId());
        return ChildResponse.fromRecord(saved);
    }

    private String metadataJson(ChildRequest request) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        put(root, "gender", request.gender());
        put(root, "grade", request.grade());
        put(root, "school", request.school());
        put(root, "note", request.note());
        ArrayNode race = root.putArray("race");
        if (request.race() != null) {
            request.race().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .forEach(race::add);
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize child metadata", exception);
        }
    }

    private void put(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value.trim());
        }
    }
}
