package com.schooldays.service.auth;

import java.util.List;
import java.util.UUID;

import com.schooldays.dao.auth.RoleDao;
import com.schooldays.dao.auth.UserDao;
import com.schooldays.entities.auth.AuthenticatedUser;
import com.schooldays.entities.auth.TenantRole;
import com.schooldays.entities.auth.UserAuthRow;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class SchoolDaysUserDetailsService implements UserDetailsService {

    private final UserDao userDao;
    private final RoleDao roleDao;

    public SchoolDaysUserDetailsService(UserDao userDao, RoleDao roleDao) {
        this.userDao = userDao;
        this.roleDao = roleDao;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        var user = userDao.findAuthUserByEmail(EmailNormalizer.normalize(username))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        if (!isActive(user)) {
            throw new UsernameNotFoundException("User is inactive");
        }

        String passwordHash = user.passwordHash();
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new UsernameNotFoundException("Password login is not enabled for this user");
        }

        return toAuthenticatedUser(user);
    }

    public AuthenticatedUser loadById(UUID userId) {
        var user = userDao.findAuthUserById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        if (!isActive(user)) {
            throw new UsernameNotFoundException("User is inactive");
        }

        return toAuthenticatedUser(user);
    }

    private boolean isActive(UserAuthRow user) {
        return user != null && "active".equalsIgnoreCase(user.status());
    }

    private AuthenticatedUser toAuthenticatedUser(UserAuthRow user) {
        List<TenantRole> tenantRoles = roleDao.findTenantRoles(user.id());
        List<SimpleGrantedAuthority> authorities = tenantRoles.stream()
                .map(TenantRole::role)
                .distinct()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        return new AuthenticatedUser(
                user.id(),
                user.email(),
                user.phone(),
                user.passwordHash(),
                user.status(),
                tenantRoles,
                List.copyOf(authorities)
        );
    }
}
