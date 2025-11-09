# Java 21 Setup for Spire Libraries

This project uses Spire.XLS and Spire.Doc libraries which require access to Java internal APIs.
Due to Java 21's module system restrictions, you must configure JVM arguments.

## IntelliJ IDEA Configuration (REQUIRED)

### Quick Setup:

1. **Open Run/Debug Configurations**
   - Top menu bar → Run → Edit Configurations...
   - Or click the dropdown next to the Run button (top-right) → Edit Configurations...

2. **Select your AppApplication configuration**
   - In the left panel, find and select `AppApplication` under "Spring Boot" or "Application"

3. **Add VM Options**
   - If you don't see "VM options" field, click **"Modify options"** (near the top)
   - Check: ✓ **Add VM options**
   - Now you'll see a "VM options" text field

4. **Paste these VM options** (copy ALL of it as one line):
   ```
   --add-opens java.base/sun.security.action=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/sun.reflect=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED
   ```

5. **Apply and Restart**
   - Click **Apply** → **OK**
   - **Stop** the currently running application (red square button)
   - **Start** it again (green play button)

## Alternative: Run with Maven

If you prefer to run via Maven command line:

```bash
mvn spring-boot:run
```

The `.mvn/jvm.config` file will automatically apply the required JVM arguments.

## Why This Is Needed

The error you'll see without these arguments:
```
java.lang.IllegalAccessError: class com.spire.xls.packages.sprRFA
cannot access class sun.security.action.GetPropertyAction
```

Spire libraries (free versions) need access to internal Java classes that are restricted
by default in Java 9+ module system. The `--add-opens` arguments explicitly grant this access.

## Verify It's Working

After configuration, when you upload a file, you should see in the logs:
```
Converting XLS to PDF using Spire.XLS: ...
XLS to PDF conversion completed successfully
```

Instead of the IllegalAccessError.
