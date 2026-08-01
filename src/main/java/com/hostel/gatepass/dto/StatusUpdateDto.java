package com.hostel.gatepass.dto;

/**
 * Data Transfer Object for updating an out-pass status (e.g. APPROVED / REJECTED).
 */
public class StatusUpdateDto {

    private String status;

    public StatusUpdateDto() {
    }

    public StatusUpdateDto(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
