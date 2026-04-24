package com.hrms.realmaiconfig.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "REALMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Realm {

    @Id
    @Column(name = "ID", length = 100)
    private String id;

    @Column(name = "NAME", nullable = false, length = 200)
    private String name;

    @Column(name = "DISPLAY_NAME", nullable = false, length = 300)
    private String displayName;

    @Column(name = "INITIALS", length = 10)
    private String initials;

    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @Column(name = "REALM_ID", nullable = false, unique = true, length = 50)
    private String realmId;

    @Column(name = "OWNER", length = 200)
    private String owner;

    @Column(name = "OWNER_EMAIL", length = 200)
    private String ownerEmail;

    @Column(name = "DEPARTMENT", length = 200)
    private String department;

    @Column(name = "TYPE", length = 50)
    private String type;

    @Column(name = "USER_COUNT")
    private Integer userCount;

    @Column(name = "STATUS", length = 20)
    private String status;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "SYNCED_AT")
    private LocalDateTime syncedAt;
}
