# hhmmss - Timesheet Converter

A Spring Boot web application that converts Excel timesheets to Word documents (DOCX format). What started as an experiment with AI code generation has evolved into a fully-featured, production-ready timesheet conversion tool.

## Overview

This application provides a simple web interface for uploading Excel timesheets and automatically generating formatted Word documents. It supports both single file conversion and batch processing of multiple timesheets via ZIP files.

## Features

### Core Functionality
- **Excel to Word Conversion**: Upload Excel timesheets (.xls, .xlsx, .xlsm, .xlsb) and convert them to formatted DOCX files
- **Batch Processing**: Process multiple Excel files at once by uploading a ZIP archive
- **Smart File Validation**: Comprehensive file type validation to ensure only valid Excel and ZIP files are processed
- **Security Features**: Executable file detection and blocking to prevent malicious uploads

### User Interface
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
- **Logging**: SLF4J with Lombok
- **Testing**: JUnit 5, Spring Boot Test

## Project Structure

```
src/
├── main/
│   ├── java/eu/hhmmss/app/
│   │   ├── converter/           # Core conversion logic
│   │   │   ├── DocService.java          # Word document generation
│   │   │   ├── XlsService.java          # Excel parsing
│   │   │   ├── ZipProcessingService.java # Batch ZIP processing
│   │   │   └── HhmmssDto.java           # Data transfer object
│   │   ├── uploadingfiles/
│   │   │   └── storage/         # File upload and storage
│   │   │       ├── UploadController.java
│   │   │       ├── UploadService.java
│   │   │       ├── GlobalExceptionHandler.java
│   │   │       └── Storage exceptions
│   │   └── util/                # Utilities
│   │       └── FileTypeValidator.java   # File validation
│   └── resources/
│       ├── templates/           # Thymeleaf templates
│       │   ├── layout.html             # Main layout
│       │   ├── upload.html             # Upload form
│       │   └── error.html              # Error page
│       └── timesheet-template.docx     # Word template
└── test/                        # Comprehensive unit tests
```

## Getting Started

### Prerequisites
- Java 17 or higher
- Maven 3.6+

### Running the Application

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

## Usage

1. **Single File Conversion**:
   - Click "Select Excel Timesheet or ZIP file"
   - Choose an Excel file (.xls, .xlsx, .xlsm, .xlsb)
   - Click "Upload and Convert"
   - Download the generated DOCX file

2. **Batch Processing**:
   - Create a ZIP file containing multiple Excel timesheets
   - Upload the ZIP file
   - View the processing results showing successful and failed conversions
   - Download the result ZIP file containing all converted DOCX files

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

- Added modern, Excel-themed UI with responsive design
- Implemented form validation with real-time feedback
- Added Excel-themed favicon
- Enhanced error messages with inline display
- Implemented ZIP batch processing capability
- Added comprehensive file type validation (extension, MIME type, file signature)
- Created global exception handler for consistent error handling
- Added detailed processing results for batch operations
- Implemented secure temporary file storage
- Added comprehensive unit test coverage

## Testing

The project includes extensive unit tests covering:
- Excel parsing (XlsService)
- Word document generation (DocService)
- ZIP batch processing (ZipProcessingService)
- File validation (FileTypeValidator)
- Upload handling (UploadService, UploadController)
- Error handling (GlobalExceptionHandler)

Test coverage includes edge cases, error scenarios, and security validations.

## Security Considerations

- **File Type Validation**: Multi-level validation (extension, MIME type, magic bytes)
- **Executable Detection**: Blocks upload of executable files
- **Temporary Storage**: Files stored securely with UUID-based naming
- **Error Sanitization**: No sensitive information exposed in error messages

## Future Enhancements

- Export to PDF format
- Cloud hosting deployment
- Enhanced template customization options
- User authentication and multi-tenancy
- Advanced scheduling and formatting options

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
