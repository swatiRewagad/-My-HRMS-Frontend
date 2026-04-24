package com.hrms.realmaiconfig.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "CONFIGURED_SUB_SERVICES")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConfiguredSubService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CONFIGURED_SERVICE_ID", nullable = false)
    private Long configuredServiceId;

    @Column(name = "SUB_SERVICE_ID", nullable = false, length = 100)
    private String subServiceId;

    @Column(name = "SUB_SERVICE_LABEL", length = 200)
    private String subServiceLabel;
}
