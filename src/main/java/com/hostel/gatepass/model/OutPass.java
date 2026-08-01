package com.hostel.gatepass.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Core entity representing a student's digital hostel out-pass.
 */
@Entity
@Table(name = "out_passes")
public class OutPass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String studentId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime outTime;

    @Column(nullable = false)
    private LocalDateTime expectedInTime;

    @Column
    private LocalDateTime actualInTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutPassStatus status;

    /**
     * AI-generated risk level for this pass request: LOW, MEDIUM, or HIGH.
     * Populated by Gemini on creation; null if AI service was unavailable.
     */
    @Column(length = 20)
    private String aiRiskLevel;

    /**
     * One-line AI-generated warden advisory note summarising the risk reasoning.
     */
    @Column(length = 1000)
    private String aiSummary;

    public OutPass() {
    }

    public OutPass(String studentId, String reason, LocalDateTime outTime, LocalDateTime expectedInTime, OutPassStatus status) {
        this.studentId = studentId;
        this.reason = reason;
        this.outTime = outTime;
        this.expectedInTime = expectedInTime;
        this.status = status;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getActualInTime() {
        return actualInTime;
    }

    public void setActualInTime(LocalDateTime actualInTime) {
        this.actualInTime = actualInTime;
    }

    public OutPassStatus getStatus() {
        return status;
    }

    public void setStatus(OutPassStatus status) {
        this.status = status;
    }

    public String getAiRiskLevel() {
        return aiRiskLevel;
    }

    public void setAiRiskLevel(String aiRiskLevel) {
        this.aiRiskLevel = aiRiskLevel;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public void setAiSummary(String aiSummary) {
        this.aiSummary = aiSummary;
    }
}
