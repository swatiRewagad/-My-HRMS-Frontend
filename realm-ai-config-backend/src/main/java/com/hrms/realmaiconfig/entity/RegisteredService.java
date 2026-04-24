package com.hrms.realmaiconfig.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "REGISTERED_SERVICES")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegisteredService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "NAME", nullable = false, length = 200)
    private String name;

    @Column(name = "SLUG", nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "BASE_URL", length = 500)
    private String baseUrl;

    @Column(name = "VERSION", length = 50)
    private String version;

    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @Column(name = "CATEGORY", length = 100)
    private String category;

    @Column(name = "AUTH_TYPE", length = 50)
    private String authType;

    @Column(name = "HEALTH_CHECK_ENDPOINT", length = 200)
    private String healthCheckEndpoint;

    @Column(name = "OWNER_NAME", length = 200)
    private String ownerName;

    @Column(name = "OWNER_EMAIL", length = 200)
    private String ownerEmail;

    @Column(name = "TAGS", length = 500)
    private String tags;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Column(name = "REGISTERED_AT")
    private LocalDateTime registeredAt;

    @PrePersist
    protected void onCreate() {
        this.registeredAt = LocalDateTime.now();
    }
}
