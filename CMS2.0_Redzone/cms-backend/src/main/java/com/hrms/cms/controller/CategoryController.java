package com.hrms.cms.controller;

import com.hrms.cms.entity.ComplaintCategory;
import com.hrms.cms.service.ComplaintService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final ComplaintService complaintService;

    @GetMapping
    public List<ComplaintCategory> getAll() {
        return complaintService.getAllCategories();
    }

    @GetMapping("/root")
    public List<ComplaintCategory> getRoots() {
        return complaintService.getRootCategories();
    }

    @GetMapping("/{parentId}/sub")
    public List<ComplaintCategory> getSubCategories(@PathVariable Long parentId) {
        return complaintService.getSubCategories(parentId);
    }
}
