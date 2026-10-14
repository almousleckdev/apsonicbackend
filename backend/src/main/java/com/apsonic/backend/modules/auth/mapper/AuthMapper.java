package com.apsonic.backend.modules.auth.mapper;

import com.apsonic.backend.modules.auth.dto.response.UserProfileResponse;
import com.apsonic.backend.modules.auth.entity.Permission;
import com.apsonic.backend.modules.auth.entity.Role;
import com.apsonic.backend.modules.auth.entity.User;
import com.apsonic.backend.security.UserPrincipal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AuthMapper {

    @Mapping(target = "roles", source = "roles", qualifiedByName = "mapRolesToStrings")
    @Mapping(target = "permissions", source = "roles", qualifiedByName = "mapPermissionsToStrings")
    UserProfileResponse toUserProfileResponse(User user);

    @Mapping(target = "roles", source = "authorities", qualifiedByName = "mapAuthoritiesToRoles")
    @Mapping(target = "permissions", source = "authorities", qualifiedByName = "mapAuthoritiesToPermissions")
    UserProfileResponse toUserProfileResponse(UserPrincipal userPrincipal);

    @Named("mapRolesToStrings")
    default Set<String> mapRolesToStrings(Set<Role> roles) {
        if (roles == null) return Collections.emptySet();
        return roles.stream().map(Role::getName).collect(Collectors.toSet());
    }

    @Named("mapPermissionsToStrings")
    default Set<String> mapPermissionsToStrings(Set<Role> roles) {
        if (roles == null) return Collections.emptySet();
        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getName)
                .collect(Collectors.toSet());
    }

    @Named("mapAuthoritiesToRoles")
    default Set<String> mapAuthoritiesToRoles(Collection<?> authorities) {
        if (authorities == null) return Collections.emptySet();
        return authorities.stream()
                .map(Object::toString)
                .filter(auth -> auth.startsWith("ROLE_"))
                .collect(Collectors.toSet());
    }

    @Named("mapAuthoritiesToPermissions")
    default Set<String> mapAuthoritiesToPermissions(Collection<?> authorities) {
        if (authorities == null) return Collections.emptySet();
        return authorities.stream()
                .map(Object::toString)
                .filter(auth -> !auth.startsWith("ROLE_"))
                .collect(Collectors.toSet());
    }
}
