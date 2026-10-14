package com.apsonic.backend.modules.trash.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrashItemResponse {
    private String id; // Use string to accommodate numeric IDs and slugs
    private String title;
    private Instant deletedAt;
    private String deletedBy;
}
