# hhmmss - Timesheet Converter

A simple Spring Boot application that converts Excel timesheets to DOCX format.

## Overview

hhmmss is a web application that allows users to upload timesheet files in Excel format and convert them to Word documents (DOCX). The project was created as an experiment with generative AI code generation tools.

## Features

- Web-based file upload interface
- Support for multiple Excel formats (.xls, .xlsx, .xlsm, .xlsb)
- Automatic conversion to DOCX format
- File validation and error handling
- Clean and simple user interface

## Technology Stack

- **Framework**: Spring Boot 3.5.7
- **Java Version**: 21
- **Template Engine**: Thymeleaf
- **Build Tool**: Maven
- **Key Dependencies**:
  - Apache POI (for Excel/Word processing)
  - Spring Boot Actuator
  - Lombok

## Getting Started

### Prerequisites

- Java 21 or higher
- Maven (or use the included Maven wrapper)

### Running the Application

1. Clone the repository:
   ```bash
   git clone https://github.com/fierascu/hhmmss.git
   cd hhmmss
   ```

2. Build the application:
   ```bash
   ./mvnw clean package
   ```

3. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

4. Open your browser and navigate to `http://localhost:8080`

## Usage

1. Visit the application homepage
2. Click "Choose File" and select an Excel timesheet file
3. Click "Upload" to convert the file
4. Download the generated DOCX file

## Project Structure

- `src/main/java/eu/hhmmss/app/`
  - `converter/` - Excel to DOCX conversion services
  - `uploadingfiles/storage/` - File upload and storage handling
  - `util/` - Utility classes (file validation, etc.)
- `src/main/resources/templates/` - Thymeleaf HTML templates
- `src/test/java/` - Unit tests

## Development

The project includes comprehensive unit tests for core functionality. Run tests with:

```bash
./mvnw test
```

## Future Plans

- Add UI improvements
- Add export to PDF functionality
- Deploy to a hosting platform

## License

This project is available as open source.

## About

This project started as an exploration of AI-assisted code generation, using tools like IntelliJ's AI assistant and ChatGPT to generate and refine the code from Python to Java.
