package com.schooldays.service.attendance;

import java.time.DayOfWeek;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schooldays.dao.attendance.AttendanceDao;
import com.schooldays.dto.attendance.AttendanceCheckInRequest;
import com.schooldays.dto.attendance.AttendanceGridCellResponse;
import com.schooldays.dto.attendance.AttendanceGridDateResponse;
import com.schooldays.dto.attendance.AttendanceGridResponse;
import com.schooldays.dto.attendance.AttendanceGridStudentResponse;
import com.schooldays.dto.attendance.AttendanceListResponse;
import com.schooldays.dto.attendance.AttendanceResponse;
import com.schooldays.dto.classroom.ClassResponse;
import com.schooldays.jooq.generated.tables.records.AttendanceRecord;
import com.schooldays.jooq.generated.tables.records.ClassesRecord;
import org.jooq.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static com.schooldays.jooq.generated.tables.Attendance.ATTENDANCE;
import static com.schooldays.jooq.generated.tables.Children.CHILDREN;
import static com.schooldays.jooq.generated.tables.Users.USERS;

@Service
public class AttendanceService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter FRIENDLY_DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US);
    private static final String TYPE_WEEKLY = "weekly";

    private final AttendanceDao attendanceDao;
    private final Clock clock;

    @Autowired
    public AttendanceService(AttendanceDao attendanceDao) {
        this(attendanceDao, Clock.systemDefaultZone());
    }

    AttendanceService(AttendanceDao attendanceDao, Clock clock) {
        this.attendanceDao = attendanceDao;
        this.clock = clock;
    }

    @Transactional
    public AttendanceResponse parentCheckIn(UUID parentUserId, AttendanceCheckInRequest request) {
        attendanceDao.findParentChild(request.tenantId(), parentUserId, request.childId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Child does not belong to this parent"));
        ClassesRecord classRecord = attendanceDao.findActiveClass(request.tenantId(), request.classId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class was not found"));
        if (!attendanceDao.hasActiveEnrollment(request.tenantId(), request.childId(), request.classId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Child must be enrolled in this class before check-in");
        }
        validateParentCheckInWindow(request.classDate(), classZoneId(request.tenantId(), request.classId()));
        validateClassDate(classRecord, request.classDate());

        attendanceDao.checkIn(
                request.tenantId(),
                request.childId(),
                request.classId(),
                request.classDate(),
                parentUserId,
                "PARENT",
                OffsetDateTime.now()
        );
        return listParentChildAttendance(request.tenantId(), parentUserId, request.childId()).attendance().stream()
                .filter(entry -> entry.classId().equals(request.classId()) && entry.classDate().equals(request.classDate()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Attendance check-in could not be loaded"));
    }

    private void validateParentCheckInWindow(LocalDate classDate, ZoneId zoneId) {
        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        LocalDate earliest = today.minusDays(1);
        LocalDate latest = today.plusDays(1);
        if (classDate.isBefore(earliest) || classDate.isAfter(latest)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Parents can only check in for yesterday, today, or tomorrow (%s to %s)."
                            .formatted(formatShortDate(earliest), formatShortDate(latest))
            );
        }
    }

    private ZoneId classZoneId(UUID tenantId, UUID classId) {
        String timezone = attendanceDao.findClassTimezone(tenantId, classId)
                .filter(value -> !value.isBlank())
                .orElse(ZoneId.systemDefault().getId());
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException ignored) {
            return ZoneId.systemDefault();
        }
    }

    @Transactional(readOnly = true)
    public AttendanceListResponse listParentAttendance(UUID tenantId, UUID parentUserId) {
        List<AttendanceResponse> attendance = attendanceDao.listParentAttendance(tenantId, parentUserId).stream()
                .map(AttendanceResponse::from)
                .toList();
        return new AttendanceListResponse(attendance);
    }

    @Transactional(readOnly = true)
    public AttendanceListResponse listParentChildAttendance(UUID tenantId, UUID parentUserId, UUID childId) {
        attendanceDao.findParentChild(tenantId, parentUserId, childId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Child does not belong to this parent"));
        List<AttendanceResponse> attendance = attendanceDao.listParentChildAttendance(tenantId, parentUserId, childId).stream()
                .map(AttendanceResponse::from)
                .toList();
        return new AttendanceListResponse(attendance);
    }

    @Transactional(readOnly = true)
    public AttendanceListResponse listClassAttendance(UUID classId, LocalDate classDate) {
        if (attendanceDao.findActiveClassById(classId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Class was not found");
        }
        List<AttendanceResponse> attendance = attendanceDao.listClassAttendance(classId, classDate).stream()
                .map(AttendanceResponse::from)
                .toList();
        return new AttendanceListResponse(attendance);
    }

    @Transactional(readOnly = true)
    public AttendanceGridResponse getClassAttendanceGrid(UUID tenantId, UUID classId) {
        ClassesRecord classRecord = attendanceDao.findClass(tenantId, classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class was not found"));
        List<AttendanceGridDateResponse> dates = classDates(classRecord);
        Map<UUID, List<Record>> rosterByChildId = attendanceDao.listClassRoster(tenantId, classId).stream()
                .collect(Collectors.groupingBy(
                        record -> record.get(CHILDREN.ID),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<UUID, Map<LocalDate, AttendanceRecord>> attendanceByChildAndDate = attendanceDao.listClassAttendance(tenantId, classId).stream()
                .map(record -> record.into(ATTENDANCE))
                .collect(Collectors.groupingBy(
                        AttendanceRecord::getChildId,
                        LinkedHashMap::new,
                        Collectors.toMap(
                                AttendanceRecord::getClassDate,
                                record -> record,
                                (left, right) -> left.getCheckedInAt().isAfter(right.getCheckedInAt()) ? left : right,
                                LinkedHashMap::new
                        )
                ));

        List<AttendanceGridStudentResponse> students = rosterByChildId.values().stream()
                .map(records -> gridStudent(records.get(0), dates, attendanceByChildAndDate.getOrDefault(records.get(0).get(CHILDREN.ID), Map.of())))
                .toList();
        return new AttendanceGridResponse(ClassResponse.from(classRecord), dates, students);
    }

    private void validateClassDate(ClassesRecord classRecord, LocalDate classDate) {
        if (classDate.isBefore(classRecord.getStartDate()) || classDate.isAfter(classRecord.getEndDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "%s runs from %s to %s. Choose a date in that range."
                            .formatted(
                                    classRecord.getName(),
                                    formatShortDate(classRecord.getStartDate()),
                                    formatShortDate(classRecord.getEndDate())
                            )
            );
        }
        JsonNode metadata = classMetadata(classRecord);
        String classType = metadata.path("classType").asText("");
        List<String> weekdays = weekdays(metadata);
        if (TYPE_WEEKLY.equalsIgnoreCase(classType) && !weekdays.isEmpty()) {
            String requestedWeekday = classDate.getDayOfWeek().name();
            if (!weekdays.contains(requestedWeekday)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "%s does not meet on %s. Scheduled days are %s."
                                .formatted(
                                        classRecord.getName(),
                                        classDate.format(FRIENDLY_DATE_FORMAT),
                                        formatWeekdays(weekdays)
                                )
                );
            }
        }
    }

    private List<AttendanceGridDateResponse> classDates(ClassesRecord classRecord) {
        LocalDate cursor = classRecord.getStartDate();
        LocalDate end = classRecord.getEndDate();
        java.util.ArrayList<AttendanceGridDateResponse> dates = new java.util.ArrayList<>();
        while (!cursor.isAfter(end)) {
            dates.add(new AttendanceGridDateResponse(cursor, isScheduledClassDate(classRecord, cursor)));
            cursor = cursor.plusDays(1);
        }
        return dates;
    }

    private AttendanceGridStudentResponse gridStudent(
            Record rosterRecord,
            List<AttendanceGridDateResponse> dates,
            Map<LocalDate, AttendanceRecord> attendanceByDate
    ) {
        UUID childId = rosterRecord.get(CHILDREN.ID);
        List<AttendanceGridCellResponse> attendance = dates.stream()
                .map(date -> gridCell(date, attendanceByDate.get(date.classDate())))
                .toList();
        return new AttendanceGridStudentResponse(
                childId,
                childName(rosterRecord),
                rosterRecord.get(USERS.EMAIL),
                rosterRecord.get(USERS.PHONE),
                attendance
        );
    }

    private AttendanceGridCellResponse gridCell(AttendanceGridDateResponse date, AttendanceRecord attendance) {
        boolean checkedIn = attendance != null && "checked_in".equals(attendance.getStatus());
        return new AttendanceGridCellResponse(
                date.classDate(),
                date.scheduled(),
                checkedIn,
                attendance == null ? null : attendance.getCheckedInAt(),
                attendance == null ? (date.scheduled() ? "not_checked_in" : "no_class") : attendance.getStatus()
        );
    }

    private String childName(Record record) {
        String firstName = record.get(CHILDREN.FIRST_NAME);
        String lastName = record.get(CHILDREN.LAST_NAME);
        return String.join(" ",
                firstName == null ? "" : firstName,
                lastName == null ? "" : lastName
        ).trim();
    }

    private boolean isScheduledClassDate(ClassesRecord classRecord, LocalDate classDate) {
        JsonNode metadata = classMetadata(classRecord);
        String classType = metadata.path("classType").asText("");
        List<String> weekdays = weekdays(metadata);
        return !TYPE_WEEKLY.equalsIgnoreCase(classType)
                || weekdays.isEmpty()
                || weekdays.contains(classDate.getDayOfWeek().name());
    }

    private String formatShortDate(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US));
    }

    private String formatWeekdays(List<String> weekdays) {
        List<String> labels = weekdays.stream()
                .map(this::formatWeekday)
                .toList();
        if (labels.size() == 1) {
            return labels.get(0);
        }
        return String.join(", ", labels.subList(0, labels.size() - 1)) + " and " + labels.get(labels.size() - 1);
    }

    private String formatWeekday(String weekday) {
        try {
            return DayOfWeek.valueOf(weekday).getDisplayName(TextStyle.FULL, Locale.US);
        } catch (IllegalArgumentException ignored) {
            String normalized = weekday == null ? "" : weekday.trim().toLowerCase(Locale.US).replace('_', ' ');
            return normalized.isBlank()
                    ? "Scheduled day"
                    : normalized.substring(0, 1).toUpperCase(Locale.US) + normalized.substring(1);
        }
    }

    private JsonNode classMetadata(ClassesRecord classRecord) {
        try {
            return OBJECT_MAPPER.readTree(classRecord.getMetadata() == null ? "{}" : classRecord.getMetadata().data());
        } catch (Exception ignored) {
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
}
