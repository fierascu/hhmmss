# hhmmss - Timesheet Converter

A Spring Boot web application that converts Excel timesheets to Word documents (DOCX format). What started as an experiment with AI code generation has evolved into a fully-featured, production-ready timesheet conversion tool.

## Overview

This application provides a simple web interface for uploading Excel timesheets and automatically generating formatted Word documents. It supports both single file conversion and batch processing of multiple timesheets via ZIP files.

## Features

### Core Functionality
- **Excel to Word Conversion**: Upload Excel timesheets (.xls, .xlsx, .xlsm, .xlsb) and convert them to formatted DOCX files
- **PDF Export**: Convert Excel and Word documents to PDF format using LibreOffice in headless mode
- **Batch Processing**: Process multiple Excel files at once by uploading a ZIP archive
- **Smart File Validation**: Comprehensive file type validation to ensure only valid Excel and ZIP files are processed
- **Security Features**: Executable file detection and blocking to prevent malicious uploads
- **Throttling & Rate Limiting**: Semaphore-based concurrency control to prevent resource exhaustion
- **Automatic File Cleanup**: Scheduled daily cleanup of temporary files with configurable retention period

### User Interface
- **Multiple UI Themes**: Choose from ASCII art, Classic, or Terminal themes for different aesthetics
- **ASCII Loading Animation**: Retro-style animated spinner during file processing
- **Modern Responsive Design**: Clean, Excel-themed UI built with Thymeleaf templates
- **Real-time File Validation**: Upload button activates only when valid files are selected
- **Error Handling**: User-friendly error messages displayed inline
- **Download Management**: Easy access to converted files with detailed processing results
- **Custom Favicon**: Excel-themed favicon for better brand recognition

### File Processing
- **ZIP Archive Support**: Upload a ZIP file containing multiple Excel timesheets for batch conversion
- **Detailed Results**: View lists of successfully processed and failed files for batch operations
- **Temporary Storage**: Secure file handling with temporary storage management
- **Template-based Generation**: Uses a Word template for consistent output formatting

### Error Handling
- **Comprehensive Validation**: File type checking at multiple levels (extension, MIME type, file signature)
- **User-friendly Messages**: Clear error messages displayed in the UI
- **Global Exception Handler**: Centralized error handling for consistent user experience
- **Graceful Degradation**: Handles edge cases like invalid files, corrupted archives, and processing failures

## Technical Stack

- **Framework**: Spring Boot 3.x
- **Template Engine**: Thymeleaf
- **Build Tool**: Maven
- **Language**: Java
- **File Processing**: Apache POI (Excel), Apache POI-OOXML (Word)
- **PDF Conversion**: JODConverter with LibreOffice
- **Logging**: SLF4J with Lombok
- **Testing**: JUnit 5, Spring Boot Test
- **Concurrency**: Java Semaphores for request throttling
- **Scheduling**: Spring Scheduled tasks for file cleanup

## Project Structure

```
src/
├── main/
│   ├── java/eu/hhmmss/app/
│   │   ├── converter/           # Core conversion logic
│   │   │   ├── DocService.java          # Word document generation
│   │   │   ├── PdfService.java          # PDF conversion via LibreOffice
│   │   │   ├── XlsService.java          # Excel parsing
│   │   │   ├── ZipProcessingService.java # Batch ZIP processing
│   │   │   └── HhmmssDto.java           # Data transfer object
│   │   ├── uploadingfiles/
│   │   │   └── storage/         # File upload and storage
│   │   │       ├── UploadController.java
│   │   │       ├── UploadService.java
│   │   │       ├── ThrottlingService.java       # Request rate limiting
│   │   │       ├── FileCleanupService.java      # Scheduled file cleanup
│   │   │       ├── GlobalExceptionHandler.java
│   │   │       └── Storage exceptions
│   │   └── util/                # Utilities
│   │       ├── FileTypeValidator.java   # File validation
│   │       ├── FileHasher.java          # File integrity hashing
│   │       └── TimeBasedUuidGenerator.java # Unique ID generation
│   └── resources/
│       ├── templates/           # Thymeleaf templates
│       │   ├── layout.html              # Unified layout with CSS themes (ascii, terminal, classic)
│       │   ├── upload.html              # Upload form
│       │   ├── footer.html              # Shared footer
│       │   └── error.html               # Error page
│       └── timesheet-template.docx     # Word template
└── test/                        # Comprehensive unit tests
```

