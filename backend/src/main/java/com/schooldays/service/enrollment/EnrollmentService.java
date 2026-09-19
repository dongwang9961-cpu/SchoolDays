package com.schooldays.service.enrollment;

import static com.schooldays.jooq.generated.tables.Classes.CLASSES;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.schooldays.dao.enrollment.EnrollmentDao;
import com.schooldays.dto.enrollment.CreateEnrollmentRequest;
import com.schooldays.dto.enrollment.CreateEnrollmentResponse;
import com.schooldays.dto.enrollment.EnrollmentListResponse;
import com.schooldays.dto.enrollment.EnrollmentRequestResponse;
import com.schooldays.dto.enrollment.EnrollmentResponse;
import com.schooldays.jooq.generated.tables.records.ClassFeeItemsRecord;
import com.schooldays.jooq.generated.tables.records.ClassesRecord;
import com.schooldays.jooq.generated.tables.records.ChildrenRecord;
import com.schooldays.jooq.generated.tables.records.EnrollmentsRecord;
import com.schooldays.service.cache.SchoolDataCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EnrollmentService {

    private final EnrollmentDao enrollmentDao;
    private final SchoolDataCacheService cacheService;

    @Autowired
    public EnrollmentService(EnrollmentDao enrollmentDao, SchoolDataCacheService cacheService) {
        this.enrollmentDao = enrollmentDao;
        this.cacheService = cacheService;
    }

    EnrollmentService(EnrollmentDao enrollmentDao) {
        this(enrollmentDao, new SchoolDataCacheService());
    }

    @Transactional(readOnly = true)
    public EnrollmentListResponse listParentEnrollments(UUID tenantId, UUID parentUserId) {
        List<EnrollmentResponse> enrollments = enrollmentDao.listParentEnrollments(tenantId, parentUserId).stream()
                .map(record -> {
                    EnrollmentsRecord enrollment = record.into(EnrollmentsRecord.class);
                    return EnrollmentResponse.from(
                            enrollment,
                            enrollmentDao.selectedOptionalFeeItemIds(enrollment.getId()),
                            record.get(CLASSES.NAME),
                            record.get(CLASSES.START_DATE),
                            record.get(CLASSES.END_DATE),
                            record.get(CLASSES.STATUS)
                    );
                })
                .toList();
        return new EnrollmentListResponse(enrollments);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentRequestResponse> listPendingSiteEnrollments(UUID tenantId, UUID siteId) {
        return enrollmentDao.listPendingSiteEnrollments(tenantId, siteId).stream()
                .map(record -> {
                    String firstName = record.get(com.schooldays.jooq.generated.tables.Children.CHILDREN.FIRST_NAME);
                    String lastName = record.get(com.schooldays.jooq.generated.tables.Children.CHILDREN.LAST_NAME);
                    return new EnrollmentRequestResponse(
                            record.get(com.schooldays.jooq.generated.tables.Enrollments.ENROLLMENTS.ID),
                            record.get(com.schooldays.jooq.generated.tables.Enrollments.ENROLLMENTS.CHILD_ID),
                            String.join(" ", firstName == null ? "" : firstName, lastName == null ? "" : lastName).trim(),
                            record.get(com.schooldays.jooq.generated.tables.Enrollments.ENROLLMENTS.CLASS_ID),
                            record.get(com.schooldays.jooq.generated.tables.Classes.CLASSES.NAME),
                            record.get(com.schooldays.jooq.generated.tables.Classes.CLASSES.START_DATE),
                            record.get(com.schooldays.jooq.generated.tables.Classes.CLASSES.END_DATE),
                            record.get(com.schooldays.jooq.generated.tables.Users.USERS.EMAIL),
                            record.get(com.schooldays.jooq.generated.tables.Enrollments.ENROLLMENTS.ENROLLMENT_STATUS),
                            record.get(com.schooldays.jooq.generated.tables.Enrollments.ENROLLMENTS.CREATED_AT)
                    );
                })
                .toList();
    }

    @Transactional
    public void approveEnrollment(UUID tenantId, UUID enrollmentId) {
        EnrollmentsRecord enrollment = enrollmentDao.findPendingEnrollment(tenantId, enrollmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pending enrollment request was not found"));
        ClassesRecord classRecord = enrollmentDao.findActiveClass(tenantId, enrollment.getClassId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class was not found"));
        int capacity = classRecord.getCapacity() == null ? Integer.MAX_VALUE : classRecord.getCapacity();
        if (enrollmentDao.activeEnrollmentCount(tenantId, enrollment.getClassId()) >= capacity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This class has no available seats");
        }
        enrollmentDao.updateEnrollmentStatus(tenantId, enrollmentId, "enrolled", OffsetDateTime.now());
        cacheService.clearAttendanceCaches(tenantId);
    }

    @Transactional
    public void rejectEnrollment(UUID tenantId, UUID enrollmentId) {
        enrollmentDao.findPendingEnrollment(tenantId, enrollmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pending enrollment request was not found"));
        enrollmentDao.updateEnrollmentStatus(tenantId, enrollmentId, "rejected", OffsetDateTime.now());
    }

    @Transactional
    public CreateEnrollmentResponse createParentEnrollment(UUID parentUserId, CreateEnrollmentRequest request) {
        UUID tenantId = request.tenantId();
        UUID classId = request.classId();
        List<UUID> childIds = request.childIds().stream().distinct().toList();
        if (childIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one child");
        }

        ClassesRecord classRecord = enrollmentDao.findActiveClass(tenantId, classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class was not found"));
        if (!isRegistrationOpen(classRecord, OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration is closed for this class");
        }

        List<ChildrenRecord> children = enrollmentDao.findParentChildren(tenantId, parentUserId, childIds);
        if (children.size() != childIds.size()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "One or more children do not belong to this parent");
        }

        int capacity = classRecord.getCapacity() == null ? Integer.MAX_VALUE : classRecord.getCapacity();
        int enrolledCount = enrollmentDao.activeEnrollmentCount(tenantId, classId);
        if (enrolledCount + childIds.size() > capacity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This class does not have enough available seats");
        }

        for (UUID childId : childIds) {
            if (enrollmentDao.enrollmentExists(tenantId, childId, classId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "One or more selected children are already enrolled in this class");
            }
        }

        List<ClassFeeItemsRecord> feeItems = enrollmentDao.activeFeeItems(tenantId, classId);
        int requiredFeeTotal = feeItems.stream()
                .filter(ClassFeeItemsRecord::getRequired)
                .mapToInt(ClassFeeItemsRecord::getAmount)
                .sum();
        String currency = feeItems.stream()
                .findFirst()
                .map(item -> item.getMetadata() == null ? "USD" : "USD")
                .orElse("USD");
        String status = "pending";

        Set<UUID> optionalFeeItemIds = new HashSet<>(request.optionalFeeItemIds() == null ? List.of() : request.optionalFeeItemIds());
        Set<UUID> validOptionalFeeItemIds = feeItems.stream()
                .filter(item -> !item.getRequired())
                .map(ClassFeeItemsRecord::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!validOptionalFeeItemIds.containsAll(optionalFeeItemIds)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected optional fees are not valid for this class");
        }
        java.util.Map<UUID, ClassFeeItemsRecord> feeItemById = feeItems.stream()
                .collect(java.util.stream.Collectors.toMap(ClassFeeItemsRecord::getId, item -> item));

        OffsetDateTime now = OffsetDateTime.now();
        List<EnrollmentResponse> responses = childIds.stream()
                .map(childId -> {
                    EnrollmentsRecord enrollment = enrollmentDao.createEnrollment(tenantId, childId, classId, status, parentUserId, now);
                    optionalFeeItemIds.forEach(feeItemId -> {
                        ClassFeeItemsRecord feeItem = feeItemById.get(feeItemId);
                        String perkStatus = feeItem != null && feeItem.getAmount() != null && feeItem.getAmount() > 0
                                ? "pending_payment"
                                : "active";
                        enrollmentDao.createPerk(tenantId, enrollment.getId(), feeItemId, perkStatus, now);
                    });
                    return EnrollmentResponse.from(enrollment, optionalFeeItemIds.stream().toList());
                })
                .toList();

        cacheService.clearAttendanceCaches(tenantId);
        return new CreateEnrollmentResponse(responses, requiredFeeTotal > 0, requiredFeeTotal, currency);
    }

    private boolean isRegistrationOpen(ClassesRecord classRecord, OffsetDateTime now) {
        if (classRecord.getRegistrationOpensAt() != null
                && classRecord.getRegistrationOpensAt().isAfter(now)) {
            return false;
        }
        if (classRecord.getRegistrationClosesAt() != null) {
            return !classRecord.getRegistrationClosesAt().isBefore(now);
        }
        return classRecord.getEndDate() != null && !classRecord.getEndDate().isBefore(now.toLocalDate());
    }
}
