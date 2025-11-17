# Security Audit Report
## HHMMSS Timesheet Converter Application

**Audit Date:** 2025-11-17
**Application:** Spring Boot Timesheet Converter
**Version:** 0.0.1-SNAPSHOT
**Auditor:** Claude (AI Security Auditor)

---

## Executive Summary

This security audit was performed on the HHMMSS Timesheet Converter, a Spring Boot 3.5.7 web application that converts Excel timesheets to Word documents and PDFs. The application demonstrates **good security practices** overall, with multiple layers of file validation, path traversal protection, and proper error handling. However, several **medium and high-severity vulnerabilities** were identified that should be addressed before production deployment.

**Overall Security Rating:** ⚠️ **MEDIUM-HIGH RISK**

---

## Application Overview

- **Type:** Spring Boot 3.5.7 Web Application
- **Language:** Java 21
- **Key Dependencies:**
  - Apache POI 5.4.1 (Excel/Word processing)
  - JODConverter 4.4.7 (LibreOffice integration)
  - Thymeleaf (Template engine)
- **Deployment:** Docker, Docker Compose, Fly.io
- **Features:**
  - File upload and conversion
  - Batch ZIP processing
  - PDF generation via LibreOffice
  - Request throttling
  - Scheduled file cleanup

---

## Security Findings

### 🔴 CRITICAL Vulnerabilities

#### 1. Missing Authentication and Authorization
**Severity:** CRITICAL
**CWE:** CWE-306 (Missing Authentication for Critical Function)
**Location:** Entire application

**Description:**
The application has **NO authentication or authorization** mechanisms implemented. All endpoints are publicly accessible without any user verification:
- File upload endpoint (`POST /`)
- File download endpoint (`GET /files/{filename}`)
- Template generation endpoint (`POST /generate`)
- Metrics endpoint (via Spring Actuator)

**Impact:**
- Anyone can upload files to the server
- Anyone can download any file if they know/guess the filename
- No audit trail of who performed actions
- Resource exhaustion attacks possible
- Data exfiltration risk

**Affected Files:**
- All controllers (no security configuration found)
- No `SecurityConfig.java` or Spring Security dependency

**Recommendation:**
```java
// Add Spring Security dependency to pom.xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

// Implement SecurityConfig.java with authentication
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(Customizer.withDefaults())
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
```

---

#### 2. Insecure Direct Object Reference (IDOR)
**Severity:** CRITICAL
**CWE:** CWE-639 (Authorization Bypass Through User-Controlled Key)
**Location:** `UploadController.java:79-89`

**Description:**
The file download endpoint allows users to access **any file** by providing the filename in the URL:

```java
@GetMapping("/files/{filename:.+}")
@ResponseBody
public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
    Resource file = uploadService.loadAsResource(filename);
    // No ownership verification - anyone can download any file!
}
```

**Impact:**
- User A can download files uploaded by User B
- Complete data breach if filenames are predictable or enumerated
- Privacy violation

**Proof of Concept:**
```bash
# User uploads file, gets: files/018c5e1e-3f2a-7b4c-9d6e-1a2b3c4d5e6f-a1b2c3d4e5f67890.xlsx
# Attacker can access by guessing/enumerating:
curl http://server/files/018c5e1e-3f2a-7b4c-9d6e-1a2b3c4d5e6f-a1b2c3d4e5f67890.xlsx
```

**Recommendation:**
- Implement session-based file ownership tracking
- Verify that the requesting user owns the file before serving
- Use session IDs or user-specific tokens in filenames

---

#### 3. Missing CSRF Protection
**Severity:** HIGH
**CWE:** CWE-352 (Cross-Site Request Forgery)
**Location:** All POST endpoints

**Description:**
The application does not implement CSRF tokens for state-changing operations. Spring Security is not configured, so the default CSRF protection is missing.

**Affected Endpoints:**
- `POST /` (file upload)
- `POST /generate` (template generation)

**Impact:**
An attacker can trick authenticated users into:
- Uploading malicious files
- Generating unwanted timesheets
- Consuming server resources

**Attack Vector:**
```html
<!-- Attacker's malicious page -->
<form action="http://vulnerable-app/generate" method="POST">
    <input type="hidden" name="period" value="2025-01">
</form>
<script>document.forms[0].submit();</script>
```

