package com.schooldays.service.student;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.schooldays.dao.student.StudentRosterDao;
import com.schooldays.dto.student.StudentRosterResponse;
import com.schooldays.dto.student.StudentRosterRowResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentRosterService {

    private final StudentRosterDao studentRosterDao;

    public StudentRosterService(StudentRosterDao studentRosterDao) {
        this.studentRosterDao = studentRosterDao;
    }

    public StudentRosterResponse listActiveClassStudents(UUID tenantId, UUID classId) {
        return listActiveClassStudents(tenantId, classId, null);
    }

    public StudentRosterResponse listActiveClassStudents(UUID tenantId, UUID classId, UUID siteId) {
        return listClassStudents(tenantId, classId, siteId, "active", null);
    }

    public StudentRosterResponse listClassStudents(
            UUID tenantId, UUID classId, UUID siteId, String group, Integer year) {
        return listClassStudents(tenantId, classId, siteId, group, year, false);
    }

    public StudentRosterResponse listClassStudents(
            UUID tenantId, UUID classId, UUID siteId, String group, Integer year, boolean detailed) {
        if (!"active".equalsIgnoreCase(group) && !"current".equalsIgnoreCase(group) && !"history".equalsIgnoreCase(group)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Student group must be active, current, or history");
        }
        if ("history".equalsIgnoreCase(group) && year == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "History student group requires a year");
        }
        if (siteId != null && !studentRosterDao.siteBelongsToTenant(tenantId, siteId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Site was not found");
        }
        if (classId != null && !studentRosterDao.classBelongsToTenant(tenantId, classId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Class was not found");
        }
        if (classId != null && siteId != null && !studentRosterDao.classBelongsToSite(tenantId, classId, siteId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Class does not belong to the selected site");
        }
        List<StudentRosterRowResponse> students = studentRosterDao.listClassStudents(tenantId, classId, siteId, group, year)
                .stream()
                .map(StudentRosterRowResponse::from)
                .toList();
        if (classId == null && !detailed) {
            students = summarizeByStudent(students);
        }
        return new StudentRosterResponse(students);
    }

    private List<StudentRosterRowResponse> summarizeByStudent(List<StudentRosterRowResponse> students) {
        Map<UUID, List<StudentRosterRowResponse>> rowsByStudent = new LinkedHashMap<>();
        students.forEach(student -> rowsByStudent
                .computeIfAbsent(student.childId(), ignored -> new ArrayList<>())
                .add(student));
        return rowsByStudent.values().stream()
                .map(this::summarizeStudent)
                .toList();
    }

    private StudentRosterRowResponse summarizeStudent(List<StudentRosterRowResponse> rows) {
        StudentRosterRowResponse first = rows.get(0);
        LinkedHashMap<UUID, String> classNamesById = new LinkedHashMap<>();
        rows.forEach(row -> {
            if (row.classId() != null) {
                classNamesById.putIfAbsent(row.classId(), row.className());
            }
        });
        int classCount = classNamesById.size();
        String className = classNamesById.values().stream()
                .filter(Objects::nonNull)
                .filter(name -> !name.isBlank())
                .reduce((left, right) -> left + ", " + right)
                .orElse(first.className());
        String enrollmentStatus = rows.stream()
                .map(StudentRosterRowResponse::enrollmentStatus)
                .filter(Objects::nonNull)
                .distinct()
                .reduce((left, right) -> "multiple")
                .orElse(first.enrollmentStatus());
        OffsetDateTime enrolledAt = rows.stream()
                .map(StudentRosterRowResponse::enrolledAt)
                .filter(Objects::nonNull)
                .max(OffsetDateTime::compareTo)
                .orElse(first.enrolledAt());

        return new StudentRosterRowResponse(
                rows.size() == 1 ? first.enrollmentId() : null,
                first.childId(),
                first.childName(),
                first.firstName(),
                first.lastName(),
                first.dateOfBirth(),
                classCount == 1 ? first.classId() : null,
                className,
                first.classStatus(),
                enrollmentStatus,
                first.parentUserId(),
                first.parentEmail(),
                first.parentPhone(),
                enrolledAt,
                classCount
        );
    }
}
