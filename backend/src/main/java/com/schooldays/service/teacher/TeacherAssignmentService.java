package com.schooldays.service.teacher;

import static com.schooldays.jooq.generated.tables.Classes.CLASSES;
import static com.schooldays.jooq.generated.tables.TeacherAssignments.TEACHER_ASSIGNMENTS;
import static com.schooldays.jooq.generated.tables.Users.USERS;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.schooldays.dao.auth.RoleDao;
import com.schooldays.dao.auth.UserDao;
import com.schooldays.dto.teacher.AssignTeacherRequest;
import com.schooldays.dto.teacher.ClassTeacherListResponse;
import com.schooldays.dto.teacher.ClassTeacherResponse;
import com.schooldays.service.auth.EmailNormalizer;
import com.schooldays.service.cache.SchoolDataCacheService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TeacherAssignmentService {

    private final DSLContext dsl;
    private final UserDao userDao;
    private final RoleDao roleDao;
    private final SchoolDataCacheService cacheService;

    @Autowired
    public TeacherAssignmentService(DSLContext dsl, UserDao userDao, RoleDao roleDao, SchoolDataCacheService cacheService) {
        this.dsl = dsl;
        this.userDao = userDao;
        this.roleDao = roleDao;
        this.cacheService = cacheService;
    }

    TeacherAssignmentService(DSLContext dsl, UserDao userDao, RoleDao roleDao) {
        this(dsl, userDao, roleDao, new SchoolDataCacheService());
    }

    public ClassTeacherListResponse listClassTeachers(UUID tenantId, UUID classId) {
        requireClass(tenantId, classId);

        List<ClassTeacherResponse> teachers = dsl.select(
                        TEACHER_ASSIGNMENTS.ID,
                        TEACHER_ASSIGNMENTS.CLASS_ID,
                        TEACHER_ASSIGNMENTS.TEACHER_USER_ID,
                        TEACHER_ASSIGNMENTS.CREATED_AT,
                        USERS.FIRST_NAME,
                        USERS.LAST_NAME,
                        USERS.EMAIL,
                        USERS.PHONE,
                        USERS.STATUS
                )
                .from(TEACHER_ASSIGNMENTS)
                .join(USERS).on(USERS.ID.eq(TEACHER_ASSIGNMENTS.TEACHER_USER_ID))
                .where(TEACHER_ASSIGNMENTS.CLASS_ID.eq(classId))
                .orderBy(USERS.LAST_NAME.asc(), USERS.FIRST_NAME.asc(), USERS.EMAIL.asc())
                .fetch(ClassTeacherResponse::from);
        return new ClassTeacherListResponse(teachers);
    }

    public ClassTeacherResponse assignTeacher(UUID tenantId, UUID classId, UUID assignedByUserId, AssignTeacherRequest request) {
        requireClass(tenantId, classId);
        String email = EmailNormalizer.normalize(request.email());
        UUID teacherUserId = userDao.findUserIdByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Teacher account was not found"));

        roleDao.assignRole(teacherUserId, tenantId, "TEACHER");
        dsl.insertInto(TEACHER_ASSIGNMENTS)
                .set(TEACHER_ASSIGNMENTS.CLASS_ID, classId)
                .set(TEACHER_ASSIGNMENTS.TEACHER_USER_ID, teacherUserId)
                .set(TEACHER_ASSIGNMENTS.ASSIGNED_BY_USER_ID, assignedByUserId)
                .onConflict(TEACHER_ASSIGNMENTS.CLASS_ID, TEACHER_ASSIGNMENTS.TEACHER_USER_ID)
                .doNothing()
                .execute();
        cacheService.clearClassCaches(tenantId);

        return teacherAssignment(classId, teacherUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Teacher assignment could not be loaded"));
    }

    private void requireClass(UUID tenantId, UUID classId) {
        boolean classExists = dsl.fetchExists(dsl.selectOne()
                .from(CLASSES)
                .where(CLASSES.TENANT_ID.eq(tenantId))
                .and(CLASSES.ID.eq(classId)));
        if (!classExists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Class was not found");
        }
    }

    private Optional<ClassTeacherResponse> teacherAssignment(UUID classId, UUID teacherUserId) {
        return dsl.select(
                        TEACHER_ASSIGNMENTS.ID,
                        TEACHER_ASSIGNMENTS.CLASS_ID,
                        TEACHER_ASSIGNMENTS.TEACHER_USER_ID,
                        TEACHER_ASSIGNMENTS.CREATED_AT,
                        USERS.FIRST_NAME,
                        USERS.LAST_NAME,
                        USERS.EMAIL,
                        USERS.PHONE,
                        USERS.STATUS
                )
                .from(TEACHER_ASSIGNMENTS)
                .join(USERS).on(USERS.ID.eq(TEACHER_ASSIGNMENTS.TEACHER_USER_ID))
                .where(TEACHER_ASSIGNMENTS.CLASS_ID.eq(classId))
                .and(TEACHER_ASSIGNMENTS.TEACHER_USER_ID.eq(teacherUserId))
                .fetchOptional(ClassTeacherResponse::from);
    }
}
