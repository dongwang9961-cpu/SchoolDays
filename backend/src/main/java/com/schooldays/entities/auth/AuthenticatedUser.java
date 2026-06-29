package com.schooldays.entities.auth;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class AuthenticatedUser implements UserDetails {

    private final UUID id;
    private final String email;
    private final String phone;
    private final String passwordHash;
    private final String status;
    private final List<TenantRole> tenantRoles;
    private final List<GrantedAuthority> authorities;

    public AuthenticatedUser(
            UUID id,
            String email,
            String phone,
            String passwordHash,
            String status,
            List<TenantRole> tenantRoles,
            List<GrantedAuthority> authorities
    ) {
        this.id = id;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.status = status;
        this.tenantRoles = List.copyOf(tenantRoles);
        this.authorities = List.copyOf(authorities);
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String phone() {
        return phone;
    }

    public List<TenantRole> tenantRoles() {
        return tenantRoles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !"locked".equalsIgnoreCase(status);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "active".equalsIgnoreCase(status);
    }
}
