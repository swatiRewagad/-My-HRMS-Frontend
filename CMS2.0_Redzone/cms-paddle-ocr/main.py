"""
CMS PaddleOCR Sidecar
----------------------
Accepts raw file bytes (image or PDF), runs PaddleOCR for text extraction,
then applies rule-based field extraction to produce structured complaint fields.

No external API key required — runs fully offline.

POST /ocr
  multipart/form-data: file=<bytes>, mime_type=<string>
  Response: { "success": true, "fields": {...}, "raw_text": "..." }

GET /health
  Response: { "status": "UP", "provider": "paddle" }
"""

import io
import re
import logging
import pdfplumber
from datetime import datetime
from typing import Optional

import numpy as np
from PIL import Image
from fastapi import FastAPI, File, Form, UploadFile, HTTPException
from fastapi.responses import JSONResponse
from paddleocr import PaddleOCR

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("paddle-ocr")

app = FastAPI(title="CMS PaddleOCR Sidecar", version="1.0.0")

# Initialise PaddleOCR once at startup (downloads models on first run)
ocr_engine = PaddleOCR(use_angle_cls=True, lang="en", show_log=False)
log.info("PaddleOCR engine ready")


# ── Health ─────────────────────────────────────────────────────────────────────

@app.get("/health")
def health():
    return {"status": "UP", "provider": "paddle"}


# ── Main OCR endpoint ──────────────────────────────────────────────────────────

@app.post("/ocr")
async def ocr(file: UploadFile = File(...), mime_type: str = Form("application/octet-stream")):
    file_bytes = await file.read()
    if not file_bytes:
        raise HTTPException(status_code=400, detail="Empty file")

    log.info("Received %d bytes, mime=%s", len(file_bytes), mime_type)

    try:
        raw_text = extract_text(file_bytes, mime_type)
        fields = extract_fields(raw_text)
        log.info("Extracted %d fields, raw_text=%d chars", len(fields), len(raw_text))
        return JSONResponse({"success": True, "fields": fields, "raw_text": raw_text})
    except Exception as e:
        log.exception("OCR failed")
        return JSONResponse({"success": False, "error": str(e), "fields": {}, "raw_text": ""}, status_code=200)


# ── Text extraction ────────────────────────────────────────────────────────────

def extract_text(file_bytes: bytes, mime_type: str) -> str:
    if mime_type == "application/pdf":
        text = extract_pdf_text(file_bytes)
        if len(text.strip()) > 30:
            log.info("Used pdfplumber for PDF (%d chars)", len(text))
            return text
        log.info("PDF text too short (%d chars) — falling back to PaddleOCR on rendered pages", len(text))
        return extract_pdf_via_ocr(file_bytes)
    else:
        return extract_image_text(file_bytes)


def extract_pdf_text(file_bytes: bytes) -> str:
    """Use pdfplumber to extract embedded text from a digital PDF."""
    lines = []
    with pdfplumber.open(io.BytesIO(file_bytes)) as pdf:
        for page in pdf.pages:
            text = page.extract_text()
            if text:
                lines.append(text)
    return "\n".join(lines)


def extract_pdf_via_ocr(file_bytes: bytes) -> str:
    """Convert each PDF page to an image and run PaddleOCR on it."""
    import fitz  # PyMuPDF
    doc = fitz.open(stream=file_bytes, filetype="pdf")
    all_text = []
    for page in doc:
        mat = fitz.Matrix(2.0, 2.0)  # 2× scale for better OCR accuracy
        pix = page.get_pixmap(matrix=mat)
        img = Image.frombytes("RGB", [pix.width, pix.height], pix.samples)
        page_text = run_paddle_ocr(np.array(img))
        all_text.append(page_text)
    return "\n".join(all_text)


def extract_image_text(file_bytes: bytes) -> str:
    img = Image.open(io.BytesIO(file_bytes)).convert("RGB")
    return run_paddle_ocr(np.array(img))