**Recommendation:**
- Enable Spring Security (automatically enables CSRF protection)
- Add CSRF tokens to all Thymeleaf forms:
```html
<form th:action="@{/}" method="POST">
    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
    <!-- rest of form -->
</form>
```

---

### 🟠 HIGH Severity Vulnerabilities

#### 4. Potential XXE via Apache POI
**Severity:** HIGH
**CWE:** CWE-611 (Improper Restriction of XML External Entity Reference)
**Location:** `XlsService.java:52-53`, `DocService.java:74-75`

**Description:**
The application uses Apache POI to parse XLSX and DOCX files (which are ZIP archives containing XML). While POI 5.4.1 has some XXE protections, there's no explicit hardening:

```java
// XlsService.java:52-53
try (InputStream in = new FileInputStream(xlsxPath.toFile());
     Workbook wb = new XSSFWorkbook(in)) {
    // No explicit XXE protection configuration
}
```

**Impact:**
- Server-Side Request Forgery (SSRF)
- Local file disclosure
- Denial of Service
- Potential Remote Code Execution

**Attack Vector:**
An attacker could craft a malicious XLSX file with XML external entities:
```xml
<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
<worksheet>&xxe;</worksheet>
```

**Recommendation:**
While Apache POI 5.4.1 has default XXE protections, explicitly configure them:

```java
// Before parsing, configure safer XML processing
System.setProperty("javax.xml.stream.XMLInputFactory",
    "com.sun.xml.internal.stream.XMLInputFactoryImpl");
System.setProperty("javax.xml.parsers.SAXParserFactory",
    "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl");

// Or use POI's built-in protection
XMLConstants.FEATURE_SECURE_PROCESSING should be enabled
```

**Status:** MEDIUM risk (POI 5.4.1 has default protections, but not explicitly configured)

---

#### 5. Information Disclosure via Error Messages
**Severity:** MEDIUM-HIGH
**CWE:** CWE-209 (Generation of Error Message Containing Sensitive Information)
**Location:** `GlobalExceptionHandler.java:96`, `UploadController.java:127`

**Description:**
Error messages expose internal implementation details:

```java
// GlobalExceptionHandler.java:96
mav.addObject("message", "An unexpected error occurred: " + exc.getMessage());

// UploadController.java:127
redirectAttributes.addFlashAttribute("errorMessage",
    "An unexpected error occurred: " + e.getMessage());
```

**Impact:**
- Stack traces could reveal:
  - File system paths (`/app/uploads/...`)
  - Library versions
  - Database connection strings (if added later)
  - Internal logic details
- Aids attackers in reconnaissance

**Example Leaked Information:**
```
Error processing template: /app/uploads/temp-12345.docx (No such file or directory)
```

**Recommendation:**
```java
// Use generic error messages for users
mav.addObject("message", "An error occurred while processing your request. " +
    "Please contact support with reference ID: " + generateErrorId());

// Log detailed errors server-side only
log.error("Error processing file: {}", exc.getMessage(), exc);
```

---

#### 6. Insecure File Cleanup Configuration
**Severity:** MEDIUM
**CWE:** CWE-459 (Incomplete Cleanup)
**Location:** `application.properties:18`, `application-docker.properties:27`

**Description:**
Files are retained for only **1 day** in both configurations:

```properties
# application.properties:18
cleanup.retention.days=1

# application-docker.properties:27
cleanup.retention.days=1
```

This creates a **data retention window** where files remain accessible to attackers who discover the filenames.

**Impact:**
- Extended exposure window for sensitive data
- GDPR/privacy compliance issues
- Files should be deleted immediately after download or session expiry

**Recommendation:**
```properties
# Reduce to minimal retention
cleanup.retention.days=0  # Delete at end of day
# OR implement session-based cleanup (delete after user session ends)
```

```java
// Implement immediate cleanup after download
@GetMapping("/files/{filename:.+}")
public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
    Resource file = uploadService.loadAsResource(filename);

    // Schedule for immediate deletion after serving
    CompletableFuture.runAsync(() -> {
        try {
            Thread.sleep(5000); // Allow download to complete
            uploadService.delete(filename);
        } catch (Exception e) {
            log.error("Failed to cleanup file: {}", filename, e);
        }
    });

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
        .body(file);
}
```

---

### 🟡 MEDIUM Severity Vulnerabilities

