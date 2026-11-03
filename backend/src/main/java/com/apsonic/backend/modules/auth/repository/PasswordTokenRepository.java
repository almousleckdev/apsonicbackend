package com.apsonic.backend.modules.auth.repository;

import com.apsonic.backend.modules.auth.entity.PasswordToken;
import com.apsonic.backend.modules.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PasswordTokenRepository extends JpaRepository<PasswordToken, Long> {

    @Query("select t from PasswordToken t join fetch t.user where t.tokenHash = :hash")
    Optional<PasswordToken> findByTokenHash(@Param("hash") String hash);

    /** Spends a token. Returns 1 only for the single caller that finds it unused and unexpired. */
    @Modifying
    @Query("update PasswordToken t set t.usedAt = :now where t.tokenHash = :hash and t.usedAt is null and t.expiresAt > :now")
    int markUsed(@Param("hash") String hash, @Param("now") Instant now);

    /** Cancels every link still outstanding for the user, so only the newest one works. */
    @Modifying
    @Query("update PasswordToken t set t.usedAt = :now where t.user = :user and t.usedAt is null")
    int invalidateOutstanding(@Param("user") User user, @Param("now") Instant now);

    @Modifying
    int deleteByExpiresAtBefore(Instant cutoff);
}
