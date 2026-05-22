package com.hrms.cms.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileResponse {
    private Long id;
    private String name;
    private String phone;
    private String email;
    private String address;
    private String pincode;
    private String state;
    private String district;
    private boolean newUser;
    private String lastLoginAt;
    private String createdAt;
}
