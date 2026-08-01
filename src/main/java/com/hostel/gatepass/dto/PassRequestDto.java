package com.hostel.gatepass.dto;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for requesting a new hostel out-pass.
 */
public class PassRequestDto {

    private String studentId;
    private String reason;
    private LocalDateTime outTime;
    private LocalDateTime expectedInTime;

    public PassRequestDto() {
    }

    public PassRequestDto(String studentId, String reason, LocalDateTime outTime, LocalDateTime expectedInTime) {
        this.studentId = studentId;
        this.reason = reason;
        this.outTime = outTime;
        this.expectedInTime = expectedInTime;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getOutTime() {
        return outTime;
    }

    public void setOutTime(LocalDateTime outTime) {
        this.outTime = outTime;
    }

    public LocalDateTime getExpectedInTime() {
        return expectedInTime;
    }

    public void setExpectedInTime(LocalDateTime expectedInTime) {
        this.expectedInTime = expectedInTime;
    }
}
