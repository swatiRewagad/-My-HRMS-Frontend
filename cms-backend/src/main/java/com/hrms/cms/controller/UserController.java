package com.hrms.cms.controller;

import com.hrms.cms.dto.UserLoginRequest;
import com.hrms.cms.dto.UserProfileResponse;
import com.hrms.cms.dto.UserUpdateRequest;
import com.hrms.cms.service.CmsUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final CmsUserService userService;

    @PostMapping("/login")
    public UserProfileResponse login(@RequestBody UserLoginRequest request) {
        return userService.loginOrRegister(request.getPhone());
    }

    @GetMapping("/profile/{phone}")
    public UserProfileResponse getProfile(@PathVariable String phone) {
        return userService.getByPhone(phone);
    }

    @PutMapping("/profile/{phone}")
    public UserProfileResponse updateProfile(@PathVariable String phone,
                                             @RequestBody UserUpdateRequest request) {
        return userService.updateProfile(phone, request);
    }
}
