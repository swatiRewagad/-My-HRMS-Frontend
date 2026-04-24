package com.hrms.realmaiconfig.controller;

import com.hrms.realmaiconfig.entity.Realm;
import com.hrms.realmaiconfig.service.RealmConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/realms")
@RequiredArgsConstructor
public class RealmController {

    private final RealmConfigService realmConfigService;

    @GetMapping
    public ResponseEntity<List<Realm>> getActiveRealms() {
        return ResponseEntity.ok(realmConfigService.getActiveRealms());
    }
}
