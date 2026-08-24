package com.rbi.cms.assignment.web;

import com.rbi.cms.assignment.dto.request.ResolveRequest;
import com.rbi.cms.assignment.dto.response.ResolveResponse;
import com.rbi.cms.assignment.service.AssignmentResolveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assignment")
@RequiredArgsConstructor
public class ResolveController {

    private final AssignmentResolveService resolveService;

    @PostMapping("/resolve")
    public ResponseEntity<ResolveResponse> resolve(@Valid @RequestBody ResolveRequest request) {
        ResolveResponse response = resolveService.resolve(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resolve/batch")
    public ResponseEntity<List<ResolveResponse>> resolveBatch(@Valid @RequestBody List<ResolveRequest> requests) {
        if (requests.size() > 500) {
            return ResponseEntity.badRequest().build();
        }
        List<ResolveResponse> responses = requests.stream()
                .map(resolveService::resolve)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
