# Security Fix: Session-Based File Ownership Tracking

## Overview
This document describes the implementation of session-based file ownership tracking to address the **IDOR (Insecure Direct Object Reference)** vulnerability identified in the security audit report.

## Vulnerability Fixed
**CWE-639: Authorization Bypass Through User-Controlled Key**
- **Severity:** CRITICAL
- **Issue:** Users could access any file by guessing/knowing the filename
- **Impact:** Complete data breach, privacy violation

## Implementation Details

### 1. FileOwnershipService.java (NEW)
**Location:** `src/main/java/eu/hhmmss/app/uploadingfiles/storage/FileOwnershipService.java`

**Purpose:** Centralized service for tracking file ownership by HTTP session

**Key Features:**
- Thread-safe concurrent tracking of session-to-files mapping
- Ownership verification before file access
- Session cleanup support
- Session ID masking in logs for security

**Security Benefits:**
- Prevents unauthorized file access
- Maintains audit trail of access attempts
- Supports session invalidation cleanup

### 2. UploadService.java (MODIFIED)
**Location:** `src/main/java/eu/hhmmss/app/uploadingfiles/storage/UploadService.java`

**Changes:**
1. **Injected FileOwnershipService dependency**
   - Added constructor parameter for ownership tracking

2. **Modified `store()` method signature**
   - Added `String sessionId` parameter (required)
   - Validates session ID is not null/empty

3. **Enhanced filename generation**
   - **Before:** `UUID-hash-extension` (e.g., `550e8400-e29b-41d4-a716-446655440000-a1b2c3d4e5f67890.xlsx`)
   - **After:** `sessionPrefix_UUID-hash-extension` (e.g., `ABC123DEF456_550e8400-e29b-41d4-a716-446655440000-a1b2c3d4e5f67890.xlsx`)
   - Session prefix: First 12 alphanumeric characters of session ID
   - Prevents path traversal and special character issues

4. **Ownership tracking**
   - Automatically tracks file ownership on upload
   - Logs session ID (masked) for audit trail

5. **New method: `trackGeneratedFile()`**
   - Tracks ownership for derived files (DOCX, PDF)
   - Inherits session from source file

**Security Benefits:**
- Session-specific filenames prevent enumeration attacks
- Automatic ownership tracking on upload
- Support for tracking generated/derived files

### 3. UploadController.java (MODIFIED)
**Location:** `src/main/java/eu/hhmmss/app/uploadingfiles/storage/UploadController.java`

**Changes:**

#### 3.1 Injected Dependencies
```java
private final FileOwnershipService fileOwnershipService;
```

#### 3.2 File Download Endpoint (serveFile)
**Before:**
```java
@GetMapping("/files/{filename:.+}")
public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
    Resource file = uploadService.loadAsResource(filename);
    // No ownership check - VULNERABLE!
    return ResponseEntity.ok()...
}
```

**After:**
```java
@GetMapping("/files/{filename:.+}")
public ResponseEntity<Resource> serveFile(@PathVariable String filename, HttpSession session) {
    // SECURITY CHECK: Verify ownership before serving
    if (!fileOwnershipService.verifyOwnership(session, filename)) {
        log.warn("Unauthorized file access attempt...");
        return ResponseEntity.status(403).build(); // 403 Forbidden
    }

    Resource file = uploadService.loadAsResource(filename);
    return ResponseEntity.ok()...
}
```

**Security Benefits:**
- **Authorization check** before serving files
- Returns **403 Forbidden** for unauthorized access attempts
- Logs unauthorized access attempts for security monitoring
- Prevents IDOR attacks completely

#### 3.3 File Upload Handler (handleFileUpload)
**Changes:**
- Added `HttpSession session` parameter
- Extracts `session.getId()` and passes to `uploadService.store()`
- Passes session ID to `handleExcelFile()` and `handleZipFile()`

