package com.schooldays.service.enrollment;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.schooldays.dao.enrollment.EnrollmentDao;
import com.schooldays.dto.enrollment.CreateEnrollmentRequest;
import com.schooldays.dto.enrollment.CreateEnrollmentResponse;
import com.schooldays.dto.enrollment.EnrollmentListResponse;
import com.schooldays.dto.enrollment.EnrollmentResponse;
import com.schooldays.jooq.generated.tables.records.ClassFeeItemsRecord;
import com.schooldays.jooq.generated.tables.records.ClassesRecord;
import com.schooldays.jooq.generated.tables.records.ChildrenRecord;
import com.schooldays.jooq.generated.tables.records.EnrollmentsRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EnrollmentService {

    private final EnrollmentDao enrollmentDao;

    public EnrollmentService(EnrollmentDao enrollmentDao) {
        this.enrollmentDao = enrollmentDao;
    }

    @Transactional(readOnly = true)
    public EnrollmentListResponse listParentEnrollments(UUID tenantId, UUID parentUserId) {
        List<EnrollmentResponse> enrollments = enrollmentDao.listParentEnrollments(tenantId, parentUserId).stream()
                .map(record -> EnrollmentResponse.from(record, enrollmentDao.selectedOptionalFeeItemIds(record.getId())))
                .toList();
        return new EnrollmentListResponse(enrollments);
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
        if (classRecord.getRegistrationClosesAt() != null && classRecord.getRegistrationClosesAt().isBefore(OffsetDateTime.now())) {
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
        String status = requiredFeeTotal > 0 ? "pending_payment" : "enrolled";

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

        return new CreateEnrollmentResponse(responses, requiredFeeTotal > 0, requiredFeeTotal, currency);
    }
}
