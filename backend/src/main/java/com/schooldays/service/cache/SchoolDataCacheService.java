package com.schooldays.service.cache;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.schooldays.dto.attendance.AttendanceGridResponse;
import com.schooldays.dto.attendance.AttendanceListResponse;
import com.schooldays.dto.classroom.ClassListResponse;
import com.schooldays.dto.externalcheckin.ExternalCheckInDateCountResponse;
import com.schooldays.dto.externalcheckin.ExternalCheckInListResponse;
import org.springframework.stereotype.Service;

@Service
public class SchoolDataCacheService {

    private final Map<ClassListCacheKey, ClassListResponse> classLists = new ConcurrentHashMap<>();
    private final Map<AvailableClassListCacheKey, ClassListResponse> availableClassLists = new ConcurrentHashMap<>();
    private final Map<TeacherClassListCacheKey, ClassListResponse> teacherClassLists = new ConcurrentHashMap<>();

    private final Map<ParentAttendanceCacheKey, AttendanceListResponse> parentAttendance = new ConcurrentHashMap<>();
    private final Map<ParentChildAttendanceCacheKey, AttendanceListResponse> parentChildAttendance = new ConcurrentHashMap<>();
    private final Map<ClassAttendanceCacheKey, AttendanceListResponse> classAttendance = new ConcurrentHashMap<>();
    private final Map<ClassAttendanceGridCacheKey, AttendanceGridResponse> classAttendanceGrids = new ConcurrentHashMap<>();

    private final Map<ExternalCheckInListCacheKey, ExternalCheckInListResponse> externalCheckInLists = new ConcurrentHashMap<>();
    private final Map<ExternalCheckInCountCacheKey, List<ExternalCheckInDateCountResponse>> externalCheckInCounts = new ConcurrentHashMap<>();

    public ClassListResponse getClassList(UUID tenantId, UUID siteId, Supplier<ClassListResponse> loader) {
        return classLists.computeIfAbsent(new ClassListCacheKey(tenantId, siteId), ignored -> loader.get());
    }

    public ClassListResponse getAvailableClassList(UUID tenantId, Supplier<ClassListResponse> loader) {
        return availableClassLists.computeIfAbsent(new AvailableClassListCacheKey(tenantId), ignored -> loader.get());
    }

    public ClassListResponse getTeacherClassList(UUID tenantId, UUID teacherUserId, Supplier<ClassListResponse> loader) {
        return teacherClassLists.computeIfAbsent(new TeacherClassListCacheKey(tenantId, teacherUserId), ignored -> loader.get());
    }

    public AttendanceListResponse getParentAttendance(UUID tenantId, UUID parentUserId, Supplier<AttendanceListResponse> loader) {
        return parentAttendance.computeIfAbsent(new ParentAttendanceCacheKey(tenantId, parentUserId), ignored -> loader.get());
    }

    public AttendanceListResponse getParentChildAttendance(
            UUID tenantId,
            UUID parentUserId,
            UUID childId,
            Supplier<AttendanceListResponse> loader
    ) {
        return parentChildAttendance.computeIfAbsent(
                new ParentChildAttendanceCacheKey(tenantId, parentUserId, childId),
                ignored -> loader.get()
        );
    }

    public AttendanceListResponse getClassAttendance(UUID classId, LocalDate classDate, Supplier<AttendanceListResponse> loader) {
        return classAttendance.computeIfAbsent(new ClassAttendanceCacheKey(classId, classDate), ignored -> loader.get());
    }

    public AttendanceGridResponse getClassAttendanceGrid(UUID tenantId, UUID classId, Supplier<AttendanceGridResponse> loader) {
        return classAttendanceGrids.computeIfAbsent(new ClassAttendanceGridCacheKey(tenantId, classId), ignored -> loader.get());
    }

    public ExternalCheckInListResponse getExternalCheckInList(
            UUID tenantId,
            UUID classId,
            LocalDate checkDate,
            UUID checkedInByUserId,
            String checkedInByRole,
            Supplier<ExternalCheckInListResponse> loader
    ) {
        return externalCheckInLists.computeIfAbsent(
                new ExternalCheckInListCacheKey(tenantId, classId, checkDate, checkedInByUserId, checkedInByRole),
                ignored -> loader.get()
        );
    }

    public List<ExternalCheckInDateCountResponse> getExternalCheckInCounts(
            UUID tenantId,
            UUID classId,
            LocalDate startDate,
            LocalDate endDate,
            UUID checkedInByUserId,
            String checkedInByRole,
            Supplier<List<ExternalCheckInDateCountResponse>> loader
    ) {
        return externalCheckInCounts.computeIfAbsent(
                new ExternalCheckInCountCacheKey(tenantId, classId, startDate, endDate, checkedInByUserId, checkedInByRole),
                ignored -> List.copyOf(loader.get())
        );
    }

    public void clearClassCaches(UUID tenantId) {
        classLists.keySet().removeIf(key -> key.tenantId().equals(tenantId));
        availableClassLists.keySet().removeIf(key -> key.tenantId().equals(tenantId));
        teacherClassLists.keySet().removeIf(key -> key.tenantId().equals(tenantId));
    }

    public void clearAttendanceCaches(UUID tenantId) {
        parentAttendance.keySet().removeIf(key -> key.tenantId().equals(tenantId));
        parentChildAttendance.keySet().removeIf(key -> key.tenantId().equals(tenantId));
        classAttendance.clear();
        classAttendanceGrids.keySet().removeIf(key -> key.tenantId().equals(tenantId));
    }

    public void clearExternalCheckInCaches(UUID tenantId) {
        externalCheckInLists.keySet().removeIf(key -> key.tenantId().equals(tenantId));
        externalCheckInCounts.keySet().removeIf(key -> key.tenantId().equals(tenantId));
    }

    public void clearAll() {
        classLists.clear();
        availableClassLists.clear();
        teacherClassLists.clear();
        parentAttendance.clear();
        parentChildAttendance.clear();
        classAttendance.clear();
        classAttendanceGrids.clear();
        externalCheckInLists.clear();
        externalCheckInCounts.clear();
    }

    private record ClassListCacheKey(UUID tenantId, UUID siteId) {
    }

    private record AvailableClassListCacheKey(UUID tenantId) {
    }

    private record TeacherClassListCacheKey(UUID tenantId, UUID teacherUserId) {
    }

    private record ParentAttendanceCacheKey(UUID tenantId, UUID parentUserId) {
    }

    private record ParentChildAttendanceCacheKey(UUID tenantId, UUID parentUserId, UUID childId) {
    }

    private record ClassAttendanceCacheKey(UUID classId, LocalDate classDate) {
    }

    private record ClassAttendanceGridCacheKey(UUID tenantId, UUID classId) {
    }

    private record ExternalCheckInListCacheKey(
            UUID tenantId,
            UUID classId,
            LocalDate checkDate,
            UUID checkedInByUserId,
            String checkedInByRole
    ) {
    }

    private record ExternalCheckInCountCacheKey(
            UUID tenantId,
            UUID classId,
            LocalDate startDate,
            LocalDate endDate,
            UUID checkedInByUserId,
            String checkedInByRole
    ) {
    }
}
