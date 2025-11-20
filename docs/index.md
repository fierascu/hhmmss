# hhmmss - Timesheet Converter

A Spring Boot web application that converts Excel timesheets to Word documents (DOCX format). What started as an experiment with AI code generation has evolved into a fully-featured, production-ready, and security-hardened timesheet conversion tool.

## Overview

This application provides a simple web interface for uploading Excel timesheets and automatically generating formatted Word documents. It supports both single file conversion and batch processing of multiple timesheets via ZIP files, with robust security features and production-ready deployment options.

## Recent Updates

### Security Enhancements (November 2025)
- **Fixed File Ownership Vulnerabilities**: Resolved security issues related to file ownership and permissions (PR #50)
- **Fixed UUID Predictability**: Addressed CWE-330 vulnerability by implementing cryptographically secure UUID generation (PR #49)
- **Comprehensive Security Audits**: Multiple security audit reports conducted with context-aware analysis (PR #48, #52)
- **Mobile Download Fix**: Corrected file extension handling for mobile browsers (PR #51)

### Deployment & Infrastructure
- **Fly.io Support**: Full deployment configuration with auto-scaling and HTTPS support (PR #47)
- **Docker Optimization**: Multi-stage builds with LibreOffice integration
- **Health Monitoring**: Spring Boot Actuator endpoints for production monitoring

### Feature Improvements
- **Long Fridays Feature**: Enhanced timesheet calculations for extended workdays (PR #45)
- **Contract Metrics**: Calculate time spent and savings per contract
- **Intelligent File Cleanup**: Skip generated templates in scheduled cleanup jobs (PR #46)
- **Enhanced Logging**: Improved privacy and information disclosure controls

## Features

### Core Functionality
- **Excel to Word Conversion**: Upload Excel timesheets (.xls, .xlsx, .xlsm, .xlsb) and convert them to formatted DOCX files
- **PDF Export**: Convert Excel and Word documents to PDF format using LibreOffice in headless mode
- **Batch Processing**: Process multiple Excel files at once by uploading a ZIP archive
- **Smart File Validation**: Comprehensive file type validation (extension, MIME type, file signature)
- **Security Features**:
  - Executable file detection and blocking
  - Cryptographically secure file naming
  - Multi-layer file validation
  - Secure temporary file handling
- **Throttling & Rate Limiting**: Semaphore-based concurrency control to prevent resource exhaustion
- **Automatic File Cleanup**: Scheduled daily cleanup of temporary files with configurable retention period

### User Interface
- **Multiple UI Themes**: Choose from ASCII art, Classic, or Terminal themes
- **ASCII Loading Animation**: Retro-style animated spinner during file processing
- **Modern Responsive Design**: Clean, Excel-themed UI built with Thymeleaf templates
- **Real-time File Validation**: Upload button activates only when valid files are selected
- **Error Handling**: User-friendly error messages displayed inline
- **Mobile-Friendly**: Optimized for both desktop and mobile browsers

### File Processing
- **ZIP Archive Support**: Upload a ZIP file containing multiple Excel timesheets for batch conversion
- **Detailed Results**: View lists of successfully processed and failed files for batch operations
- **Temporary Storage**: Secure file handling with cryptographically random naming
- **Template-based Generation**: Uses a Word template for consistent output formatting

## Technology Stack

- **Framework**: Spring Boot 3.x
- **Language**: Java 21
- **Template Engine**: Thymeleaf
- **Build Tool**: Maven
- **File Processing**: Apache POI (Excel), Apache POI-OOXML (Word)
- **PDF Conversion**: JODConverter with LibreOffice
- **Security**: Cryptographically secure random generation, multi-layer validation
- **Logging**: SLF4J with Lombok
- **Testing**: JUnit 5, Spring Boot Test
- **Concurrency**: Java Semaphores for request throttling
- **Deployment**: Docker, Fly.io

## Getting Started

### Quick Start with Docker (Recommended)

1. Clone the repository:
   ```bash
   git clone https://github.com/fierascu/hhmmss.git
   cd hhmmss
   ```

2. Start the application:
   ```bash
   docker-compose up -d
   ```

3. Open your browser and navigate to:
   ```
   http://localhost:8080
   ```

4. Stop the application:
   ```bash
   docker-compose down
   ```

### Running Locally

1. Ensure you have Java 21+ and Maven installed
2. Clone the repository
3. Build and run:
   ```bash
   ./mvnw spring-boot:run
   ```
4. Access the application at `http://localhost:8080`

### Deploying to Fly.io

The application includes ready-to-use Fly.io configuration:

```bash
# Install Fly CLI
curl -L https://fly.io/install.sh | sh

# Login and deploy
fly auth login
fly deploy

# Open your deployed app
fly open
```

## Security Features

### Multi-Layer Protection
- **File Type Validation**: Extension, MIME type, and magic byte verification
- **Cryptographic Security**: Uses `SecureRandom` for all random value generation
- **Executable Detection**: Blocks upload of potentially malicious files
- **Temporary Storage**: Secure file handling with time-based UUID naming
- **Request Throttling**: Prevents DoS attacks through concurrent request limiting
- **Automatic Cleanup**: Removes temporary files after configurable retention period
- **File Integrity**: SHA-256 hashing for upload verification
- **Error Sanitization**: No sensitive information exposed in error messages

### Security Audit Compliance
The application has undergone comprehensive security audits addressing:
- CWE-330: Use of Insufficiently Random Values
- CWE-22: Path Traversal
- CWE-434: Unrestricted Upload of File with Dangerous Type
- CWE-400: Uncontrolled Resource Consumption
- And more...

All identified vulnerabilities have been fixed and documented in detailed security audit reports.

## Usage

### Single File Conversion
1. Click "Select Excel Timesheet or ZIP file"
2. Choose an Excel file (.xls, .xlsx, .xlsm, .xlsb)
3. Click "Upload and Convert"
4. Watch the ASCII loading animation during processing
5. Download the generated DOCX file

### Batch Processing
1. Create a ZIP file containing multiple Excel timesheets
2. Upload the ZIP file
3. View the processing results showing successful and failed conversions
4. Download the result ZIP file containing all converted DOCX files

### UI Theme Selection
Access different themes via URL parameters:
- ASCII theme (default): `http://localhost:8080/`
- Classic theme: `http://localhost:8080/?theme=classic`
- Terminal theme: `http://localhost:8080/?theme=terminal`

## Testing

The project includes extensive unit tests covering:
- Excel parsing and validation
- Word document generation
- PDF conversion
- ZIP batch processing
- File validation and security checks
- Upload handling and throttling
- File cleanup and error handling

Run tests with:
```bash
./mvnw test
```

## Project Evolution

### From Experiment to Production

**Initial Concept**:
- Started as an AI code generation experiment using ChatGPT and IntelliJ's Junie
- Evolved from Python prototype to Java implementation
- Transformed into a production-ready application

**Security Hardening Journey**:
- Multiple security audits and fixes
- Implementation of cryptographic best practices
- Comprehensive validation and error handling
- Production-ready deployment configurations

**Recent Milestone**: Successfully deployed to Fly.io with comprehensive security features, Docker optimization, and enterprise-grade functionality.

## Contributing

This project explores the intersection of AI-assisted development and practical application building. Contributions and feedback are welcome!

## Acknowledgments

Generated with assistance from:
- ChatGPT (initial concept and Python prototype)
- IntelliJ Junie (code refinement and Java conversion)
- Claude Code (ongoing development, security hardening, and enhancement)

## License

This project is open source and available for educational and commercial use.

---

**Live Demo**: [Visit the deployed application on Fly.io](https://hhmmss.fly.dev) (if available)

**GitHub Repository**: [fierascu/hhmmss](https://github.com/fierascu/hhmmss)

**Documentation**: Full documentation available in the [README](https://github.com/fierascu/hhmmss/blob/main/README.md)

---

*Last Updated: November 2025 - Includes latest security fixes and feature enhancements*
