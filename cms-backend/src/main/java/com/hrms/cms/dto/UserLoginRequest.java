package com.hrms.cms.dto;

import lombok.Data;

@Data
public class UserLoginRequest {
    private String phone;
    private String otp;
}
