package com.hrms.cms.service;

import com.hrms.cms.entity.AcknowledgementLetterTemplate;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ClosureClauseMaster;
import com.hrms.cms.repository.AcknowledgementLetterTemplateRepository;
import com.hrms.cms.repository.ClosureClauseMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Renders the citizen's acknowledgement and closure letters as PDFs.
 *
 * <p>Generated on demand rather than stored: the letter is a view of the complaint row, so persisting a
 * copy would mean a second source of truth that drifts whenever the complaint is updated.
 *
 * <p>The acknowledgement letter is department-templated (RBIO vs CEPC, via
 * {@link AcknowledgementLetterTemplateRepository}) and bilingual — an English page followed by a Hindi
 * page in the same PDF. The closure letter has no approved bilingual template to seed (unlike the
 * acknowledgement letter, nothing in the BRD specifies closure-letter body prose), so it stays a single
 * English template, but shares the same rendering pipeline — emblem, real-glyph word wrap, Devanagari-safe
 * font for citizen data — so a complainant name/address in Hindi no longer vanishes, and resolves the
 * closure clause to its human-readable label instead of printing the bare code.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplaintLetterPdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private static final float MARGIN = 56f;
    private static final float WIDTH = PDRectangle.A4.getWidth() - (2 * MARGIN);

    private static final String EMBLEM_RESOURCE = "static/letters/RBI_New_Logo.png";
    private static final String DEVANAGARI_FONT_RESOURCE = "fonts/NotoSansDevanagari-Regular.ttf";
    private static final float EMBLEM_SIZE = 46f;

    private final AcknowledgementLetterTemplateRepository templateRepository;
    private final AcknowledgementLetterDataService dataService;
    private final TemplateVariableRenderer variableRenderer;
    private final ClosureClauseMasterRepository closureClauseRepository;

    @Value("${cms.eligibility.scheme-version:RBIOS_2021}")
    private String defaultSchemeVersion;

    /** Logged once per JVM run rather than once per download, so a missing font doesn't spam logs. */
    private static final AtomicBoolean DEVANAGARI_WARNING_LOGGED = new AtomicBoolean(false);

    public byte[] render(Complaint c, String type) {
        if ("acknowledgement".equals(type)) {
            return renderAcknowledgement(c);
        }
        return renderClosureLegacy(c);
    }

    // ══════════════════════════════════════════════════════════════════
    // Acknowledgement — bilingual, department-templated
    // ══════════════════════════════════════════════════════════════════

    private byte[] renderAcknowledgement(Complaint c) {
        String department = "CEPC".equalsIgnoreCase(c.getDepartment()) ? "CEPC" : "RBIO";
        Map<String, String> vars = dataService.buildVariables(c);

        String englishBody = resolveTemplate(department, "EN")
                .map(t -> variableRenderer.render(t.getBodyTemplate(), vars))
                .orElseGet(() -> fallbackAcknowledgementText(c));
        String hindiBody = resolveTemplate(department, "HI")
                .map(t -> variableRenderer.render(t.getBodyTemplate(), vars))
                .orElse(null);

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDImageXObject emblem = loadEmblem(doc);

            PDFont englishNormal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont englishBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            renderLetterPage(doc, parseLines(englishBody), englishNormal, englishBold, emblem, true);

            if (hindiBody != null) {
                PDFont devanagari = loadDevanagariFont(doc);
                if (devanagari != null) {
                    renderLetterPage(doc, parseLines(hindiBody), devanagari, devanagari, emblem, true);
                } else if (DEVANAGARI_WARNING_LOGGED.compareAndSet(false, true)) {
                    log.warn("Devanagari font resource '{}' not found on classpath — Hindi acknowledgement "
                            + "page is omitted until the font is added. (complaint {})",
                            DEVANAGARI_FONT_RESOURCE, c.getComplaintNumber());
                }
            }

            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render acknowledgement letter PDF", e);
        }
    }

    private Optional<AcknowledgementLetterTemplate> resolveTemplate(String department, String language) {
        return templateRepository.findFirstByDepartmentAndLanguageAndActiveTrueOrderByVersionDesc(department, language);
    }

    /** Used only if the DB has no active template row yet (e.g. before the seeder has run). */
    private String fallbackAcknowledgementText(Complaint c) {
        return "Acknowledgement: Registration of Complaint - " + nvl(c.getComplaintNumber())
                + "\n\nDear " + nvl(c.getComplainantName()) + ",\n\n"
                + "We acknowledge receipt of your complaint against " + nvl(c.getEntityName())
                + ". It has been registered with us with complaint number " + nvl(c.getComplaintNumber()) + ".";
    }

    private PDImageXObject loadEmblem(PDDocument doc) {
        try (InputStream in = new ClassPathResource(EMBLEM_RESOURCE).getInputStream()) {
            return PDImageXObject.createFromByteArray(doc, in.readAllBytes(), "rbi-emblem");
        } catch (IOException e) {
            log.warn("Letter emblem resource '{}' not found — rendering without it", EMBLEM_RESOURCE);
            return null;
        }
    }

    private PDFont loadDevanagariFont(PDDocument doc) {
        ClassPathResource resource = new ClassPathResource(DEVANAGARI_FONT_RESOURCE);
        if (!resource.exists()) return null;
        try (InputStream in = resource.getInputStream()) {
            return PDType0Font.load(doc, in);
        } catch (IOException e) {
            log.warn("Failed to load Devanagari font '{}'", DEVANAGARI_FONT_RESOURCE, e);
            return null;
        }
    }

    /**
     * Template line prefixes, so an admin editing a stored template can control layout without a code
     * change: {@code >>} centers and bolds a line (the letterhead title), {@code ##} bolds a line in
     * place (the subject line), a blank line is a paragraph gap, anything else is a normal paragraph.
     */
    private List<LetterLine> parseLines(String body) {
        List<LetterLine> lines = new ArrayList<>();
        for (String raw : body.split("\n", -1)) {
            String line = raw.stripTrailing();
            if (line.isBlank()) {
                lines.add(LetterLine.gap());
            } else if (line.startsWith(">> ")) {
                lines.add(new LetterLine(line.substring(3).trim(), 13, true, true));
            } else if (line.startsWith("## ")) {
                lines.add(new LetterLine(line.substring(3).trim(), 11, true, false));
            } else {
                lines.add(new LetterLine(line.trim(), 11, false, false));
            }
        }
        return lines;
    }

    /**
     * Lays out one language's letter body onto fresh page(s) of {@code doc}, starting a brand-new page
     * so the English and Hindi halves never share a page even if the first one has room left — the
     * screenshots this mirrors show them as two distinct pages.
     */
    private void renderLetterPage(PDDocument doc, List<LetterLine> lines, PDFont normalFont, PDFont boldFont,
                                   PDImageXObject emblem, boolean withEmblem) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        PDPageContentStream cs = new PDPageContentStream(doc, page);
        float pageWidth = PDRectangle.A4.getWidth();
        float y = PDRectangle.A4.getHeight() - MARGIN;

        if (withEmblem && emblem != null) {
            float x = (pageWidth - EMBLEM_SIZE) / 2f;
            cs.drawImage(emblem, x, y - EMBLEM_SIZE, EMBLEM_SIZE, EMBLEM_SIZE);
            y -= EMBLEM_SIZE + 12;
        }

        for (LetterLine line : lines) {
            if (line.text() == null) {
                y -= 10;
                continue;
            }
            PDFont font = line.bold() ? boldFont : normalFont;

            for (String chunk : wrap(font, line.text(), line.size())) {
                if (y < MARGIN + line.size()) {
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = PDRectangle.A4.getHeight() - MARGIN;
                }
                float x = line.centered() ? centeredX(font, chunk, line.size(), pageWidth) : MARGIN;
                cs.beginText();
                cs.setFont(font, line.size());
                cs.newLineAtOffset(x, y);
                cs.showText(chunk);
                cs.endText();
                y -= line.size() + 5;
            }
        }

        cs.close();
    }

    private float centeredX(PDFont font, String text, float size, float pageWidth) throws IOException {
        float textWidth = font.getStringWidth(text) / 1000f * size;
        return (pageWidth - textWidth) / 2f;
    }

    /** Word-wraps using the font's real glyph widths, so this works for both Latin and Devanagari text. */
    private List<String> wrap(PDFont font, String text, float size) throws IOException {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (word.isEmpty()) continue;
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (font.getStringWidth(candidate) / 1000f * size > WIDTH && current.length() > 0) {
                out.add(current.toString());
                current.setLength(0);
                current.append(word);
            } else {
                current.setLength(0);
                current.append(candidate);
            }
        }
        if (current.length() > 0) out.add(current.toString());
        return out.isEmpty() ? List.of("") : out;
    }

    private record LetterLine(String text, float size, boolean bold, boolean centered) {
        static LetterLine gap() {
            return new LetterLine(null, 0, false, false);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Closure — same rendering pipeline as the acknowledgement letter (emblem, Devanagari-safe
    // font, real-glyph word wrap), department-aware heading, resolved clause label. English only:
    // unlike the acknowledgement letter, no approved Hindi closure-letter body exists to seed.
    // ══════════════════════════════════════════════════════════════════

    private byte[] renderClosureLegacy(Complaint c) {
        String department = "CEPC".equalsIgnoreCase(c.getDepartment()) ? "CEPC" : "RBIO";
        String cell = "CEPC".equals(department)
                ? "Consumer Education and Protection Cell"
                : "The Office of RBI Ombudsman";

        List<LetterLine> lines = new ArrayList<>();
        lines.add(new LetterLine(cell, 13, true, true));
        lines.add(new LetterLine("Reserve Bank of India", 13, true, true));
        lines.add(LetterLine.gap());
        lines.add(new LetterLine("Complaint Closure Letter", 11, true, false));
        lines.add(LetterLine.gap());

        lines.add(new LetterLine("Complaint Number: " + nvl(c.getComplaintNumber() != null
                ? c.getComplaintNumber() : c.getCaseId()), 11, false, false));
        lines.add(new LetterLine("Complainant: " + nvl(c.getComplainantName()), 11, false, false));
        if (c.getCreatedAt() != null) {
            lines.add(new LetterLine("Date of Registration: " + c.getCreatedAt().format(DATE), 11, false, false));
        }
        lines.add(new LetterLine("Regulated Entity: " + nvl(c.getEntityName()), 11, false, false));
        lines.add(LetterLine.gap());

        if (c.getClosedAt() != null) {
            lines.add(new LetterLine("Date of Closure: " + c.getClosedAt().format(DATE), 11, false, false));
        }
        lines.add(new LetterLine("Closure Clause: " + resolveClauseLabel(c), 11, false, false));
        lines.add(LetterLine.gap());

        lines.add(new LetterLine("Dear " + nvl(c.getComplainantName()) + ",", 11, false, false));
        lines.add(LetterLine.gap());
        lines.add(new LetterLine("Your complaint referenced above has been examined and is now closed under the "
                + "Reserve Bank - Integrated Ombudsman Scheme. The closure clause applicable to your "
                + "complaint is recorded above.", 11, false, false));
        lines.add(LetterLine.gap());
        lines.add(new LetterLine("If you are dissatisfied with this decision, you may file an appeal through the "
                + "portal within 30 days of the date of this letter.", 11, false, false));

        lines.add(LetterLine.gap());
        lines.add(new LetterLine("Subject: " + nvl(c.getSubject()), 11, false, false));
        if (c.getDescription() != null && !c.getDescription().isBlank()) {
            lines.add(new LetterLine("Particulars: " + c.getDescription(), 11, false, false));
        }
        lines.add(LetterLine.gap());
        lines.add(new LetterLine("This is a system-generated letter and does not require a signature.", 9, false, false));

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDImageXObject emblem = loadEmblem(doc);
            PDFont[] fonts = resolveClosureFonts(doc, lines);
            renderLetterPage(doc, lines, fonts[0], fonts[1], emblem, true);
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render closure letter PDF", e);
        }
    }

    /**
     * {normal, bold} fonts for the closure letter. Devanagari-capable only if the body actually
     * contains non-Latin-1 text (a citizen name or address in Hindi) and the font resource is
     * present — otherwise Helvetica, so the common case stays on a crisp standard font rather than
     * every closure letter paying the embedded-font cost. The Devanagari font has no separate bold
     * variant, so both slots get the same instance, mirroring the acknowledgement letter's Hindi page.
     */
    private PDFont[] resolveClosureFonts(PDDocument doc, List<LetterLine> lines) throws IOException {
        boolean needsDevanagari = lines.stream()
                .map(LetterLine::text)
                .filter(java.util.Objects::nonNull)
                .anyMatch(this::hasNonLatin1);
        if (needsDevanagari) {
            PDFont devanagari = loadDevanagariFont(doc);
            if (devanagari != null) {
                return new PDFont[]{devanagari, devanagari};
            }
            if (DEVANAGARI_WARNING_LOGGED.compareAndSet(false, true)) {
                log.warn("Devanagari font resource '{}' not found on classpath — non-Latin characters in "
                        + "the closure letter will not render correctly.", DEVANAGARI_FONT_RESOURCE);
            }
        }
        return new PDFont[]{
                new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        };
    }

    private boolean hasNonLatin1(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 255) return true;
        }
        return false;
    }

    /** The clause's human-readable label, falling back to the bare code if unresolved. */
    private String resolveClauseLabel(Complaint c) {
        String code = c.getClosureClause();
        if (code == null || code.isBlank()) {
            return "-";
        }
        String scheme = c.getSchemeVersion() != null && !c.getSchemeVersion().isBlank()
                ? c.getSchemeVersion() : defaultSchemeVersion;
        return closureClauseRepository.findBySchemeVersionAndClauseCode(scheme, code.trim())
                .map(ClosureClauseMaster::getLabel)
                .orElse(code);
    }

    private String nvl(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
