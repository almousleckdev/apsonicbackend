package com.apsonic.backend.shared.audit;

import com.apsonic.backend.shared.util.SecurityUtils;
import org.springframework.data.domain.AuditorAware;
import org.springframework.lang.NonNull;

import java.util.Optional;

public class SecurityAuditorAware implements AuditorAware<String> {

    @Override
    @NonNull
    public Optional<String> getCurrentAuditor() {
        return SecurityUtils.getCurrentUsername().or(() -> Optional.of("SYSTEM"));
    }
}
