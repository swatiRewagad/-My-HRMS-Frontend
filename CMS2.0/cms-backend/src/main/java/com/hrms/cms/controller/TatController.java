package com.hrms.cms.controller;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.Holiday;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.HolidayRepository;
import com.hrms.cms.service.TatCalculationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tat")
@CrossOrigin(origins = "*")
public class TatController {

    private final TatCalculationService tatService;
    private final ComplaintRepository complaintRepository;
    private final HolidayRepository holidayRepository;

    public TatController(TatCalculationService tatService,
                         ComplaintRepository complaintRepository,
                         HolidayRepository holidayRepository) {
        this.tatService = tatService;
        this.complaintRepository = complaintRepository;
        this.holidayRepository = holidayRepository;
    }

    @GetMapping("/complaint/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> getComplaintTat(@PathVariable String complaintNumber) {
        return complaintRepository.findByComplaintNumber(complaintNumber)
            .map(complaint -> {
                var result = tatService.calculateTat(complaint.getFiledAt(), complaint.getResolvedAt());
                return ResponseEntity.ok(result.toMap());
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/holidays/{year}")
    public ResponseEntity<List<Holiday>> getHolidays(@PathVariable int year) {
        return ResponseEntity.ok(holidayRepository.findByYear(year));
    }

    @PostMapping("/holidays")
    public ResponseEntity<Holiday> addHoliday(@RequestBody Map<String, String> body) {
        Holiday holiday = new Holiday();
        holiday.setHolidayDate(LocalDate.parse(body.get("date")));
        holiday.setName(body.get("name"));
        holiday.setType(body.getOrDefault("type", "NATIONAL"));
        holiday.setYear(holiday.getHolidayDate().getYear());
        holiday.setNational("NATIONAL".equals(holiday.getType()));
        return ResponseEntity.ok(holidayRepository.save(holiday));
    }

    @DeleteMapping("/holidays/{id}")
    public ResponseEntity<Void> deleteHoliday(@PathVariable Long id) {
        holidayRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
