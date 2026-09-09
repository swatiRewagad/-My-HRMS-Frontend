FROM python:3.11-slim

# Debian's default mirror is plain http:// (port 80), which some corporate
# networks block while allowing https. Force https to avoid apt-get hanging.
RUN sed -i 's|http://deb.debian.org|https://deb.debian.org|g' /etc/apt/sources.list.d/*.sources 2>/dev/null || true

# System deps: poppler for PDF rendering, libGL for OpenCV used by PaddleOCR
RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0 libsm6 libxrender1 libxext6 libgl1 \
    poppler-utils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Pre-download PaddleOCR models at build time so the container starts fast
RUN python -c "from paddleocr import PaddleOCR; PaddleOCR(use_angle_cls=True, lang='en', show_log=False)"

COPY main.py .

EXPOSE 8100

HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8100/health')"

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8100", "--workers", "2"]