#### 7. Command Injection via LibreOffice (Mitigated by JODConverter)
**Severity:** LOW-MEDIUM (Mitigated)
**CWE:** CWE-78 (OS Command Injection)
**Location:** `PdfService.java:35-42`

**Description:**
The application uses JODConverter to invoke LibreOffice in headless mode. While JODConverter provides good isolation, file paths are passed to an external process:

```java
documentConverter
    .convert(xlsPath.toFile())
    .to(pdfPath.toFile())
    .execute();
```

**Mitigation Status:** ✅ **GOOD**
- File paths are validated and normalized before reaching this point
- `UploadService.java:92-96` prevents path traversal
- JODConverter uses process isolation
- Filenames are UUID-based, preventing special character injection

**Recommendation:**
Current implementation is secure. Continue validating inputs upstream.

---

#### 8. Weak Throttling Mechanism
**Severity:** MEDIUM
**CWE:** CWE-770 (Allocation of Resources Without Limits or Throttling)
**Location:** Configuration

**Description:**
The application limits concurrent conversions to **2** (via semaphore), but has no:
- Per-IP rate limiting
- Total request rate limiting
- Cooldown periods after failures

**Current Protection:**
```properties
app.throttling.max-concurrent-requests=2
app.throttling.timeout-seconds=30
```

**Impact:**
- Attacker can still launch denial-of-service by:
  - Repeatedly uploading files (occupying 2 slots continuously)
  - Timing out requests (30s * many attempts)
  - Using multiple IPs to bypass semaphore

**Recommendation:**
Implement multi-layer rate limiting:

```java
@Configuration
public class RateLimitConfig {
    @Bean
    public RateLimiter perIpRateLimiter() {
        // 10 requests per minute per IP
        return RateLimiter.create(10.0 / 60.0);
    }

    @Bean
    public RateLimiter globalRateLimiter() {
        // 100 requests per minute globally
        return RateLimiter.create(100.0 / 60.0);
    }
}
```

---

#### 9. Lack of Content Security Policy (CSP)
**Severity:** LOW-MEDIUM
**CWE:** CWE-1021 (Improper Restriction of Rendered UI Layers)
**Location:** Missing security headers

**Description:**
The application does not set security headers:
- No Content-Security-Policy
- No X-Frame-Options
- No X-Content-Type-Options
- No Referrer-Policy

**Impact:**
- XSS vulnerability amplification
- Clickjacking attacks possible
- MIME-sniffing vulnerabilities

**Recommendation:**
```java
@Configuration
public class SecurityHeadersConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request,
                                     HttpServletResponse response,
                                     Object handler) {
                response.setHeader("Content-Security-Policy",
                    "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'");
                response.setHeader("X-Frame-Options", "DENY");
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
                response.setHeader("X-XSS-Protection", "1; mode=block");
                return true;
            }
        });
    }
}
```

---

#### 10. Filename Enumeration via UUID Predictability
**Severity:** LOW-MEDIUM
**CWE:** CWE-330 (Use of Insufficiently Random Values)
**Location:** `TimeBasedUuidGenerator.java`

**Description:**
The application uses **time-based UUIDs** (UUID v1 or custom) for filenames. While the file also includes a hash, time-based UUIDs are partially predictable:

```java
// UploadService.java:89-90
String secureFilename = timeBasedUuid + "-" + fileHash + fileExtension;
// Example: 018c5e1e-3f2a-7b4c-9d6e-1a2b3c4d5e6f-a1b2c3d4e5f67890.xlsx
```

**Impact:**
- Attackers can enumerate filenames by:
  1. Predicting timestamp-based UUID portion
  2. Brute-forcing the 16-character hash portion (2^64 combinations)
- Combined with IDOR vulnerability, this enables file access

**Recommendation:**
Use **UUID v4 (random)** instead:

```java
UUID secureUuid = UUID.randomUUID(); // v4 - cryptographically random
```

Or use `SecureRandom`:

```java
byte[] randomBytes = new byte[16];
SecureRandom.getInstanceStrong().nextBytes(randomBytes);
String secureId = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
```

---

### 🟢 LOW Severity Findings

#### 11. Missing Security Headers in Docker Health Check
**Severity:** LOW
**Location:** `Dockerfile:49-50`

**Description:**
Health check uses `wget` without certificate validation:
```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
```

**Impact:** Minimal (health check is internal to container)

