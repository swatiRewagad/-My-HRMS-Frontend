package com.hrms.cms.service;

import com.hrms.cms.dto.UserProfileResponse;
import com.hrms.cms.dto.UserUpdateRequest;
import com.hrms.cms.entity.CmsUser;
import com.hrms.cms.repository.CmsUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CmsUserService {

    private final CmsUserRepository userRepository;

    @Transactional
    public UserProfileResponse loginOrRegister(String phone) {
        var existing = userRepository.findByPhone(phone);

        if (existing.isPresent()) {
            CmsUser user = existing.get();
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
            return toResponse(user, false);
        }

        CmsUser newUser = CmsUser.builder()
                .name("")
                .phone(phone)
                .build();
        CmsUser saved = userRepository.save(newUser);
        return toResponse(saved, true);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getByPhone(String phone) {
        CmsUser user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return toResponse(user, false);
    }

    @Transactional
    public UserProfileResponse updateProfile(String phone, UserUpdateRequest req) {
        CmsUser user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (req.getName() != null && !req.getName().isBlank()) user.setName(req.getName());
        if (req.getEmail() != null) user.setEmail(req.getEmail());
        if (req.getAddress() != null) user.setAddress(req.getAddress());
        if (req.getPincode() != null) user.setPincode(req.getPincode());
        if (req.getState() != null) user.setState(req.getState());
        if (req.getDistrict() != null) user.setDistrict(req.getDistrict());

        CmsUser saved = userRepository.save(user);
        return toResponse(saved, false);
    }

    private UserProfileResponse toResponse(CmsUser user, boolean isNew) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .address(user.getAddress())
                .pincode(user.getPincode())
                .state(user.getState())
                .district(user.getDistrict())
                .newUser(isNew)
                .lastLoginAt(user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null)
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null)
                .build();
    }
}
