package com.hrms.realmaiconfig.controller;

import com.hrms.realmaiconfig.entity.EcmExtractedField;
import com.hrms.realmaiconfig.entity.EcmFile;
import com.hrms.realmaiconfig.service.EcmFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api/ecm")
@RequiredArgsConstructor
@Slf4j
public class EcmFileController {

    private final EcmFileService ecmFileService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFiles(
            @RequestParam("realmId") String realmId,
            @RequestParam(value = "folderPath", required = false) String folderPath,
            @RequestParam(value = "uploadedBy", required = false, defaultValue = "Unknown") String uploadedBy,
            @RequestParam("files") List<MultipartFile> files) {

        try {
            List<EcmFile> uploaded = files.stream().map(file -> {
                try {
                    return ecmFileService.uploadFile(realmId, folderPath, uploadedBy, file);
                } catch (IOException e) {
                    log.error("Failed to upload file: {}", file.getOriginalFilename(), e);
                    throw new RuntimeException("Failed to upload: " + file.getOriginalFilename(), e);
                }
            }).toList();

            return ResponseEntity.ok(uploaded);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/files")
    public ResponseEntity<List<EcmFile>> getFiles(
            @RequestParam("realmId") String realmId,
            @RequestParam(value = "folderPath", required = false) String folderPath) {
        return ResponseEntity.ok(ecmFileService.getFiles(realmId, folderPath));
    }

    @DeleteMapping("/files/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable Long id) {
        boolean deleted = ecmFileService.deleteFile(id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "File deleted"));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/extracted-fields/{id}")
    public ResponseEntity<List<EcmExtractedField>> getExtractedFields(@PathVariable Long id) {
        EcmFile ecmFile = ecmFileService.getFileById(id);
        if (ecmFile == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ecmFileService.getExtractedFields(id));
    }

    @GetMapping("/spreadsheet/{id}")
    public ResponseEntity<?> getSpreadsheetHtml(
            @PathVariable Long id,
            @RequestParam(value = "sheet", required = false) String sheetName) {

        EcmFile ecmFile = ecmFileService.getFileById(id);
        if (ecmFile == null) return ResponseEntity.notFound().build();

        String lower = ecmFile.getFileName().toLowerCase();
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls") && !lower.endsWith(".csv")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Not a spreadsheet file"));
        }

        Path filePath = Paths.get(ecmFile.getFilePath().replace("/", java.io.File.separator));
        if (!Files.exists(filePath)) return ResponseEntity.notFound().build();

        try (Workbook workbook = WorkbookFactory.create(filePath.toFile())) {

            List<String> sheetNames = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                sheetNames.add(workbook.getSheetName(i));
            }

            Sheet sheet = (sheetName != null && !sheetName.isBlank())
                    ? workbook.getSheet(sheetName)
                    : workbook.getSheetAt(0);
            if (sheet == null) sheet = workbook.getSheetAt(0);

