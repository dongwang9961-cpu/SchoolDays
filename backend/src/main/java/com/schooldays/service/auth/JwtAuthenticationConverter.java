package com.schooldays.service.auth;

import java.util.List;
import java.util.UUID;

import com.schooldays.dao.auth.RoleDao;
import com.schooldays.dao.auth.UserDao;
import com.schooldays.entities.auth.TenantRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserDao userDao;
    private final RoleDao roleDao;

    public JwtAuthenticationConverter(UserDao userDao, RoleDao roleDao) {
        this.userDao = userDao;
        this.roleDao = roleDao;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (Exception exception) {
            return new JwtAuthenticationToken(jwt, List.of(), jwt.getClaimAsString("email"));
        }
        if (userDao.findAuthUserById(userId).map(user -> !"active".equalsIgnoreCase(user.status())).orElse(true)) {
            return new JwtAuthenticationToken(jwt, List.of(), jwt.getClaimAsString("email"));
        }
        List<SimpleGrantedAuthority> authorities = roleDao.findTenantRoles(userId).stream()
                .map(TenantRole::role)
                .distinct()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("email"));
    }
}
