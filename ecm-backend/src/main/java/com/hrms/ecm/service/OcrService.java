package com.hrms.ecm.service;

import com.hrms.ecm.entity.FileEntity;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
public class OcrService {

    public String extractText(FileEntity file) throws IOException {
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        String name = file.getOriginalName() != null ? file.getOriginalName().toLowerCase() : "";

        if (contentType.contains("pdf")) {
            return extractFromPdf(file.getStoragePath());
        } else if (contentType.contains("image") || name.matches(".*\\.(png|jpg|jpeg|tiff|bmp|gif)$")) {
            return extractFromImage(file.getStoragePath());
        } else {
            throw new RuntimeException("OCR not supported for file type: " + contentType);
        }
    }

    private String extractFromPdf(String storagePath) throws IOException {
        Path path = Paths.get(storagePath);
        try (PDDocument doc = Loader.loadPDF(Files.readAllBytes(path))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String embeddedText = stripper.getText(doc);

            if (embeddedText != null && embeddedText.trim().length() > 50) {
                return embeddedText;
            }

            return ocrPdfPages(doc);
        }
    }

    private String ocrPdfPages(PDDocument doc) throws IOException {
        StringBuilder ocrText = new StringBuilder();
        Tesseract tesseract = buildTesseract();

        try {
            PDFRenderer renderer = new PDFRenderer(doc);
            int maxPages = Math.min(doc.getNumberOfPages(), 20);
            for (int i = 0; i < maxPages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 300, ImageType.GRAY);
                String pageText = tesseract.doOCR(image);
                if (pageText != null && !pageText.isBlank()) {
                    ocrText.append("--- Page ").append(i + 1).append(" ---\n");
                    ocrText.append(pageText).append("\n");
                }
            }
        } catch (TesseractException e) {
            log.error("OCR failed: {}", e.getMessage());
            throw new RuntimeException("OCR processing failed: " + e.getMessage());
        }

        return ocrText.toString();
    }

    private String extractFromImage(String storagePath) throws IOException {
        Tesseract tesseract = buildTesseract();
        File imageFile = new File(storagePath);

        try {
            return tesseract.doOCR(imageFile);
        } catch (TesseractException e) {
            log.error("OCR failed for image: {}", e.getMessage());
            throw new RuntimeException("OCR processing failed: " + e.getMessage());
        }
    }

    private Tesseract buildTesseract() {
        Tesseract tesseract = new Tesseract();
        tesseract.setOcrEngineMode(1);
        tesseract.setPageSegMode(3);
        tesseract.setLanguage("eng");
        tesseract.setVariable("user_defined_dpi", "300");
        tesseract.setVariable("preserve_interword_spaces", "1");

        String tempDir = System.getProperty("java.io.tmpdir");
        File tessdataDir = new File(tempDir, "tess4j-data");
        if (!tessdataDir.exists()) {
            tessdataDir.mkdirs();
        }
        File engData = new File(tessdataDir, "eng.traineddata");
        if (!engData.exists() || engData.length() < 100_000) {
            try (var is = getClass().getClassLoader().getResourceAsStream("tessdata/eng.traineddata")) {
                if (is != null) {
                    Files.copy(is, engData.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    log.info("Extracted eng.traineddata to {}", engData.getAbsolutePath());
                }
            } catch (IOException e) {
                log.warn("Could not extract tessdata: {}", e.getMessage());
            }
        }
        tesseract.setDatapath(tessdataDir.getAbsolutePath());
        return tesseract;
    }
}