## Getting Started

### Prerequisites

**For Local Development:**
- Java 21 or higher
- Maven 3.6+

**For Docker Deployment:**
- Docker 20.10+
- Docker Compose 2.0+ (optional, but recommended)

### Running with Docker (Recommended)

The easiest way to run the application is using Docker:

#### Quick Start with Docker Compose

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

#### Building and Running with Docker

Alternatively, you can use Docker directly:

1. Build the Docker image:
```bash
docker build -t hhmmss:latest .
```

2. Run the container:
```bash
docker run -d \
  --name hhmmss-app \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=docker \
  hhmmss:latest
```

3. View logs:
```bash
docker logs -f hhmmss-app
```

4. Stop the container:
```bash
docker stop hhmmss-app
docker rm hhmmss-app
```

#### Docker Configuration

The Docker setup includes:
- **Multi-stage build**: Optimized image size using separate build and runtime stages
- **LibreOffice integration**: Pre-installed for PDF conversion capabilities
- **Health checks**: Automatic health monitoring via Spring Boot Actuator
- **Non-root user**: Runs as unprivileged user for enhanced security
- **Resource limits**: JVM configured for containerized environments (75% max RAM)
- **Volume support**: Optional persistent storage for uploaded files

**Environment Variables:**
- `SPRING_PROFILES_ACTIVE=docker` - Activates Docker-specific configuration
- `JAVA_OPTS` - JVM options (default: `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`)
- `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` - Max upload size (default: 128KB)
- `JODCONVERTER_LOCAL_ENABLED` - Enable/disable LibreOffice integration (default: true)
- `APP_THROTTLING_MAX_CONCURRENT_REQUESTS` - Max concurrent conversions (default: 2)
- `APP_THROTTLING_TIMEOUT_SECONDS` - Throttling timeout in seconds (default: 30)
- `CLEANUP_RETENTION_DAYS` - Days to retain uploaded files before cleanup (default: 7)

### Running Locally (Without Docker)

1. Clone the repository:
```bash
git clone https://github.com/fierascu/hhmmss.git
cd hhmmss
```

2. Build the project:
```bash
mvn clean install
```

3. Run the application:
```bash
mvn spring-boot:run
```

4. Open your browser and navigate to:
```
http://localhost:8080
```

### Running Tests

Execute the comprehensive test suite:
```bash
mvn test
```

Or with Docker:
```bash
docker run --rm -v "$(pwd)":/build -w /build maven:3.9-eclipse-temurin-21-alpine mvn test
```

### Deploying to Fly.io

The application includes configuration for deployment to Fly.io:

1. Install the Fly.io CLI:
```bash
curl -L https://fly.io/install.sh | sh
```

2. Login to Fly.io:
```bash
fly auth login
```

3. Deploy the application:
```bash
fly deploy
```

4. Open the deployed application:
```bash
fly open
```

**Fly.io Configuration:**
- Application configured with 1GB RAM, shared CPU
- Auto-start/stop machines for cost optimization
- HTTPS enforced by default
- Region: iad (US East)

## Usage

1. **Single File Conversion**:
   - Click "Select Excel Timesheet or ZIP file"
   - Choose an Excel file (.xls, .xlsx, .xlsm, .xlsb)
   - Click "Upload and Convert"
   - Watch the ASCII loading animation during processing
   - Download the generated DOCX file

2. **Batch Processing**:
   - Create a ZIP file containing multiple Excel timesheets
   - Upload the ZIP file
   - View the processing results showing successful and failed conversions
   - Download the result ZIP file containing all converted DOCX files

3. **UI Theme Selection**:
   - Access different themes via URL parameters:
     - ASCII theme (default): `http://localhost:8080/`
     - Classic theme: `http://localhost:8080/?theme=classic`
     - Terminal theme: `http://localhost:8080/?theme=terminal`