#### 3.4 Excel File Handler (handleExcelFile)
**Changes:**
- Added `String sessionId` parameter
- Tracks ownership for all generated files:
  - **DOCX file** (timesheet document)
  - **Excel PDF** (1:1 print of input)
  - **DOCX PDF** (1:1 print of timesheet)

**Tracking calls:**
```java
uploadService.trackGeneratedFile(uuidFilename, extractedFilename, sessionId);    // DOCX
uploadService.trackGeneratedFile(uuidFilename, xlsPdfFilename, sessionId);       // Excel PDF
uploadService.trackGeneratedFile(uuidFilename, docPdfFilename, sessionId);       // DOCX PDF
```

#### 3.5 ZIP File Handler (handleZipFile)
**Changes:**
- Added `String sessionId` parameter
- Tracks ownership for result ZIP file:
  ```java
  uploadService.trackGeneratedFile(uuidFilename, result.resultZipFileName(), sessionId);
  ```

#### 3.6 Template Generator (handleGenerate)
**Changes:**
- Added `HttpSession session` parameter
- Tracks ownership for generated template files:
  ```java
  fileOwnershipService.trackFile(sessionId, filename);
  ```
- Tracks both cached and newly generated templates

## Security Improvements Summary

### Before Implementation
❌ **No authentication/authorization**
❌ **Anyone could download any file** if they knew the filename
❌ **No audit trail** of file access
❌ **Predictable filenames** (time-based UUID + hash)
❌ **No session isolation**

### After Implementation
✅ **Session-based file ownership** tracking
✅ **Authorization check** on every file download
✅ **403 Forbidden** response for unauthorized access
✅ **Audit logging** of unauthorized access attempts
✅ **Session-prefixed filenames** for additional security
✅ **Complete session isolation** - users can only access their own files
✅ **Automatic tracking** for all generated/derived files

## Attack Scenarios - Before vs After

### Attack Scenario 1: Direct File Access
**Before:**
```bash
# User A uploads file, gets URL: /files/550e8400-e29b-41d4-a716-446655440000-abc123.xlsx
# User B (attacker) can access User A's file:
curl http://server/files/550e8400-e29b-41d4-a716-446655440000-abc123.xlsx
# ❌ SUCCESS - File downloaded
```

**After:**
```bash
# User A uploads file, gets URL: /files/ABC123DEF456_550e8400-e29b-41d4-a716-446655440000-abc123.xlsx
# User B (attacker) tries to access User A's file:
curl -b "JSESSIONID=XYZ789GHI012" http://server/files/ABC123DEF456_550e8400-e29b-41d4-a716-446655440000-abc123.xlsx
# ✅ BLOCKED - 403 Forbidden
# Server logs: "Unauthorized file access attempt: session=XYZ789GH***, file=ABC123DEF456_..."
```

### Attack Scenario 2: Filename Enumeration
**Before:**
```bash
# Attacker can enumerate files by predicting UUIDs and hashes
for uuid in $(generate_time_based_uuids); do
  for hash in $(brute_force_hashes); do
    curl http://server/files/${uuid}-${hash}.xlsx
  done
done
# ❌ Can potentially find and download files
```

**After:**
```bash
# Attacker cannot enumerate because:
# 1. Session prefix is unknown (user-specific)
# 2. Even if they guess the filename, ownership check blocks access
curl http://server/files/ABC123DEF456_550e8400-e29b-41d4-a716-446655440000-abc123.xlsx
# ✅ BLOCKED - 403 Forbidden (wrong session)
```

## Files Modified

1. **NEW:** `src/main/java/eu/hhmmss/app/uploadingfiles/storage/FileOwnershipService.java`
2. **MODIFIED:** `src/main/java/eu/hhmmss/app/uploadingfiles/storage/UploadService.java`
3. **MODIFIED:** `src/main/java/eu/hhmmss/app/uploadingfiles/storage/UploadController.java`

## Testing Recommendations

