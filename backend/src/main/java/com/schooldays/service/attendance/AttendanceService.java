package com.schooldays.service.attendance;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.schooldays.dao.attendance.AttendanceDao;
import com.schooldays.dto.attendance.AttendanceCheckInRequest;
import com.schooldays.dto.attendance.AttendanceListResponse;
import com.schooldays.dto.attendance.AttendanceResponse;
import com.schooldays.jooq.generated.tables.records.ClassesRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttendanceService {

    private final AttendanceDao attendanceDao;

    public AttendanceService(AttendanceDao attendanceDao) {
        this.attendanceDao = attendanceDao;
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

    private void validateClassDate(ClassesRecord classRecord, LocalDate classDate) {
        if (classDate.isBefore(classRecord.getStartDate()) || classDate.isAfter(classRecord.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Class date must be within the class date range");
        }
    }
}
