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

    Optional<User> findFirstByEmailIgnoreCase(String email);

    Optional<User> findByUsernameOrEmail(String username, String email);

    Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(String username, String email, Pageable pageable);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEnabledTrueAndRoles_Name(String roleName);

    // --- sign-in attempts: single statements, so parallel guesses are all counted -------------------------------

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update User u set u.failedLoginAttempts = u.failedLoginAttempts + 1 where u.id = :id")
    int incrementFailedAttempts(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("select u.failedLoginAttempts from User u where u.id = :id")
    int findFailedAttempts(@org.springframework.data.repository.query.Param("id") Long id);

    /** Blocks the account. Returns 1 only for the call that actually started the block. */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update User u set u.accountNonLocked = false, u.lockTime = :now where u.id = :id and u.accountNonLocked = true")
    int lockAccount(@org.springframework.data.repository.query.Param("id") Long id,
                    @org.springframework.data.repository.query.Param("now") java.time.Instant now);

    /** Lifts a block and starts the count again (the 30 minutes ran out, or an administrator unlocked the account). */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update User u set u.accountNonLocked = true, u.failedLoginAttempts = 0, u.lockTime = null where u.id = :id")
    int unlock(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update User u set u.failedLoginAttempts = 0, u.lockTime = null where u.id = :id and (u.failedLoginAttempts > 0 or u.lockTime is not null)")
    int clearFailedAttempts(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"roles"})
    Page<User> findAll(org.springframework.data.jpa.domain.Specification<User> spec, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"roles"})
    Page<User> findAll(Pageable pageable);
}