**Recommendation:**
Add `--no-check-certificate` explicitly or use `curl`:
```dockerfile
CMD curl -f http://localhost:8080/actuator/health || exit 1
```

---

#### 12. Actuator Endpoints Exposure
**Severity:** LOW
**Location:** `application-docker.properties:22-23`

**Description:**
```properties
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=when-authorized
```

**Status:** ✅ **GOOD** - Only health and info exposed, details require authorization

**Recommendation:** Keep as-is, but ensure authentication is implemented.

---

## Security Strengths ✅

The application demonstrates several **excellent security practices**:

### 1. Comprehensive File Validation
**Location:** `FileTypeValidator.java`

- ✅ **Triple validation**: Extension, MIME type, magic bytes
- ✅ **Executable blocking**: Detects `.exe` and ELF files
- ✅ **File signature verification**: Prevents renamed files

```java
// FileTypeValidator.java:51-57
if (matchesMagicBytes(header, EXE_MAGIC)) {
    throw new IllegalArgumentException("File appears to be a Windows executable");
}
if (matchesMagicBytes(header, ELF_MAGIC)) {
    throw new IllegalArgumentException("File appears to be a Linux executable");
}
```

### 2. Path Traversal Protection
**Location:** `UploadService.java:92-96`, `ZipProcessingService.java:163-166`

- ✅ **Normalization and validation**: Prevents `../` attacks
- ✅ **ZIP slip protection**: Validates extracted paths

```java
// UploadService.java:92-96
Path destinationFile = this.rootLocation.resolve(Paths.get(secureFilename))
        .normalize().toAbsolutePath();
if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
    throw new StorageException("Cannot store file outside current directory.");
}
```

### 3. Secure Docker Configuration
**Location:** `Dockerfile`

- ✅ **Non-root user**: Runs as `spring:spring` user
- ✅ **Multi-stage build**: Separates build from runtime
- ✅ **Minimal base image**: Alpine Linux
- ✅ **Health checks**: Proper container monitoring

```dockerfile
# Dockerfile:31-43
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
```

### 4. File Integrity Verification
**Location:** `FileHasher.java`

- ✅ **SHA-256 hashing**: Strong cryptographic algorithm
- ✅ **File integrity tracking**: Tamper detection capability

### 5. Input Validation and Size Limits
**Location:** `UploadController.java:300-325`

- ✅ **File size validation**: Separate limits for XLSX (128KB) and ZIP (2MB)
- ✅ **Type-specific limits**: Different limits for different file types

### 6. Proper Error Handling
**Location:** `GlobalExceptionHandler.java`

- ✅ **Centralized exception handling**: Consistent error responses
- ✅ **Specific exception types**: Different handling per error type

### 7. Automatic Cleanup
**Location:** `FileCleanupService.java`

- ✅ **Scheduled cleanup**: Prevents disk exhaustion
- ✅ **Startup cleanup**: Removes stale files on restart

---

## Dependency Security Assessment

### Current Dependencies
```xml
<!-- pom.xml -->
<spring-boot-starter-parent>3.5.7</spring-boot-starter-parent>
<poi-ooxml>5.4.1</poi-ooxml>
<jodconverter-local>4.4.7</jodconverter-local>
```

### Vulnerability Status

#### Apache POI 5.4.1
- **Status:** ⚠️ Check for CVEs
- **Known Issues:** Historical XXE vulnerabilities (mostly patched)
- **Recommendation:** Monitor for updates, currently on recent version

#### JODConverter 4.4.7
- **Status:** ⚠️ Last release: 2023
- **Risk:** May have unpatched vulnerabilities
- **Recommendation:** Check for maintained forks or alternatives

#### Spring Boot 3.5.7
- **Status:** ⚠️ Version 3.5.7 does not exist!
- **Latest:** Spring Boot 3.3.x (as of Jan 2025)
- **Action:** **CRITICAL** - Fix version number in `pom.xml`

