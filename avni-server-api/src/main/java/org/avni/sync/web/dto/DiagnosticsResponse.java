package org.avni.sync.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Response DTO for diagnostics
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiagnosticsResponse {
    
    private List<DiagnosticCheck> checks;
    private Long timestamp;
    
    public DiagnosticsResponse() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public DiagnosticsResponse(List<DiagnosticCheck> checks) {
        this();
        this.checks = checks;
    }
    
    // Getters and setters
    public List<DiagnosticCheck> getChecks() { return checks; }
    public void setChecks(List<DiagnosticCheck> checks) { this.checks = checks; }
    
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    
    /**
     * Individual diagnostic check result
     */
    public static class DiagnosticCheck {
        private String name;
        private String status; // "ok", "error", "not_implemented"
        private String message;
        
        public DiagnosticCheck() {}
        
        public DiagnosticCheck(String name, String status, String message) {
            this.name = name;
            this.status = status;
            this.message = message;
        }
        
        public static DiagnosticCheck ok(String name, String message) {
            return new DiagnosticCheck(name, "ok", message);
        }
        
        public static DiagnosticCheck error(String name, String message) {
            return new DiagnosticCheck(name, "error", message);
        }
        
        public static DiagnosticCheck notImplemented(String name, String message) {
            return new DiagnosticCheck(name, "not_implemented", message);
        }
        
        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
