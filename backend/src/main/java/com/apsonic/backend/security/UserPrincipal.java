package com.apsonic.backend.security;

import com.apsonic.backend.modules.auth.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Getter
@Builder
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String username;
    @JsonIgnore
    private final String password;
    private final String firstName;
    private final String lastName;
    private final String avatarUrl;
    private final boolean enabled;
    private final boolean accountNonExpired;
    private final boolean credentialsNonExpired;
    private final boolean accountNonLocked;
    private final Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal create(User user) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        if (user.getRoles() != null) {
            user.getRoles().forEach(role -> {
                if (role != null && role.getName() != null) {
                    authorities.add(new SimpleGrantedAuthority(role.getName()));
                    if (role.getPermissions() != null) {
                        role.getPermissions().forEach(permission -> {
                            if (permission != null && permission.getName() != null) {
                                authorities.add(new SimpleGrantedAuthority(permission.getName()));
                            }
                        });
                    }
                }
            });
        }

        return UserPrincipal.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .password(user.getPassword())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .avatarUrl(user.getAvatarUrl())
                .enabled(user.isEnabled())
                .accountNonExpired(user.isAccountNonExpired())
                .credentialsNonExpired(user.isCredentialsNonExpired())
                .accountNonLocked(user.isAccountNonLocked())
                .authorities(authorities)
                .build();
    }
}
