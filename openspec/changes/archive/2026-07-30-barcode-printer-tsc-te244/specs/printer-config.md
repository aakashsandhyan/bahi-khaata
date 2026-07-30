# Spec: Printer Configuration & Health Check

## Overview

Admin-only endpoint and screen to configure printer address, test connectivity, and view status. Single config (singleton) per installation.

## Data Model

**Entity: PrinterConfig**
```java
@Entity
@Table(name = "printer_config")
public class PrinterConfig {
  @Id private UUID id;
  
  @Column(nullable = false) private String address;      // "192.168.1.100:9100" or "/dev/ttyUSB0"
  @Column(nullable = false) private int portSpeed;       // e.g., 9600
  @Column(nullable = false) private String paperSize;    // "4x6" (fixed for now)
  @Column(nullable = false) private int copiesDefault;   // default copies per print job (1–5)
  @Column(nullable = false) private boolean enabled;     // true = printer available
  
  @Column private String testStatus;                     // "OK", "UNREACHABLE", "ERROR"
  @Column private LocalDateTime lastTestedAt;
  @Column(columnDefinition = "TEXT") private String testError; // error message if last test failed
  
  @CreationTimestamp private LocalDateTime createdAt;
  @UpdateTimestamp private LocalDateTime updatedAt;
}
```

**Repository:**
```java
public interface PrinterConfigRepository extends JpaRepository<PrinterConfig, UUID> {
  Optional<PrinterConfig> findFirst();  // singleton query
}
```

## REST Endpoints

### GET /api/admin/printer-config — Read Current Config

**Response (HTTP 200):**
```json
{
  "id": "770e8400-e29b-41d4-a716-446655440002",
  "address": "192.168.1.100:9100",
  "portSpeed": 9600,
  "paperSize": "4x6",
  "copiesDefault": 1,
  "enabled": true,
  "testStatus": "OK",
  "lastTestedAt": "2026-07-27T10:20:00Z",
  "testError": null
}
```

**Error Responses:**
- 403 Forbidden: user is not admin
- 404 Not Found: no config created yet (first run)
- 500 Internal Server Error: database error

**Behavior:**
- Returns singleton config (or 404 if none exists yet)
- Frontend initializes form with these values
- testStatus badge: green if "OK", red if "UNREACHABLE" or "ERROR"

### PUT /api/admin/printer-config — Save Config

**Request:**
```json
{
  "address": "192.168.1.100:9100",
  "portSpeed": 9600,
  "paperSize": "4x6",
  "copiesDefault": 1,
  "enabled": true
}
```

**Validation:**
- `address` is required, non-empty string
- `portSpeed` must be > 0 and <= 115200
- `paperSize` must be "4x6" (currently fixed; extensible)
- `copiesDefault` must be > 0 and <= 5
- `enabled` is boolean

**Response (HTTP 200):**
```json
{
  "id": "770e8400-e29b-41d4-a716-446655440002",
  "address": "192.168.1.100:9100",
  "portSpeed": 9600,
  "paperSize": "4x6",
  "copiesDefault": 1,
  "enabled": true,
  "testStatus": "OK",
  "lastTestedAt": "2026-07-27T10:20:00Z",
  "testError": null
}
```

**Error Responses:**
- 400 Bad Request: validation failed (invalid address, portSpeed, etc.)
- 403 Forbidden: user is not admin
- 500 Internal Server Error: database error

**Behavior:**
- If config doesn't exist, create it
- If config exists, update it
- Don't clear testStatus (preserve last test result)
- Return updated config

### POST /api/admin/printer-config/test — Test Printer Connection

**Request:** (no body)

**Response (HTTP 200):**
```json
{
  "testStatus": "OK",
  "message": "Printer connected and ready.",
  "testedAt": "2026-07-27T10:23:15Z"
}
```

**Alternative Responses:**
```json
{
  "testStatus": "UNREACHABLE",
  "message": "Connection timeout: 192.168.1.100:9100 did not respond after 5 seconds.",
  "testedAt": "2026-07-27T10:23:15Z"
}
```

```json
{
  "testStatus": "ERROR",
  "message": "Serial port error: /dev/ttyUSB0 not found or permission denied.",
  "testedAt": "2026-07-27T10:23:15Z"
}
```

