package com.hrms.cms.service;

import com.hrms.cms.entity.CommentTemplate;
import com.hrms.cms.repository.CommentTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentTemplateService {

    private final CommentTemplateRepository templateRepository;

    @Transactional(readOnly = true)
    public List<CommentTemplate> getAll() {
        return templateRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<CommentTemplate> getActive() {
        return templateRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public CommentTemplate getById(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Comment template not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<CommentTemplate> getByCategory(String category) {
        return templateRepository.findByCategoryAndActiveTrue(category);
    }

    @Transactional(readOnly = true)
    public List<CommentTemplate> getByModeOfReceipt(String modeOfReceipt) {
        List<String> modes = Arrays.asList(modeOfReceipt, "ALL");
        return templateRepository.findByModeOfReceiptInAndActiveTrue(modes);
    }

    @Transactional(readOnly = true)
    public List<CommentTemplate> getByCategoryAndMode(String category, String modeOfReceipt) {
        List<String> modes = Arrays.asList(modeOfReceipt, "ALL");
        return templateRepository.findByCategoryAndModeOfReceiptInAndActiveTrue(category, modes);
    }

    @Transactional
    public CommentTemplate create(CommentTemplate template) {
        return templateRepository.save(template);
    }

    @Transactional
    public CommentTemplate update(Long id, CommentTemplate updates) {
        CommentTemplate existing = getById(id);
        existing.setTitle(updates.getTitle());
        existing.setDescription(updates.getDescription());
        existing.setContent(updates.getContent());
        existing.setModeOfReceipt(updates.getModeOfReceipt());
        existing.setCategory(updates.getCategory());
        return templateRepository.save(existing);
    }

    @Transactional
    public void deactivate(Long id) {
        CommentTemplate template = getById(id);
        template.setActive(false);
        templateRepository.save(template);
    }

    @Transactional
    public void activate(Long id) {
        CommentTemplate template = getById(id);
        template.setActive(true);
        templateRepository.save(template);
    }
}
