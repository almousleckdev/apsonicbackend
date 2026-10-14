package com.apsonic.backend.modules.auth.repository;

import com.apsonic.backend.modules.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameOrEmail(String username, String email);

    Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(String username, String email, Pageable pageable);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEnabledTrueAndRoles_Name(String roleName);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"roles"})
    Page<User> findAll(org.springframework.data.jpa.domain.Specification<User> spec, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"roles"})
    Page<User> findAll(Pageable pageable);
}