def run_paddle_ocr(img_array: np.ndarray) -> str:
    result = ocr_engine.ocr(img_array, cls=True)
    lines = []
    if result and result[0]:
        for line in result[0]:
            text, confidence = line[1]
            if confidence > 0.5:
                lines.append(text)
    return "\n".join(lines)


# ── Rule-based field extraction ────────────────────────────────────────────────
# Handles common Indian banking complaint letter patterns.
# No ML required — regex + line heuristics.

KNOWN_BANKS = {
    "sbi", "state bank", "hdfc", "icici", "axis", "pnb", "punjab national",
    "bank of baroda", "canara", "union bank", "kotak", "yes bank", "federal bank",
    "idbi", "indusind", "rbl", "south indian bank", "karnataka bank",
    "bank of india", "central bank", "indian bank", "uco bank",
}

STATE_CODES = {
    "andhra pradesh": "AP", "arunachal pradesh": "AR", "assam": "AS",
    "bihar": "BR", "chhattisgarh": "CG", "goa": "GA", "gujarat": "GJ",
    "haryana": "HR", "himachal pradesh": "HP", "jharkhand": "JH",
    "karnataka": "KA", "kerala": "KL", "madhya pradesh": "MP",
    "maharashtra": "MH", "manipur": "MN", "meghalaya": "ML",
    "mizoram": "MZ", "nagaland": "NL", "odisha": "OD", "punjab": "PB",
    "rajasthan": "RJ", "sikkim": "SK", "tamil nadu": "TN", "telangana": "TS",
    "tripura": "TR", "uttar pradesh": "UP", "uttarakhand": "UK",
    "west bengal": "WB", "delhi": "DL", "jammu": "JK", "kashmir": "JK",
}

CATEGORY_KEYWORDS = {
    "ATM": ["atm", "cash machine", "card swallowed", "cash withdrawal"],
    "UPI": ["upi", "phonepe", "gpay", "google pay", "paytm", "bhim"],
    "CREDIT_CARD": ["credit card", "cc bill", "card charges"],
    "LOAN": ["loan", "emi", "home loan", "personal loan", "car loan", "mortgage"],
    "DEPOSIT": ["fd", "fixed deposit", "recurring deposit", "rd", "savings account"],
    "NEFT_RTGS": ["neft", "rtgs", "imps", "wire transfer", "fund transfer"],
    "INSURANCE": ["insurance", "policy", "premium", "claim"],
}


def extract_fields(text: str) -> dict:
    if not text or not text.strip():
        return {}

    fields = {}
    lines = [l.strip() for l in text.splitlines() if l.strip()]
    lower = text.lower()

    # ── Phone ──────────────────────────────────────────────────────────────────
    phone_m = re.search(r"\b([6-9]\d{9})\b", text)
    if phone_m:
        fields["complainantPhone"] = phone_m.group(1)

    # ── Email ──────────────────────────────────────────────────────────────────
    email_m = re.search(r"\b[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}\b", text)
    if email_m:
        fields["complainantEmail"] = email_m.group(0)

    # ── Pincode ────────────────────────────────────────────────────────────────
    pin_m = re.search(r"\b([1-9]\d{5})\b", text)
    if pin_m:
        fields["complainantPincode"] = pin_m.group(1)

    # ── Amount ────────────────────────────────────────────────────────────────
    amt_m = re.search(r"(?:Rs\.?|₹|INR)\s*([\d,]+(?:\.\d{1,2})?)", text, re.IGNORECASE)
    if amt_m:
        fields["amountInvolved"] = amt_m.group(1).replace(",", "")

    # ── Dates ─────────────────────────────────────────────────────────────────
    dates = extract_dates(text)
    if len(dates) >= 1:
        fields["letterDate"] = dates[0]
    if len(dates) >= 2:
        fields["transactionDate"] = dates[1]

    # ── Name (line after "Name:", "Dear Sir", from address block) ─────────────
    name = extract_name(lines, text)
    if name:
        fields["complainantName"] = name

    # ── Subject ───────────────────────────────────────────────────────────────
    subject = extract_subject(lines)
    if subject:
        fields["subject"] = subject

    # ── Description (body text after subject/salutation) ──────────────────────
    description = extract_description(lines)
    if description:
        fields["description"] = description

    # ── Bank / entity name ────────────────────────────────────────────────────
    entity = extract_entity(lower)
    if entity:
        fields["entityName"] = entity
        fields["entityType"] = "BANK"

    # ── Category ──────────────────────────────────────────────────────────────
    category = extract_category(lower)
    if category:
        fields["category"] = category

    # ── State ─────────────────────────────────────────────────────────────────
    for state_name, code in STATE_CODES.items():
        if state_name in lower:
            fields["complainantState"] = code
            break

    return fields