            String activeSheetName = sheet.getSheetName();
            String html = convertSheetToHtml(sheet);
            List<List<String>> rows = extractSheetRows(sheet);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sheets", sheetNames);
            result.put("activeSheet", activeSheetName);
            result.put("html", html);
            result.put("rows", rows);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to parse spreadsheet: {}", ecmFile.getFilePath(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to parse spreadsheet"));
        }
    }

    @SuppressWarnings("unchecked")
    @PutMapping("/spreadsheet/{id}")
    public ResponseEntity<?> updateSpreadsheet(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {

        EcmFile ecmFile = ecmFileService.getFileById(id);
        if (ecmFile == null) return ResponseEntity.notFound().build();

        String lower = ecmFile.getFileName().toLowerCase();
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Not a spreadsheet file"));
        }

        Path filePath = Paths.get(ecmFile.getFilePath().replace("/", java.io.File.separator));
        if (!Files.exists(filePath)) return ResponseEntity.notFound().build();

        List<List<String>> rows = (List<List<String>>) request.get("rows");
        String sheetName = (String) request.get("sheet");
        String editedBy = (String) request.get("editedBy");
        if (rows == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "rows is required"));
        }

        try {
            Workbook workbook;
            try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
                workbook = WorkbookFactory.create(fis);
            }
            Sheet sheet = (sheetName != null && !sheetName.isBlank())
                    ? workbook.getSheet(sheetName)
                    : workbook.getSheetAt(0);
            if (sheet == null) sheet = workbook.getSheetAt(0);

            for (int r = 0; r < rows.size(); r++) {
                List<String> cellValues = rows.get(r);
                Row row = sheet.getRow(r);
                if (row == null) row = sheet.createRow(r);
                for (int c = 0; c < cellValues.size(); c++) {
                    Cell cell = row.getCell(c);
                    if (cell == null) cell = row.createCell(c);
                    String val = cellValues.get(c);
                    if (val == null) val = "";
                    try {
                        double num = Double.parseDouble(val);
                        cell.setCellValue(num);
                    } catch (NumberFormatException e) {
                        cell.setCellValue(val);
                    }
                }
            }

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                workbook.write(fos);
            }

            workbook.close();
            ecmFile.setFileSize(Files.size(filePath));
            ecmFileService.saveFile(ecmFile);
            log.info("Spreadsheet updated by {}: {} (id={})", editedBy, ecmFile.getFileName(), id);
            return ResponseEntity.ok(Map.of("message", "Spreadsheet saved"));
        } catch (Exception e) {
            log.error("Failed to update spreadsheet: {}", ecmFile.getFilePath(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to save spreadsheet"));
        }
    }

    private String convertSheetToHtml(Sheet sheet) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table>");

        int lastRow = Math.min(sheet.getLastRowNum(), 999);
        Set<String> mergedCells = new HashSet<>();
        Map<String, int[]> mergeSpans = new HashMap<>();

        for (CellRangeAddress range : sheet.getMergedRegions()) {
            for (int r = range.getFirstRow(); r <= range.getLastRow(); r++) {
                for (int c = range.getFirstColumn(); c <= range.getLastColumn(); c++) {
                    if (r == range.getFirstRow() && c == range.getFirstColumn()) {
                        mergeSpans.put(r + ":" + c, new int[]{
                                range.getLastRow() - range.getFirstRow() + 1,
                                range.getLastColumn() - range.getFirstColumn() + 1
                        });
                    } else {
                        mergedCells.add(r + ":" + c);
                    }
                }
            }
        }

        for (int r = 0; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            sb.append("<tr>");
            int lastCol = row != null ? row.getLastCellNum() : 0;
            for (int c = 0; c < lastCol; c++) {
                String key = r + ":" + c;
                if (mergedCells.contains(key)) continue;

                String tag = r == 0 ? "th" : "td";
                int[] span = mergeSpans.get(key);
                sb.append("<").append(tag);
                if (span != null) {
                    if (span[0] > 1) sb.append(" rowspan=\"").append(span[0]).append("\"");
                    if (span[1] > 1) sb.append(" colspan=\"").append(span[1]).append("\"");
                }
                sb.append(">");

                if (row != null) {
                    Cell cell = row.getCell(c);
                    if (cell != null) {
                        sb.append(escapeHtml(getCellValue(cell)));
                    }
                }
                sb.append("</").append(tag).append(">");
            }
            sb.append("</tr>");
        }

        sb.append("</table>");
        return sb.toString();
    }

    private List<List<String>> extractSheetRows(Sheet sheet) {
        int lastRow = Math.min(sheet.getLastRowNum(), 999);
        int maxCol = 0;
        for (int r = 0; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row != null) maxCol = Math.max(maxCol, row.getLastCellNum());
        }
        List<List<String>> rows = new ArrayList<>();
        for (int r = 0; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            List<String> cells = new ArrayList<>();
            for (int c = 0; c < maxCol; c++) {
                if (row != null) {
                    Cell cell = row.getCell(c);
                    cells.add(cell != null ? getCellValue(cell) : "");
                } else {
                    cells.add("");
                }
            }
            rows.add(cells);
        }
        return rows;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : formatNumber(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield formatNumber(cell.getNumericCellValue());
                } catch (Exception e) {
                    try {
                        yield cell.getStringCellValue();
                    } catch (Exception e2) {
                        yield cell.getCellFormula();
                    }
                }
            }
            default -> "";
        };
    }

    private String formatNumber(double val) {
        if (val == Math.floor(val) && !Double.isInfinite(val)) {
            return String.valueOf((long) val);
        }
        return String.format("%.2f", val);
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @GetMapping("/preview/{id}")
    public ResponseEntity<Resource> previewFile(
            @PathVariable Long id,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {

        EcmFile ecmFile = ecmFileService.getFileById(id);
        if (ecmFile == null) {
            return ResponseEntity.notFound().build();
        }

        Path filePath = Paths.get(ecmFile.getFilePath().replace("/", java.io.File.separator));
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        try {
            long fileLength = Files.size(filePath);
            String mimeType = detectMimeType(ecmFile.getFileName());
            Resource resource = new FileSystemResource(filePath);

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String[] ranges = rangeHeader.substring(6).split("-");
                long start = Long.parseLong(ranges[0]);
                long end = ranges.length > 1 && !ranges[1].isEmpty()
                        ? Long.parseLong(ranges[1])
                        : Math.min(start + 1024 * 1024 - 1, fileLength - 1);

                if (end >= fileLength) end = fileLength - 1;
                long contentLength = end - start + 1;

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType(mimeType));
                headers.set("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
                headers.setContentLength(contentLength);
                headers.set("Accept-Ranges", "bytes");
                headers.set("Cache-Control", "no-cache");

                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .headers(headers)
                        .body(new RangeResource(resource, start, contentLength));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(mimeType));
            headers.setContentLength(fileLength);
            headers.set("Accept-Ranges", "bytes");
            headers.set("Content-Disposition", "inline; filename=\"" + ecmFile.getFileName() + "\"");

            return ResponseEntity.ok().headers(headers).body(resource);

        } catch (IOException e) {
            log.error("Error serving file preview: {}", ecmFile.getFilePath(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/content/{id}")
    public ResponseEntity<String> getFileContent(@PathVariable Long id) {
        EcmFile ecmFile = ecmFileService.getFileById(id);
        if (ecmFile == null) return ResponseEntity.notFound().build();

        Path filePath = Paths.get(ecmFile.getFilePath().replace("/", java.io.File.separator));
        if (!Files.exists(filePath)) return ResponseEntity.notFound().build();

        try {
            String content = Files.readString(filePath);
            return ResponseEntity.ok(content);
        } catch (IOException e) {
            log.error("Failed to read file content: {}", ecmFile.getFilePath(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/content/{id}")
    public ResponseEntity<?> updateFileContent(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String content = request.get("content");
        String editedBy = request.get("editedBy");
        if (content == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "content is required"));
        }

        EcmFile ecmFile = ecmFileService.getFileById(id);
        if (ecmFile == null) return ResponseEntity.notFound().build();

        try {
            Path filePath = Paths.get(ecmFile.getFilePath().replace("/", java.io.File.separator));
            Files.writeString(filePath, content);
            ecmFile.setFileSize(Files.size(filePath));
            ecmFileService.saveFile(ecmFile);
            log.info("File content updated by {}: {} (id={})", editedBy, ecmFile.getFileName(), id);
            return ResponseEntity.ok(Map.of("message", "File updated"));
        } catch (IOException e) {
            log.error("Failed to update file content: {}", ecmFile.getFilePath(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to save file"));
        }
    }

    @PutMapping("/files/{id}/replace")
    public ResponseEntity<?> replaceFile(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "editedBy", required = false, defaultValue = "Unknown") String editedBy) {

        EcmFile ecmFile = ecmFileService.getFileById(id);
        if (ecmFile == null) return ResponseEntity.notFound().build();

        try {
            Path filePath = Paths.get(ecmFile.getFilePath().replace("/", java.io.File.separator));
            try (var is = file.getInputStream()) {
                Files.copy(is, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            ecmFile.setFileSize(file.getSize());
            ecmFileService.saveFile(ecmFile);
            log.info("File replaced by {}: {} (id={})", editedBy, ecmFile.getFileName(), id);
            return ResponseEntity.ok(ecmFile);
        } catch (IOException e) {
            log.error("Failed to replace file: {}", ecmFile.getFilePath(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to replace file"));
        }
    }

    @PostMapping("/folder")
    public ResponseEntity<?> createFolder(@RequestBody Map<String, String> request) {
        String folderPath = request.get("folderPath");
        if (folderPath == null || folderPath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "folderPath is required"));
        }
        try {
            ecmFileService.ensureFolder(folderPath);
            return ResponseEntity.ok(Map.of("message", "Folder created", "path", folderPath));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to create folder: " + e.getMessage()));
        }
    }

    private String detectMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".avi")) return "video/x-msvideo";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".aac")) return "audio/aac";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml")) return "application/xml";
        return "application/octet-stream";
    }

    private static class RangeResource extends FileSystemResource {
        private final Resource delegate;
        private final long start;
        private final long length;

        public RangeResource(Resource delegate, long start, long length) {
            super(((FileSystemResource) delegate).getPath());
            this.delegate = delegate;
            this.start = start;
            this.length = length;
        }

        @Override
        public java.io.InputStream getInputStream() throws IOException {
            java.io.InputStream is = delegate.getInputStream();
            is.skip(start);
            return new java.io.FilterInputStream(is) {
                private long remaining = length;

                @Override
                public int read() throws IOException {
                    if (remaining <= 0) return -1;
                    int b = super.read();
                    if (b != -1) remaining--;
                    return b;
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    if (remaining <= 0) return -1;
                    int toRead = (int) Math.min(len, remaining);
                    int n = super.read(b, off, toRead);
                    if (n > 0) remaining -= n;
                    return n;
                }
            };
        }

        @Override
        public long contentLength() {
            return length;
        }
    }
}