**Corrected version:**
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.0</version> <!-- or latest 3.x -->
</parent>
```

---

## Compliance & Best Practices

### OWASP Top 10 (2021) Assessment

| Rank | Category | Status | Notes |
|------|----------|--------|-------|
| A01 | Broken Access Control | ❌ FAIL | No authentication/authorization |
| A02 | Cryptographic Failures | ⚠️ PARTIAL | Good hashing, but no encryption at rest |
| A03 | Injection | ✅ PASS | No SQL/NoSQL, good input validation |
| A04 | Insecure Design | ⚠️ PARTIAL | IDOR vulnerability, missing CSRF |
| A05 | Security Misconfiguration | ❌ FAIL | No security headers, missing CSP |
| A06 | Vulnerable Components | ⚠️ PARTIAL | Invalid Spring Boot version |
| A07 | Auth & Session Management | ❌ FAIL | No implementation |
| A08 | Software & Data Integrity | ✅ PASS | Good file validation |
| A09 | Logging & Monitoring | ⚠️ PARTIAL | Logging present, no alerting |
| A10 | SSRF | ⚠️ PARTIAL | Potential via XXE |

**Overall OWASP Score:** 4/10 ⚠️

---

## Recommendations Priority Matrix

### CRITICAL (Fix Immediately)
1. ✅ **Implement Authentication** - Add Spring Security
2. ✅ **Fix Spring Boot Version** - Correct pom.xml version
3. ✅ **Implement Authorization** - File ownership verification
4. ✅ **Add CSRF Protection** - Enable tokens

### HIGH (Fix Before Production)
5. ⚠️ **Configure XXE Protection** - Harden Apache POI
6. ⚠️ **Sanitize Error Messages** - Remove sensitive details
7. ⚠️ **Add Security Headers** - CSP, X-Frame-Options, etc.
8. ⚠️ **Reduce File Retention** - Implement session-based cleanup

### MEDIUM (Fix Soon)
9. 🔵 **Enhance Rate Limiting** - Add per-IP limits
10. 🔵 **Use Random UUIDs** - Switch from time-based to v4
11. 🔵 **Add Input Sanitization** - Thymeleaf output encoding (already good)

### LOW (Monitor)
12. 🟢 **Update Dependencies** - Regular vulnerability scanning
13. 🟢 **Add Logging/Monitoring** - Security event detection
14. 🟢 **Implement SIEM Integration** - Centralized logging

---

## Testing Recommendations

### Security Testing Checklist

- [ ] Penetration testing for authentication bypass
- [ ] IDOR testing with different user sessions
- [ ] File upload fuzzing (malicious files)
- [ ] XXE exploitation attempts
- [ ] CSRF token validation testing
- [ ] Rate limiting stress testing
- [ ] Path traversal attack vectors
- [ ] Dependency vulnerability scanning (Snyk, OWASP Dependency-Check)
- [ ] Docker security scanning (Trivy, Grype)
- [ ] Static code analysis (SonarQube, Checkmarx)

### Recommended Tools

```bash
# Dependency scanning
mvn org.owasp:dependency-check-maven:check

# Docker image scanning
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  aquasec/trivy image hhmmss-app:latest

# Static analysis
mvn sonar:sonar

# DAST scanning
zap-cli quick-scan --self-contained http://localhost:8080
```

---

## Conclusion

The HHMMSS Timesheet Converter demonstrates **strong file handling security** with comprehensive validation and path traversal protection. However, the **complete absence of authentication and authorization** makes it unsuitable for production deployment in its current state.

### Key Actions Required

**Before Production:**
1. Implement Spring Security with authentication
2. Add file ownership/session verification (fix IDOR)
3. Enable CSRF protection
4. Fix Spring Boot version in pom.xml
5. Add security headers
6. Harden XXE protection

**Post-Deployment:**
1. Regular dependency updates
2. Security monitoring and logging
3. Periodic penetration testing
4. Compliance audits (GDPR, HIPAA if applicable)

### Final Rating

**Current State:** ⚠️ **NOT PRODUCTION READY**
**With Fixes:** ✅ **PRODUCTION READY**

The application has a solid security foundation for file operations but requires authentication/authorization infrastructure before deployment.

---

## Appendix: References

- [OWASP Top 10 (2021)](https://owasp.org/Top10/)
- [CWE/SANS Top 25](https://cwe.mitre.org/top25/)
- [Spring Security Documentation](https://docs.spring.io/spring-security/reference/)
- [Apache POI Security](https://poi.apache.org/help/security.html)
- [Docker Security Best Practices](https://docs.docker.com/engine/security/)

---

**Report Generated:** 2025-11-17
**Auditor:** Claude AI Security Auditor
**Contact:** [Security Team]