## Development History

### Project Evolution

**Initial Concept**:
- Started as an AI code generation experiment using ChatGPT and IntelliJ's Junie
- Original goal: Parse dummy timesheet data and generate DOCX files
- Evolved from Python prototype to Java implementation

**Framework Selection**:
- Initially explored JHipster but found it too complex for the use case
- Settled on Spring Initializr for a lightweight, focused solution

### Major Milestones

1. **POC Phase**: Basic Excel parsing and Word document generation
2. **Web Interface**: Added Spring Boot web layer with file upload capability
3. **Security Hardening**: Implemented file validation and executable detection
4. **Error Handling**: Added comprehensive error handling and user feedback
5. **UI Modernization**: Upgraded to modern, responsive Thymeleaf templates
6. **Batch Processing**: Added ZIP file support for bulk conversions
7. **Test Coverage**: Comprehensive unit tests for all core services and utilities

### Recent Enhancements

- **PDF Export**: Integrated LibreOffice-based PDF conversion for Excel and Word files
- **UI Themes**: Added ASCII art, Classic, and Terminal UI themes
- **ASCII Loading Animation**: Retro-style spinner during file processing
- **Fly.io Deployment**: Cloud deployment configuration with auto-scaling
- **Request Throttling**: Semaphore-based concurrency control to prevent resource exhaustion
- **File Cleanup Service**: Automated daily cleanup of temporary files
- **File Integrity**: SHA-256 hashing for uploaded files
- **Time-based UUIDs**: Sortable unique identifiers for file operations
- Implemented ZIP batch processing capability
- Added comprehensive file type validation (extension, MIME type, file signature)
- Created global exception handler for consistent error handling
- Added comprehensive unit test coverage

## Testing

The project includes extensive unit tests covering:
- Excel parsing (XlsService)
- Word document generation (DocService)
- PDF conversion (PdfService)
- ZIP batch processing (ZipProcessingService)
- File validation (FileTypeValidator)
- Upload handling (UploadService, UploadController)
- Request throttling (ThrottlingService)
- File cleanup (FileCleanupService)
- File hashing (FileHasher)
- Error handling (GlobalExceptionHandler)

Test coverage includes edge cases, error scenarios, and security validations.

## Security Considerations

- **File Type Validation**: Multi-level validation (extension, MIME type, magic bytes)
- **Executable Detection**: Blocks upload of executable files
- **Temporary Storage**: Files stored securely with time-based UUID naming
- **Error Sanitization**: No sensitive information exposed in error messages
- **Request Throttling**: Prevents DoS attacks through concurrent request limiting
- **Automatic Cleanup**: Removes temporary files after configurable retention period
- **File Integrity**: SHA-256 hashing for upload verification

## Future Enhancements

- Enhanced template customization options
- User authentication and multi-tenancy
- Advanced scheduling and formatting options
- REST API for programmatic access
- Webhook support for integration with other systems
- Custom branding and theming options

## Contributing

This project explores the intersection of AI-assisted development and practical application building. Contributions and feedback are welcome!

## Acknowledgments

Generated with assistance from:
- ChatGPT (initial concept and Python prototype)
- IntelliJ Junie (code refinement and Java conversion)
- Claude Code (ongoing development and enhancement)

## License

This project is open source and available for educational and commercial use.

---

**Note**: As with food, code has flavors. Some will like it, others will need more salt. This project showcases how AI-generated code can be both elegant and practical, while sometimes being verbose or redundant. The journey from AI-generated prototype to production application has been both insightful and educational.

**Special Thanks to Claude**: A significant portion of this project's evolution was powered by Claude Code. From implementing comprehensive security features and batch processing capabilities, to modernizing the UI and establishing robust error handling patterns, Claude demonstrated exceptional understanding of software engineering best practices. The ability to autonomously design solutions, write clean and maintainable code, create comprehensive test suites, and maintain consistent architecture throughout the codebase has been invaluable. Claude's contributions have transformed this from a simple proof-of-concept into a production-ready application with enterprise-grade features. The collaborative development experience showcased how AI can be a thoughtful and reliable engineering partner.
