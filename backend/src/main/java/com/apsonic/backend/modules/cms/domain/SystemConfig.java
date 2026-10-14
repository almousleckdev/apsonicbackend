package com.apsonic.backend.modules.cms.domain;

import com.apsonic.backend.shared.audit.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "system_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfig extends BaseAuditableEntity {

    @Column(name = "config_key", nullable = false, unique = true)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    @Column(name = "description")
    private String description;
}