### Unit Tests
```java
@Test
void testOwnershipVerification_SameSession_ShouldSucceed() {
    // User uploads file
    String filename = uploadService.store(file, sessionId);

    // Same user downloads file
    boolean canAccess = fileOwnershipService.verifyOwnership(session, filename);

    assertTrue(canAccess);
}

@Test
void testOwnershipVerification_DifferentSession_ShouldFail() {
    // User A uploads file
    String filename = uploadService.store(file, sessionIdA);

    // User B tries to access
    boolean canAccess = fileOwnershipService.verifyOwnership(sessionB, filename);

    assertFalse(canAccess);
}

@Test
void testServeFile_UnauthorizedAccess_Returns403() {
    // User A uploads file
    String filename = uploadService.store(file, sessionA.getId());

    // User B tries to download
    ResponseEntity<Resource> response = controller.serveFile(filename, sessionB);

    assertEquals(403, response.getStatusCodeValue());
}
```

### Integration Tests
1. **Test file upload and download** in same session → Should succeed
2. **Test file download** from different session → Should return 403
3. **Test generated files** (DOCX, PDF) ownership → Should track correctly
4. **Test ZIP processing** → Result ZIP should be owned by uploader
5. **Test template generation** → Template should be owned by requester

### Manual Testing
```bash
# Test 1: Upload and download in same session
curl -c cookies.txt -F "file=@test.xlsx" http://localhost:8080/
curl -b cookies.txt http://localhost:8080/files/ABC123...xlsx
# Expected: File downloads successfully

# Test 2: Try to access file from different session
curl http://localhost:8080/files/ABC123...xlsx
# Expected: 403 Forbidden

# Test 3: Check server logs for unauthorized access attempts
tail -f logs/application.log | grep "Unauthorized file access"
```

## Compliance

This implementation addresses:
- **OWASP Top 10 (2021)**: A01 - Broken Access Control
- **CWE-639**: Authorization Bypass Through User-Controlled Key
- **CWE-284**: Improper Access Control

## Future Enhancements

1. **Session expiration cleanup**
   - Implement `HttpSessionListener` to cleanup file ownership on session invalidation
   - Delete user files when session expires

2. **Database persistence**
   - Move ownership tracking from in-memory to database
   - Supports application restarts and clustering

3. **User authentication**
   - Integrate Spring Security for user authentication
   - Replace session IDs with user IDs for ownership tracking

4. **File download limits**
   - Implement download count tracking per file
   - Support one-time download links

5. **Audit logging**
   - Store all file access attempts in audit log
   - Include IP address, user agent, timestamp

## Migration Notes

**Breaking Changes:**
- `UploadService.store()` method signature changed
  - **Before:** `store(MultipartFile file)`
  - **After:** `store(MultipartFile file, String sessionId)`
  - **Impact:** All callers must provide session ID

**Backward Compatibility:**
- Existing files uploaded before this fix will not have ownership tracking
- Consider implementing a migration script to assign existing files to a default "anonymous" session
- Or implement a grace period where files without ownership are accessible (with warning logs)

## Performance Considerations

- **Memory usage:** Minimal - only stores session ID → filenames mapping
- **Lookup performance:** O(1) for ownership verification (HashMap lookup)
- **Concurrency:** Thread-safe using `ConcurrentHashMap`
- **Scalability:** In-memory solution works for single-server deployments
  - For multi-server deployments, consider Redis or database-backed solution

## Conclusion

This implementation successfully addresses the **CRITICAL IDOR vulnerability** identified in the security audit. Users can now only access files belonging to their HTTP session, preventing unauthorized data access and privacy violations.

**Security Rating:**
- **Before:** 🔴 CRITICAL vulnerability
- **After:** ✅ SECURE - Authorization enforced

---

**Author:** Security Team
**Date:** 2025-11-17
**Related:** SECURITY_AUDIT_REPORT.md
