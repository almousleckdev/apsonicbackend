package com.apsonic.backend.modules.contact.dto;

import com.apsonic.backend.modules.contact.domain.ContactMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactMessageResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String country;
    private String department;
    private String subject;
    private String message;
    private Boolean isRead;
    private Boolean isReplied;
    private LocalDateTime createdAt;
    private LocalDateTime repliedAt;

    public static ContactMessageResponse fromEntity(ContactMessage entity) {
        return ContactMessageResponse.builder()
                .id(entity.getId())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .country(entity.getCountry())
                .department(entity.getDepartment())
                .subject(entity.getSubject())
                .message(entity.getMessage())
                .isRead(entity.getIsRead())
                .isReplied(entity.getIsReplied())
                .createdAt(entity.getCreatedAt())
                .repliedAt(entity.getRepliedAt())
                .build();
    }
}
