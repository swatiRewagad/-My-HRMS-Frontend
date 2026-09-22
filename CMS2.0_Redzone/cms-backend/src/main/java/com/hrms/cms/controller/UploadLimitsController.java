package com.hrms.cms.controller;

import com.hrms.cms.service.UploadLimitsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The attachment limits the client must honour (13.1).
 *
 * <p>Exists so the browser can ask what the limit is instead of shipping {@code maxFileSizeMB: 2} in
 * three environment files. Those constants could not track a configuration change, which is how the
 * UI came to promise 5 MB while the server rejected at 2 MB.
 *
 * <p>Anonymous GET is deliberate: the citizen complaint form is filed without logging in, and it needs
 * the limit before any upload is attempted. Nothing here is sensitive — it is the same number the
 * rejection message would reveal — and the endpoint is read-only.
 */
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class UploadLimitsController {

    private final UploadLimitsService uploadLimits;

    @GetMapping("/upload-limits")
    public ResponseEntity<Map<String, Object>> uploadLimits() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("maxFileSizeBytes", uploadLimits.maxFileSizeBytes());
        body.put("maxTotalSizeBytes", uploadLimits.maxTotalSizeBytes());
        body.put("maxFileSizeMb", uploadLimits.maxFileSizeMb());
        body.put("maxTotalSizeMb", uploadLimits.maxTotalSizeMb());
        body.put("maxFileCount", uploadLimits.maxFileCount());
        return ResponseEntity.ok(body);
    }
}
