package com.schooldays.dao.auth;

import static com.schooldays.jooq.generated.tables.Roles.ROLES;
import static com.schooldays.jooq.generated.tables.UserRoles.USER_ROLES;

import java.util.List;
import java.util.UUID;

import com.schooldays.entities.auth.TenantRole;
import com.schooldays.service.auth.InvalidAuthRequestException;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class RoleDao {

    private final DSLContext dsl;
    private final RoleRepository roleRepository;

    public RoleDao(DSLContext dsl, RoleRepository roleRepository) {
        this.dsl = dsl;
        this.roleRepository = roleRepository;
    }

    public List<TenantRole> findTenantRoles(UUID userId) {
        return dsl.select(USER_ROLES.TENANT_ID, ROLES.NAME)
                .from(USER_ROLES)
                .join(ROLES).on(ROLES.ID.eq(USER_ROLES.ROLE_ID))
                .where(USER_ROLES.USER_ID.eq(userId))
                .fetch(record -> new TenantRole(record.get(USER_ROLES.TENANT_ID), record.get(ROLES.NAME)));
    }

    public void assignRole(UUID userId, UUID tenantId, String roleName) {
        UUID roleId = roleRepository.findByName(roleName)
                .map(record -> record.getId())
                .orElseThrow(() -> new InvalidAuthRequestException("Unknown role: " + roleName));

        dsl.insertInto(USER_ROLES)
                .set(USER_ROLES.USER_ID, userId)
                .set(USER_ROLES.ROLE_ID, roleId)
                .set(USER_ROLES.TENANT_ID, tenantId)
                .onConflict(USER_ROLES.USER_ID, USER_ROLES.ROLE_ID, USER_ROLES.TENANT_ID)
                .doNothing()
                .execute();
    }
}
