# DICOM Compression Service (DCMTK Wrapper)

A high-performance **Spring Boot–based REST API** for performing **JPEG-LS Lossless compression** on medical DICOM images.  
This project follows a **Hybrid Wrapper Architecture**, combining Java’s portability and file system management with the optimized native C++ compression engine provided by **DCMTK**.

The objective is to significantly reduce storage usage **without any diagnostic image quality loss**, making it suitable for clinical and archival workflows.

---

## 🚀 How It Works

The compression workflow consists of the following steps:

1. **API Request**  
   The client sends a POST request with the directory path containing `.dcm` files to be compressed and a specific Job Code.

2. **Directory Scanning**  
   The Java service scans the provided folder to locate DICOM files (e.g., ending with `.dcm`, `.dicom`, or without extensions).

3. **Native Tool Invocation**  
   For each DICOM file found, Java uses `ProcessBuilder` to invoke DCMTK’s native **JPEG-LS compressor** (`dcmcjpls.exe` on Windows, or `dcmcjpls` on Linux/Mac) from the local `tools/` directory.

4. **Lossless Compression**  
   The tool applies **JPEG-LS Lossless compression** which provides:
   - ✔ Faster compression speed  
   - ✔ Lower CPU usage  
   - ✔ Better compression ratio than classic JPEG Lossless
   Typical size reduction: **40–70% (varies by modality)**

5. **Archiving & File Replacement**  
   The original uncompressed DICOM files are safely moved to an `archive` folder created in the application's working directory, while the compressed `.dcm` files replace the originals in the source directory.

---

## 🛠️ Technology Stack

- **Java 17** (Amazon Corretto / OpenJDK)
- **Spring Boot 3.4.1**
- **DCMTK 3.7.0**
- **Maven**

---

## ⚙️ Prerequisites

### Required

- **Java 17**
- **Windows OS (64-bit)** (or Linux/Mac with DCMTK built/available)
- **Microsoft Visual C++ Redistributable (x64)** (for Windows)
- **`tools/` directory** located in the project's working directory containing the `dcmcjpls` executable.

---

## 🏃 Getting Started

### 1️⃣ Clone Repository

```bash
git clone https://github.com/SushantAhuja1/dicom-compressor.git  
cd dicom-compressor
```

---

### 2️⃣ Build Executable JAR

```bash
mvn clean install
```

---

### 3️⃣ Run Application

```bash
java -jar target/compressor-0.0.1-SNAPSHOT.jar
```
By default, the server starts on port `8080`.

---

## 📡 API Endpoints

### Compress DICOM Files

**Endpoint:** `POST /api/dicom/compress`

Instructs the server to compress DICOM files in the specified local folder.

**Request Body:**
```json
{
  "folderPath": "C:\\path\\to\\dicom\\folder",
  "jcode": "JOB-12345"
}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Compression completed successfully",
  "jcode": "JOB-12345",
  "processedCount": 10
}
```

**Error Response (400 Bad Request / 500 Internal Server Error):**
```json
{
  "success": false,
  "message": "Invalid folder path: C:\\invalid\\path",
  "jcode": "JOB-12345",
  "processedCount": 0
}
```

---

Open compressed files using:

- RadiAnt DICOM Viewer  
- MicroDicom  
- Horos  
