package com.schooldays.service.externalcheckin;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;
import static com.schooldays.jooq.generated.tables.Classes.CLASSES;
import static com.schooldays.jooq.generated.tables.TeacherAssignments.TEACHER_ASSIGNMENTS;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.schooldays.dto.externalcheckin.ExternalCheckInListResponse;
import com.schooldays.dto.externalcheckin.ExternalCheckInDateCountResponse;
import com.schooldays.dto.externalcheckin.ExternalCheckInRequest;
import com.schooldays.dto.externalcheckin.ExternalCheckInResponse;
import com.schooldays.dto.externalcheckin.ExternalCheckInRowResponse;
import com.schooldays.service.cache.SchoolDataCacheService;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Table;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.schooldays.jooq.generated.tables.records.ClassesRecord;

@Service
public class ExternalCheckInService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Table<?> EXTERNAL_CHECK_INS = table(name("external_check_ins"));
    private static final Table<?> EXTERNAL_STUDENTS = table(name("external_students"));
    private static final Field<UUID> ID = field(name("id"), UUID.class);
    private static final Field<UUID> TENANT_ID = field(name("tenant_id"), UUID.class);
    private static final Field<String> EXTERNAL_STUDENT_ID = field(name("external_student_id"), String.class);
    private static final Field<UUID> CLASS_ID = field(name("class_id"), UUID.class);
    private static final Field<LocalDate> CHECK_DATE = field(name("check_date"), LocalDate.class);
    private static final Field<OffsetDateTime> CHECK_IN_TIME = field(name("check_in_time"), OffsetDateTime.class);
    private static final Field<UUID> CHECKED_IN_BY_USER_ID = field(name("checked_in_by_user_id"), UUID.class);
    private static final Field<String> CHECKED_IN_BY_ROLE = field(name("checked_in_by_role"), String.class);
    private static final Field<String> STATUS = field(name("status"), String.class);
    private static final Field<JSONB> METADATA = field(name("metadata"), JSONB.class);
    private static final Field<OffsetDateTime> CREATED_AT = field(name("created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = field(name("updated_at"), OffsetDateTime.class);
    private static final Field<Long> SEQ_ID = field(name("seq_id"), Long.class);
    private static final Field<String> CLASS_NAME = field(name("name"), String.class);
    private static final Field<UUID> EXTERNAL_STUDENTS_TENANT_ID = field(name("tenant_id"), UUID.class);
    private static final Field<String> EXTERNAL_STUDENTS_EXTERNAL_ID = field(name("external_id"), String.class);
    private static final Field<String> EXTERNAL_STUDENTS_NAME = field(name("student_name"), String.class);
    private static final Field<String> EXTERNAL_STUDENTS_GENDER = field(name("gender"), String.class);
    private static final Field<OffsetDateTime> EXTERNAL_STUDENTS_UPDATED_AT = field(name("updated_at"), OffsetDateTime.class);
    private static final Field<UUID> EXTERNAL_STUDENTS_ID = field(name("id"), UUID.class);

    private final DSLContext dsl;
    private final SchoolDataCacheService cacheService;

    @Autowired
    public ExternalCheckInService(DSLContext dsl, SchoolDataCacheService cacheService) {
        this.dsl = dsl;
        this.cacheService = cacheService;
    }

    ExternalCheckInService(DSLContext dsl) {
        this(dsl, new SchoolDataCacheService());
    }

    @Transactional
    public ExternalCheckInResponse recordCheckIn(
            UUID tenantId,
            UUID checkedInByUserId,
            String checkedInByRole,
            ExternalCheckInRequest request
    ) {
        if (!tenantId.equals(request.tenantId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant id in the request must match the URL");
        }

        ensureExternalStudentExists(tenantId, request.externalStudentId());
        ClassesRecord classRecord = ensureClassExists(tenantId, request.classId());
        ensureCheckInPermission(tenantId, request.classId(), checkedInByUserId, checkedInByRole);
        ensureScheduledClassDate(classRecord, request.checkDate());
        OffsetDateTime now = OffsetDateTime.now();
        JSONB metadata = metadataFor(request);

        int inserted = dsl.insertInto(EXTERNAL_CHECK_INS)
                .columns(
                        TENANT_ID,
                        EXTERNAL_STUDENT_ID,
                        CLASS_ID,
                        CHECK_DATE,
                        CHECK_IN_TIME,
                        CHECKED_IN_BY_USER_ID,
                        CHECKED_IN_BY_ROLE,
                        STATUS,
                        METADATA,
                        CREATED_AT,
                        UPDATED_AT
                )
                .values(
                        tenantId,
                        request.externalStudentId(),
                        request.classId(),
                        request.checkDate(),
                        now,
                        checkedInByUserId,
                        checkedInByRole,
                        "checked_in",
                        metadata,
                        now,
                        now
                )
                .onConflict(TENANT_ID, EXTERNAL_STUDENT_ID, CLASS_ID, CHECK_DATE)
                .doNothing()
                .execute();

        if (inserted == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This student has already checked in for the selected class and date");
        }
        cacheService.clearExternalCheckInCaches(tenantId);

        Record record = dsl.select(
                        ID,
                        SEQ_ID,
                        TENANT_ID,
                        EXTERNAL_STUDENT_ID,
                        CLASS_ID,
                        CHECK_DATE,
                        CHECK_IN_TIME,
                        CHECKED_IN_BY_USER_ID,
                        CHECKED_IN_BY_ROLE,
                        STATUS,
                        CREATED_AT,
                        UPDATED_AT
                )
                .from(EXTERNAL_CHECK_INS)
                .where(TENANT_ID.eq(tenantId))
                .and(EXTERNAL_STUDENT_ID.eq(request.externalStudentId()))
                .and(CLASS_ID.eq(request.classId()))
                .and(CHECK_DATE.eq(request.checkDate()))
                .fetchOne();
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "External check-in could not be saved");
        }

        return new ExternalCheckInResponse(
                record.get(ID),
                record.get(SEQ_ID),
                record.get(TENANT_ID),
                record.get(EXTERNAL_STUDENT_ID),
                record.get(CLASS_ID),
                classRecord.getName(),
                record.get(CHECK_DATE),
                record.get(CHECK_IN_TIME),
                record.get(CHECKED_IN_BY_USER_ID),
                record.get(CHECKED_IN_BY_ROLE),
                record.get(STATUS),
                record.get(CREATED_AT),
                record.get(UPDATED_AT)
        );
    }

    public ExternalCheckInListResponse listCheckIns(
            UUID tenantId,
            UUID classId,
            LocalDate checkDate,
            UUID checkedInByUserId,
            String checkedInByRole
    ) {
        if (checkDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "checkDate is required");
        }
        return cacheService.getExternalCheckInList(
                tenantId,
                classId,
                checkDate,
                checkedInByUserId,
                checkedInByRole,
                () -> {
                    ClassesRecord classRecord = ensureClassExists(tenantId, classId);
                    ensureCheckInPermission(tenantId, classId, checkedInByUserId, checkedInByRole);
                    ensureScheduledClassDate(classRecord, checkDate);
                    return fetchCheckIns(tenantId, classId, checkDate);
                }
        );
    }

    private ExternalCheckInListResponse fetchCheckIns(UUID tenantId, UUID classId, LocalDate checkDate) {
        var e = table(name("external_check_ins")).as("e");
        var s = table(name("external_students")).as("s");
        var c = table(name("classes")).as("c");

        var rows = dsl.select(
                        field(name("e", "id"), UUID.class),
                        field(name("e", "seq_id"), Long.class),
                        field(name("e", "external_student_id"), String.class),
                        field(name("e", "class_id"), UUID.class),
                        field(name("e", "check_date"), LocalDate.class),
                        field(name("e", "check_in_time"), OffsetDateTime.class),
                        field(name("e", "checked_in_by_user_id"), UUID.class),
                        field(name("e", "checked_in_by_role"), String.class),
                        field(name("e", "status"), String.class),
                        field(name("e", "metadata"), JSONB.class),
                        field(name("e", "created_at"), OffsetDateTime.class),
                        field(name("e", "updated_at"), OffsetDateTime.class),
                        field(name("s", "student_name"), String.class),
                        field(name("s", "gender"), String.class),
                        field(name("c", "name"), String.class)
                )
                .from(e)
                .join(s)
                .on(field(name("s", "tenant_id"), UUID.class).eq(field(name("e", "tenant_id"), UUID.class)))
                .and(field(name("s", "external_id"), String.class).eq(field(name("e", "external_student_id"), String.class)))
                .join(c)
                .on(field(name("c", "id"), UUID.class).eq(field(name("e", "class_id"), UUID.class)))
                .where(field(name("e", "tenant_id"), UUID.class).eq(tenantId))
                .and(field(name("e", "class_id"), UUID.class).eq(classId))
                .and(field(name("e", "check_date"), LocalDate.class).eq(checkDate))
                .orderBy(field(name("e", "check_in_time"), OffsetDateTime.class).desc(), field(name("e", "seq_id"), Long.class).desc())
                .fetch(record -> {
                    JSONB metadata = record.get(field(name("e", "metadata"), JSONB.class));
                    JsonNode metadataNode = readMetadata(metadata);
                    return new ExternalCheckInRowResponse(
                            record.get(field(name("e", "id"), UUID.class)),
                            record.get(field(name("e", "seq_id"), Long.class)),
                            record.get(field(name("e", "external_student_id"), String.class)),
                            record.get(field(name("s", "student_name"), String.class)),
                            record.get(field(name("s", "gender"), String.class)),
                            record.get(field(name("e", "class_id"), UUID.class)),
                            record.get(field(name("c", "name"), String.class)),
                            record.get(field(name("e", "check_date"), LocalDate.class)),
                            record.get(field(name("e", "check_in_time"), OffsetDateTime.class)),
                            record.get(field(name("e", "checked_in_by_user_id"), UUID.class)),
                            record.get(field(name("e", "checked_in_by_role"), String.class)),
                            record.get(field(name("e", "status"), String.class)),
                            metadataText(metadataNode, "barcodeValue"),
                            record.get(field(name("e", "created_at"), OffsetDateTime.class)),
                            record.get(field(name("e", "updated_at"), OffsetDateTime.class))
                    );
                });

        return new ExternalCheckInListResponse(rows);
    }

    public List<ExternalCheckInDateCountResponse> listCheckInCounts(
            UUID tenantId,
            UUID classId,
            LocalDate startDate,
            LocalDate endDate,
            UUID checkedInByUserId,
            String checkedInByRole
    ) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate and endDate are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must be on or after startDate");
        }
        return cacheService.getExternalCheckInCounts(
                tenantId,
                classId,
                startDate,
                endDate,
                checkedInByUserId,
                checkedInByRole,
                () -> {
                    ClassesRecord classRecord = ensureClassExists(tenantId, classId);
                    ensureCheckInPermission(tenantId, classId, checkedInByUserId, checkedInByRole);
                    ensureDateRangeWithinClass(classRecord, startDate, endDate);
                    return fetchCheckInCounts(tenantId, classId, startDate, endDate);
                }
        );
    }

    private List<ExternalCheckInDateCountResponse> fetchCheckInCounts(
            UUID tenantId,
            UUID classId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return dsl.select(CHECK_DATE, count().as("check_in_count"))
                .from(EXTERNAL_CHECK_INS)
                .where(TENANT_ID.eq(tenantId))
                .and(CLASS_ID.eq(classId))
                .and(CHECK_DATE.between(startDate, endDate))
                .groupBy(CHECK_DATE)
                .orderBy(CHECK_DATE.asc())
                .fetch(record -> {
                    Number countValue = record.getValue(1, Number.class);
                    return new ExternalCheckInDateCountResponse(
                            record.get(CHECK_DATE),
                            countValue == null ? 0L : countValue.longValue()
                    );
                });
    }

    private void ensureExternalStudentExists(UUID tenantId, String externalStudentId) {
        boolean exists = dsl.fetchExists(dsl.selectOne()
                .from(EXTERNAL_STUDENTS)
                .where(EXTERNAL_STUDENTS_TENANT_ID.eq(tenantId))
                .and(EXTERNAL_STUDENTS_EXTERNAL_ID.eq(externalStudentId)));
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "External student was not found");
        }
    }

    private ClassesRecord ensureClassExists(UUID tenantId, UUID classId) {
        ClassesRecord classRecord = dsl.selectFrom(CLASSES)
                .where(CLASSES.TENANT_ID.eq(tenantId))
                .and(CLASSES.ID.eq(classId))
                .fetchOneInto(ClassesRecord.class);
        if (classRecord == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Class was not found");
        }
        return classRecord;
    }

    private void ensureCheckInPermission(UUID tenantId, UUID classId, UUID checkedInByUserId, String checkedInByRole) {
        if ("SCHOOL_ADMIN".equals(checkedInByRole)) {
            return;
        }
        if (!"TEACHER".equals(checkedInByRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Check-in is not allowed for this user");
        }
        boolean assigned = dsl.fetchExists(dsl.selectOne()
                .from(TEACHER_ASSIGNMENTS)
                .join(CLASSES).on(CLASSES.ID.eq(TEACHER_ASSIGNMENTS.CLASS_ID))
                .where(CLASSES.TENANT_ID.eq(tenantId))
                .and(CLASSES.ID.eq(classId))
                .and(TEACHER_ASSIGNMENTS.TEACHER_USER_ID.eq(checkedInByUserId)));
        if (!assigned) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Teachers can only check in for their assigned classes");
        }
    }

    private void ensureScheduledClassDate(ClassesRecord classRecord, LocalDate checkDate) {
        if (classRecord.getStartDate() != null && checkDate.isBefore(classRecord.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This class does not meet on the selected date");
        }
        if (classRecord.getEndDate() != null && checkDate.isAfter(classRecord.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This class does not meet on the selected date");
        }
        JsonNode metadata = classMetadata(classRecord);
        String classType = metadata.path("classType").asText("");
        List<String> weekdays = weekdays(metadata);
        boolean scheduled = !"weekly".equalsIgnoreCase(classType)
                || weekdays.isEmpty()
                || weekdays.contains(checkDate.getDayOfWeek().name());
        if (!scheduled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This class does not meet on the selected date");
        }
    }

    private void ensureDateRangeWithinClass(ClassesRecord classRecord, LocalDate startDate, LocalDate endDate) {
        if (classRecord.getStartDate() != null && startDate.isBefore(classRecord.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested date range is outside the class schedule");
        }
        if (classRecord.getEndDate() != null && endDate.isAfter(classRecord.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested date range is outside the class schedule");
        }
    }

    private JSONB metadataFor(ExternalCheckInRequest request) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        put(root, "studentName", request.studentName());
        put(root, "gender", request.gender());
        put(root, "barcodeValue", request.barcodeValue());
        return JSONB.valueOf(root.toString());
    }

    private void put(ObjectNode root, String field, String value) {
        if (value != null && !value.isBlank()) {
            root.put(field, value);
        }
    }

    private JsonNode readMetadata(JSONB metadata) {
        try {
            return OBJECT_MAPPER.readTree(metadata == null ? "{}" : metadata.data());
        } catch (Exception exception) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private JsonNode classMetadata(ClassesRecord classRecord) {
        try {
            return OBJECT_MAPPER.readTree(classRecord.getMetadata() == null ? "{}" : classRecord.getMetadata().data());
        } catch (Exception exception) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private List<String> weekdays(JsonNode metadata) {
        JsonNode weekdays = metadata.path("weekdays");
        if (!weekdays.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(weekdays.spliterator(), false)
                .map(JsonNode::asText)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase())
                .toList();
    }

    private String metadataText(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return "";
        }
        return node.get(field).asText("");
    }
}