**Error Responses:**
- 403 Forbidden: user is not admin
- 404 Not Found: no config created yet
- 500 Internal Server Error: unexpected error

**Behavior:**
1. Load current PrinterConfig from DB
2. Try to connect to printer at `config.address` with timeout 5 seconds
3. If successful: update config.testStatus="OK", config.testError=null
4. If timeout: update config.testStatus="UNREACHABLE", config.testError="timeout message"
5. If port error: update config.testStatus="ERROR", config.testError="port error"
6. Save updated config to DB
7. Return testStatus and message
8. Frontend badge updates immediately (no reload needed)

## Printer Connection Logic

**Class: PrinterConnectionTester**

```java
@Component
public class PrinterConnectionTester {
  private static final int TIMEOUT_MS = 5000;
  
  public TestResult testConnection(String address, int portSpeed) {
    String host = null;
    int port = 9100;
    
    if (address.startsWith("/dev/")) {
      // USB: try to open serial port
      return testSerialPort(address, portSpeed);
    } else {
      // Network: extract host:port
      String[] parts = address.split(":");
      host = parts[0];
      if (parts.length > 1) port = Integer.parseInt(parts[1]);
      return testNetworkPort(host, port);
    }
  }
  
  private TestResult testSerialPort(String portName, int speed) {
    try {
      SerialPort port = new SerialPort(portName);
      port.openPort();
      port.setParams(speed, 8, 1, 0);
      // Send ZPL status request (optional)
      port.closePort();
      return new TestResult("OK", "Serial port connected.");
    } catch (SerialPortException e) {
      return new TestResult("ERROR", "Serial port error: " + e.getMessage());
    }
  }
  
  private TestResult testNetworkPort(String host, int port) {
    try {
      Socket socket = new Socket();
      socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
      socket.close();
      return new TestResult("OK", "Network connection successful.");
    } catch (SocketTimeoutException e) {
      return new TestResult("UNREACHABLE", "Connection timeout: " + host + ":" + port);
    } catch (IOException e) {
      return new TestResult("UNREACHABLE", "Connection failed: " + e.getMessage());
    }
  }
  
  public record TestResult(String status, String message) {}
}
```

## Frontend Screen: PrinterConfig.tsx

**Component Structure:**
```
┌─ PrinterConfig
├─ State:
│  ├─ config: PrinterConfig | null
│  ├─ formData: {address, portSpeed, copiesDefault, enabled}
│  ├─ testStatus: "OK" | "UNREACHABLE" | "ERROR" | null
│  ├─ testMessage: string
│  ├─ loading: boolean
│  ├─ saveMessage: {text, tone} | null
│  └─ testLoading: boolean
│
├─ useEffect:
│  └─ On mount: GET /api/admin/printer-config → load config into formData
│
├─ Render:
│  ├─ Header: "Printer Configuration"
│  ├─ Status badge: (● testStatus) + message + lastTestedAt
│  ├─ Form:
│  │  ├─ address input
│  │  ├─ portSpeed input
│  │  ├─ copiesDefault input
│  │  └─ enabled checkbox
│  ├─ Buttons:
│  │  ├─ "Test Print" → POST test, update testStatus
│  │  └─ "Save Config" → PUT config, show success banner
│  └─ Banner: (if saveMessage) success/error message
```

**Key Behaviors:**
- On mount: fetch current config, populate form
- Test button: disabled while testLoading=true, shows "Testing..." spinner
- Save button: disabled while loading=true
- After test: update testStatus badge immediately (don't wait for page reload)
- After save: show success banner for 3 seconds, then hide
- Error handling: show banner with error message (e.g., "Invalid port speed")

## Acceptance Criteria

✅ GET /api/admin/printer-config returns config (or 404 on first run)
✅ PUT /api/admin/printer-config saves config to DB
✅ POST /api/admin/printer-config/test connects to printer and returns status
✅ Test distinguishes network timeout from port error
✅ Test result updates config.testStatus in DB
✅ Frontend loads config on mount and pre-fills form
✅ Frontend test button shows spinner while testing
✅ Frontend test status badge updates without page reload
✅ Frontend save button shows success/error banner
✅ All endpoints enforce admin-only access (via @PreAuthorize or similar)
✅ No PrintJob is sent to printer if enabled=false (executor skips)
✅ Serial port and network connections both supported (configurable)
