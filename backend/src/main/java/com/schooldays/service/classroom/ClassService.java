package com.schooldays.service.classroom;

import static com.schooldays.jooq.generated.tables.Programs.PROGRAMS;
import static com.schooldays.jooq.generated.tables.Classes.CLASSES;
import static com.schooldays.jooq.generated.tables.TeacherAssignments.TEACHER_ASSIGNMENTS;
import static com.schooldays.jooq.generated.tables.SchoolSites.SCHOOL_SITES;
import static com.schooldays.jooq.generated.tables.Tenants.TENANTS;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.schooldays.dao.classroom.ClassDao;
import com.schooldays.dto.classroom.ClassListResponse;
import com.schooldays.dto.classroom.ClassResponse;
import com.schooldays.dto.classroom.CreateClassRequest;
import com.schooldays.dto.classroom.UpdateClassRequest;
import com.schooldays.jooq.generated.tables.records.ClassesRecord;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClassService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TYPE_WEEKLY = "weekly";
    private static final String TYPE_TIME_RANGE = "time_range";

    private final DSLContext dsl;
    private final ClassDao classDao;

    public ClassService(DSLContext dsl, ClassDao classDao) {
        this.dsl = dsl;
        this.classDao = classDao;
    }

    public ClassListResponse listClasses(UUID tenantId, UUID siteId) {
        requireTenant(tenantId);
        requireSite(tenantId, siteId);
        List<ClassResponse> classes = classDao.findByTenantAndSite(tenantId, siteId)
                .stream()
                .map(ClassResponse::from)
                .toList();
        return new ClassListResponse(classes);
    }

    public ClassListResponse listAvailableClasses(UUID tenantId) {
        requireTenant(tenantId);
        List<ClassResponse> classes = classDao.findActiveByTenant(tenantId)
                .stream()
                .map(ClassResponse::from)
                .toList();
        return new ClassListResponse(classes);
    }

    public ClassListResponse listTeacherClasses(UUID tenantId, UUID teacherUserId) {
        requireTenant(tenantId);
        List<ClassResponse> classes = dsl.select(CLASSES.fields())
                .from(CLASSES)
                .join(TEACHER_ASSIGNMENTS).on(TEACHER_ASSIGNMENTS.CLASS_ID.eq(CLASSES.ID))
                .join(PROGRAMS).on(PROGRAMS.ID.eq(CLASSES.PROGRAM_ID))
                .join(SCHOOL_SITES).on(SCHOOL_SITES.ID.eq(PROGRAMS.SITE_ID))
                .where(CLASSES.TENANT_ID.eq(tenantId))
                .and(TEACHER_ASSIGNMENTS.TEACHER_USER_ID.eq(teacherUserId))
                .and(CLASSES.STATUS.eq("active"))
                .and(PROGRAMS.STATUS.eq("active"))
                .and(SCHOOL_SITES.STATUS.eq("active"))
                .orderBy(PROGRAMS.SITE_ID.asc(), CLASSES.START_DATE.asc(), CLASSES.SEQ_ID.asc())
                .fetchInto(CLASSES)
                .stream()
                .map(ClassResponse::from)
                .toList();
        return new ClassListResponse(classes);
    }

    public ClassResponse createClass(UUID tenantId, CreateClassRequest request) {
        requireTenant(tenantId);
        requireProgram(tenantId, request.programId());
        validateDates(request.startDate(), request.endDate());
        validateSchedule(request.classType(), request.weekdays(), request.startTime(), request.endTime());

        OffsetDateTime now = OffsetDateTime.now();
        ClassesRecord saved = classDao.save(new ClassesRecord()
                .setTenantId(tenantId)
                .setProgramId(request.programId())
                .setName(request.name().trim())
                .setCapacity(request.capacity())
                .setStartDate(request.startDate())
                .setEndDate(request.endDate())
                .setStatus("active")
                .setMetadata(metadataFor(
                        request.description(),
                        normalizeClassType(request.classType()),
                        normalizeWeekdays(request.weekdays()),
                        request.startTime(),
                        request.endTime()
                ))
                .setCreatedAt(now)
                .setUpdatedAt(now));
        return ClassResponse.from(saved);
    }

    public ClassResponse updateClass(UUID tenantId, UUID classId, UpdateClassRequest request) {
        requireTenant(tenantId);
        ClassesRecord record = classDao.findByTenantAndId(tenantId, classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class was not found"));

        UUID programId = request.programId() == null ? record.getProgramId() : request.programId();
        requireProgram(tenantId, programId);
        ClassResponse existing = ClassResponse.from(record);
        LocalDate startDate = request.startDate() == null ? record.getStartDate() : request.startDate();
        LocalDate endDate = request.endDate() == null ? record.getEndDate() : request.endDate();
        String classType = request.classType() == null ? existing.classType() : request.classType();
        List<String> weekdays = request.weekdays() == null ? existing.weekdays() : request.weekdays();
        LocalTime startTime = request.startTime() == null ? existing.startTime() : request.startTime();
        LocalTime endTime = request.endTime() == null ? existing.endTime() : request.endTime();
        validateDates(startDate, endDate);
        validateSchedule(classType, weekdays, startTime, endTime);

        if (request.name() != null) {
            record.setName(request.name().trim());
        }
        record.setProgramId(programId);
        record.setCapacity(request.capacity() == null ? record.getCapacity() : request.capacity());
        record.setStartDate(startDate);
        record.setEndDate(endDate);
        record.setMetadata(metadataFor(
                request.description() == null ? existing.description() : request.description(),
                normalizeClassType(classType),
                normalizeWeekdays(weekdays),
                startTime,
                endTime
        ));
        record.setUpdatedAt(OffsetDateTime.now());
        record.changed(true);
        return ClassResponse.from(classDao.save(record));
    }

    public ClassResponse closeEnrollment(UUID tenantId, UUID classId) {
        ClassesRecord record = classDao.findByTenantAndId(tenantId, classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class was not found"));
        record.setRegistrationClosesAt(OffsetDateTime.now());
        record.setUpdatedAt(OffsetDateTime.now());
        record.changed(true);
        return ClassResponse.from(classDao.save(record));
    }

    public ClassResponse stopClass(UUID tenantId, UUID classId) {
        ClassesRecord record = classDao.findByTenantAndId(tenantId, classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class was not found"));
        record.setStatus("stopped");
        record.setRegistrationClosesAt(OffsetDateTime.now());
        record.setUpdatedAt(OffsetDateTime.now());
        record.changed(true);
        return ClassResponse.from(classDao.save(record));
    }

    private void requireTenant(UUID tenantId) {
        boolean exists = dsl.fetchExists(dsl.selectOne()
                .from(TENANTS)
                .where(TENANTS.ID.eq(tenantId)));
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant was not found");
        }
    }

    private void requireSite(UUID tenantId, UUID siteId) {
        boolean exists = dsl.fetchExists(dsl.selectOne()
                .from(SCHOOL_SITES)
                .where(SCHOOL_SITES.ID.eq(siteId))
                .and(SCHOOL_SITES.TENANT_ID.eq(tenantId)));
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Site was not found");
        }
    }

    private void requireProgram(UUID tenantId, UUID programId) {
        boolean exists = dsl.fetchExists(dsl.selectOne()
                .from(PROGRAMS)
                .where(PROGRAMS.ID.eq(programId))
                .and(PROGRAMS.TENANT_ID.eq(tenantId)));
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Program was not found");
        }
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Class start date and end date are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Class end date cannot be before start date");
        }
    }

    private void validateSchedule(String classType, List<String> weekdays, LocalTime startTime, LocalTime endTime) {
        String normalizedType = normalizeClassType(classType);
        if (!TYPE_WEEKLY.equals(normalizedType) && !TYPE_TIME_RANGE.equals(normalizedType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Class type must be weekly or time_range");
        }
        if (startTime == null || endTime == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Class start time and end time are required");
        }
        if (!endTime.isAfter(startTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Class end time must be after start time");
        }
        if (TYPE_WEEKLY.equals(normalizedType) && normalizeWeekdays(weekdays).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Weekly classes require at least one weekday");
        }
    }

    private String normalizeClassType(String classType) {
        if (classType == null || classType.isBlank()) {
            return TYPE_TIME_RANGE;
        }
        return classType.trim().toLowerCase().replace("-", "_");
    }

    private List<String> normalizeWeekdays(List<String> weekdays) {
        if (weekdays == null) {
            return List.of();
        }
        return weekdays.stream()
                .filter(day -> day != null && !day.isBlank())
                .map(day -> day.trim().toUpperCase())
                .distinct()
                .toList();
    }

    private JSONB metadataFor(
            String description,
            String classType,
            List<String> weekdays,
            LocalTime startTime,
            LocalTime endTime
    ) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        if (description != null) {
            root.put("description", description.trim());
        }
        root.put("classType", classType);
        var weekdaysNode = root.putArray("weekdays");
        weekdays.forEach(weekdaysNode::add);
        root.put("startTime", startTime.toString());
        root.put("endTime", endTime.toString());
        return JSONB.valueOf(root.toString());
    }
}
