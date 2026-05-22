package com.hrms.cms.dto;

import lombok.Data;

@Data
public class UserUpdateRequest {
    private String name;
    private String email;
    private String address;
    private String pincode;
    private String state;
    private String district;
}