def extract_dates(text: str) -> list:
    patterns = [
        r"\b(\d{1,2})[/-](\d{1,2})[/-](\d{4})\b",    # DD/MM/YYYY or DD-MM-YYYY
        r"\b(\d{4})[/-](\d{1,2})[/-](\d{1,2})\b",    # YYYY-MM-DD
        r"\b(\d{1,2})\s+(\w+)\s+(\d{4})\b",           # DD Month YYYY
    ]
    found = []
    for pat in patterns:
        for m in re.finditer(pat, text):
            try:
                g = m.groups()
                if len(g[0]) == 4:                     # YYYY-MM-DD
                    d = datetime(int(g[0]), int(g[1]), int(g[2]))
                elif g[1].isdigit():                   # DD/MM/YYYY
                    d = datetime(int(g[2]), int(g[1]), int(g[0]))
                else:                                  # DD Month YYYY
                    d = datetime.strptime(m.group(0), "%d %B %Y")
                found.append(d.strftime("%Y-%m-%d"))
            except Exception:
                pass
    return list(dict.fromkeys(found))  # deduplicate, preserve order


def extract_name(lines: list, text: str) -> Optional[str]:
    # "Name: John Doe" pattern
    for line in lines:
        m = re.match(r"(?:name|from|complainant)\s*[:\-]\s*(.+)", line, re.IGNORECASE)
        if m:
            candidate = m.group(1).strip()
            if 2 < len(candidate) < 60 and not re.search(r"\d{6}", candidate):
                return candidate

    # Signature block: last 5 lines often contain the name
    for line in lines[-5:]:
        if re.match(r"^[A-Z][a-z]+(?: [A-Z][a-z]+){1,3}$", line):
            return line
    return None


def extract_subject(lines: list) -> Optional[str]:
    for i, line in enumerate(lines):
        if re.match(r"^(?:sub(?:ject)?|re|regarding)\s*[:\-]", line, re.IGNORECASE):
            subject = re.sub(r"^(?:sub(?:ject)?|re|regarding)\s*[:\-]\s*", "", line, flags=re.IGNORECASE).strip()
            if subject:
                return subject
            # Subject might be on the next line
            if i + 1 < len(lines):
                return lines[i + 1]
    return None


def extract_description(lines: list) -> Optional[str]:
    """Body text: everything between salutation and signature."""
    body_start = 0
    for i, line in enumerate(lines):
        if re.match(r"^(?:dear|respected|to\b|sir|madam)", line, re.IGNORECASE):
            body_start = i + 1
            break

    body_end = len(lines)
    for i in range(len(lines) - 1, max(body_start, len(lines) - 8), -1):
        if re.match(r"^(?:yours?|sincerely|regards?|thanking|thank you)", lines[i], re.IGNORECASE):
            body_end = i
            break

    body = " ".join(lines[body_start:body_end]).strip()
    return body[:2000] if body else None


def extract_entity(lower: str) -> Optional[str]:
    for keyword in KNOWN_BANKS:
        if keyword in lower:
            # Find the capitalised form in the original text
            pattern = re.compile(re.escape(keyword), re.IGNORECASE)
            m = pattern.search(lower)
            if m:
                return keyword.title()
    return None


def extract_category(lower: str) -> Optional[str]:
    for category, keywords in CATEGORY_KEYWORDS.items():
        if any(kw in lower for kw in keywords):
            return category
    return "GENERAL"
