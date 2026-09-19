package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "COMPLAINT_REPRESENTATIVES", indexes = {
    @Index(name = "idx_cr_complaint", columnList = "complaint_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintRepresentative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false, unique = true)
    private Long complaintId;

    @Column(name = "draft_id", length = 50)
    private String draftId;

    @Column(name = "has_auth_rep", length = 10)
    private String hasAuthRep;

    @Column(name = "authorize_representative", length = 10)
    private String authorizeRepresentative;

    @Column(name = "through_advocate", length = 10)
    private String throughAdvocate;

    @Column(name = "rep_name", length = 200)
    private String repName;

    @Column(name = "rep_phone", length = 20)
    private String repPhone;

    @Column(name = "rep_email", length = 254)
    private String repEmail;

    @Column(name = "rep_address", length = 500)
    private String repAddress;

    @Column(name = "rep_city", length = 100)
    private String repCity;

    @Column(name = "rep_district", length = 100)
    private String repDistrict;

    @Column(name = "rep_state", length = 100)
    private String repState;

    @Column(name = "rep_pincode", length = 20)
    private String repPincode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
