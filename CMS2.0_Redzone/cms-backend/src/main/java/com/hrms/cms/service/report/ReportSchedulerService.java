package com.hrms.cms.service.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.ReportDefinition;
import com.hrms.cms.entity.ReportSchedule;
import com.hrms.cms.repository.ReportDefinitionRepository;
import com.hrms.cms.repository.ReportScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportSchedulerService {

    private final ReportScheduleRepository scheduleRepo;
    private final ReportDefinitionRepository reportDefRepo;
    private final QueryCompiler queryCompiler;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "0 0 22 * * *")
    public void runSlot2200() { processSlot("22:00"); }

    @Scheduled(cron = "0 0 23 * * *")
    public void runSlot2300() { processSlot("23:00"); }

    @Scheduled(cron = "0 0 0 * * *")
    public void runSlot0000() { processSlot("00:00"); }

    @Scheduled(cron = "0 0 1 * * *")
    public void runSlot0100() { processSlot("01:00"); }

    @Scheduled(cron = "0 0 2 * * *")
    public void runSlot0200() { processSlot("02:00"); }

    private void processSlot(String slot) {
        log.info("Report scheduler: processing slot {}", slot);
        List<ReportSchedule> schedules = scheduleRepo.findByActiveTrueAndDeliverySlot(slot);

        for (ReportSchedule schedule : schedules) {
            try {
                processReport(schedule);
            } catch (Exception e) {
                log.error("Report execution failed for schedule {} (report {}): {}",
                        schedule.getId(), schedule.getReportDefinitionId(), e.getMessage());
            }
        }
        log.info("Report scheduler: slot {} complete, processed {} reports", slot, schedules.size());
    }

    private void processReport(ReportSchedule schedule) {
        Optional<ReportDefinition> defOpt = reportDefRepo.findById(schedule.getReportDefinitionId());
        if (defOpt.isEmpty()) {
            log.warn("Report definition {} not found for schedule {}", schedule.getReportDefinitionId(), schedule.getId());
            return;
        }

        ReportDefinition def = defOpt.get();
        ReportQuery query = deserializeQuery(def.getQueryDefinition());
        if (query == null) {
            log.warn("Failed to deserialize query for report {}", def.getId());
            return;
        }

        List<Map<String, Object>> results = queryCompiler.execute(query, "SENIOR", null);

        byte[] excelBytes = generateExcel(def.getSentence(), results);

        // TODO: Send email with attachment using Spring Mail
        log.info("Generated Excel ({} bytes, {} rows) for report '{}' → {}",
                excelBytes.length, results.size(), def.getSentence(), schedule.getRecipientEmail());

        schedule.setLastSentAt(LocalDateTime.now());
        scheduleRepo.save(schedule);
    }

    public byte[] generateExcel(String title, List<Map<String, Object>> data) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Report");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue(title);

            if (data.isEmpty()) {
                Row emptyRow = sheet.createRow(2);
                emptyRow.createCell(0).setCellValue("No data found");
            } else {
                Set<String> columns = data.get(0).keySet();
                Row headerRow = sheet.createRow(2);
                int col = 0;
                for (String column : columns) {
                    Cell cell = headerRow.createCell(col++);
                    cell.setCellValue(column);
                    cell.setCellStyle(headerStyle);
                }

                int rowNum = 3;
                for (Map<String, Object> row : data) {
                    Row dataRow = sheet.createRow(rowNum++);
                    col = 0;
                    for (String column : columns) {
                        Object value = row.get(column);
                        Cell cell = dataRow.createCell(col++);
                        if (value == null) {
                            cell.setCellValue("");
                        } else if (value instanceof Number n) {
                            cell.setCellValue(n.doubleValue());
                        } else {
                            cell.setCellValue(value.toString());
                        }
                    }
                    if (rowNum > 5002) break;
                }

                for (int i = 0; i < columns.size(); i++) {
                    sheet.autoSizeColumn(i);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Excel generation failed: {}", e.getMessage());
            return new byte[0];
        }
    }

    @SuppressWarnings("unchecked")
    private ReportQuery deserializeQuery(String json) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            String subjectId = (String) map.get("subjectId");
            String groupByField = (String) map.get("groupByField");
            String sentence = (String) map.getOrDefault("sentence", "");

            List<ReportQuery.QueryFilter> filters = new ArrayList<>();
            Object filtersRaw = map.get("filters");
            if (filtersRaw instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> fmap) {
                        filters.add(ReportQuery.QueryFilter.builder()
                                .field((String) fmap.get("field"))
                                .operator((String) fmap.get("operator"))
                                .value((String) fmap.get("value"))
                                .build());
                    }
                }
            }

            return ReportQuery.builder()
                    .subjectId(subjectId)
                    .filters(filters)
                    .groupByField(groupByField)
                    .sentence(sentence)
                    .build();
        } catch (Exception e) {
            return null;
        }
    }
}
