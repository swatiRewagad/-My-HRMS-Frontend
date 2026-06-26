tml docume# ECM Local Setup Guide

## Prerequisites

| Tool | Version | Download |
|------|---------|----------|
| Java JDK | 21+ | https://adoptium.net/temurin/releases/?version=21 |
| Node.js | 20+ | https://nodejs.org/ |
| npm | 10+ | (comes with Node.js) |
| MySQL | 8.0+ | https://dev.mysql.com/downloads/installer/ |
| Maven | 3.9+ | https://maven.apache.org/download.cgi |
| Git | latest | https://git-scm.com/downloads |

---

## Step 1: Clone the Repository

```bash
git clone https://github.com/swatiRewagad/-My-HRMS-Frontend.git
cd My-HRMS-Frontend
```

---

## Step 2: MySQL Database Setup

1. Start MySQL service
2. Login to MySQL:
   ```bash
   mysql -u root -p
   ```
3. Create the database (auto-created by Spring Boot, but you can do it manually):
   ```sql
   CREATE DATABASE IF NOT EXISTS ecm_db;
   ```
4. Verify:
   ```sql
   SHOW DATABASES;
   ```

> **Note:** Default credentials are `root` / `root`. If your MySQL password is different, set the `DB_PASSWORD` environment variable.

---

## Step 3: ECM Backend Setup

### 3.1 Navigate to backend directory
```bash
cd ecm-backend
```

### 3.2 Set environment variables (if needed)

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_PASSWORD` | `root` | MySQL root password |
| `ECM_STORAGE_PATH` | `C:/ecm-storage` | Local folder for file storage |
| `LLM_API_KEY` | (empty) | Optional: Anthropic API key for AI features |

**Windows (PowerShell):**
```powershell
$env:DB_PASSWORD = "root"
$env:ECM_STORAGE_PATH = "C:/ecm-storage"
```

**Windows (CMD):**
```cmd
set DB_PASSWORD=root
set ECM_STORAGE_PATH=C:/ecm-storage
```

### 3.3 Create storage directory
```bash
mkdir -p C:/ecm-storage
```

### 3.4 Build and Run
```bash
mvn clean install -DskipTests
mvn spring-boot:run
```

### 3.5 Verify
- Backend starts on: **http://localhost:8081**
- Health check: Open http://localhost:8081/api/files in browser (should return JSON)

---

## Step 4: ECM Frontend Setup

### 4.1 Navigate to frontend directory
```bash
cd ecm-frontend
```

### 4.2 Install dependencies
```bash
npm install
```

### 4.3 Start development server
```bash
npm start
```

### 4.4 Verify
- Frontend starts on: **http://localhost:4200**
- Open http://localhost:4200 in browser

---

## Step 5: Verify End-to-End

1. Open **http://localhost:4200** in Chrome/Edge
2. You should see the ECM file manager interface
3. Try uploading a file — it should save to `C:/ecm-storage`
4. Try creating folders, sharing links, previewing documents

---

## Configuration Summary

| Service | Port | URL |
|---------|------|-----|
| ECM Backend | 8081 | http://localhost:8081 |
| ECM Frontend | 4200 | http://localhost:4200 |
| MySQL | 3306 | localhost:3306 |

---

## Troubleshooting

### MySQL connection refused
- Ensure MySQL service is running: `net start MySQL80` (Windows)
- Check port 3306 is not blocked
- Verify credentials: `mysql -u root -proot`

### Port 8081 already in use
- Kill the process: `netstat -ano | findstr :8081` then `taskkill /F /PID <pid>`
- Or change port in `ecm-backend/src/main/resources/application.yml`

### Port 4200 already in use
- Run on different port: `ng serve --port 4201`

### npm install fails
- Clear cache: `npm cache clean --force`
- Delete node_modules and retry: `rm -rf node_modules && npm install`

### Backend build fails (Java version)
- Ensure Java 21 is installed: `java -version`
- Set JAVA_HOME to JDK 21 path

### File upload fails
- Ensure `C:/ecm-storage` directory exists and is writable
- Check backend logs for errors

---

## Quick Start (All Commands)

```bash
# Terminal 1: Backend
cd C:\Projects\My-HRMS-Frontend\ecm-backend
mvn spring-boot:run

# Terminal 2: Frontend
cd C:\Projects\My-HRMS-Frontend\ecm-frontend
npm start
```

Then open **http://localhost:4200** in your browser.
