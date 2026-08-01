package com.hostel.gatepass.service;

import com.hostel.gatepass.ai.AiPassAnalysis;
import com.hostel.gatepass.ai.GeminiService;
import com.hostel.gatepass.model.OutPass;
import com.hostel.gatepass.model.OutPassStatus;
import com.hostel.gatepass.repository.OutPassRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service layer implementing the core state machine logic for digital hostel out-passes.
 * Strict Constraint: All iterations use traditional indexed for loops exclusively.
 */
@Service
@Transactional
public class OutPassService {

    private final OutPassRepository outPassRepository;
    private final GeminiService geminiService;

    public OutPassService(OutPassRepository outPassRepository, GeminiService geminiService) {
        this.outPassRepository = outPassRepository;
        this.geminiService = geminiService;
    }

    /**
     * Requests a new hostel out-pass with initial PENDING status.
     *
     * @param studentId  ID of the student requesting the pass
     * @param reason     Reason for leaving campus
     * @param out        Proposed exit time
     * @param expectedIn Expected return time
     * @return The saved OutPass entity
     */
    public OutPass requestPass(String studentId, String reason, LocalDateTime out, LocalDateTime expectedIn) {
        if (studentId == null || studentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Student ID cannot be null or empty.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Reason cannot be null or empty.");
        }
        if (out == null || expectedIn == null) {
            throw new IllegalArgumentException("Out time and expected in time cannot be null.");
        }
        if (out.isAfter(expectedIn)) {
            throw new IllegalArgumentException("Out time cannot be after expected in time.");
        }

        OutPass outPass = new OutPass(studentId, reason, out, expectedIn, OutPassStatus.PENDING);

        // Fetch historical context for AI Policy Engine
        List<OutPass> history = outPassRepository.findByStudentId(studentId);
        int totalPasses = history.size();
        int lateReturns = 0;
        
        for (int i = 0; i < history.size(); i++) {
            OutPass pastPass = history.get(i);
            if (OutPassStatus.RETURNED.equals(pastPass.getStatus()) 
                    && pastPass.getActualInTime() != null 
                    && pastPass.getExpectedInTime() != null) {
                if (pastPass.getActualInTime().isAfter(pastPass.getExpectedInTime())) {
                    lateReturns++;
                }
            } else if (OutPassStatus.EXITED.equals(pastPass.getStatus()) 
                    && pastPass.getExpectedInTime() != null) {
                if (LocalDateTime.now().isAfter(pastPass.getExpectedInTime())) {
                    lateReturns++; // Currently outside and late
                }
            }
        }

        // AI Risk Analysis: call Gemini to score the request and potentially auto-approve.
        // Wrapped in try-catch so a Gemini failure never blocks pass creation.
        try {
            AiPassAnalysis analysis = geminiService.analysePassRequest(studentId, reason, out, expectedIn, totalPasses, lateReturns);
            outPass.setAiRiskLevel(analysis.getRiskLevel());
            
            if (analysis.isAutoApprove()) {
                outPass.setStatus(OutPassStatus.APPROVED);
                outPass.setAiSummary("⚡ Auto-Approved by AI. " + analysis.getSummary());
            } else {
                outPass.setAiSummary(analysis.getSummary());
            }
        } catch (Exception ex) {
            System.err.println("[OutPassService] Gemini analysis failed, saving pass without AI data: " + ex.getMessage());
        }

        return outPassRepository.save(outPass);
    }

    /**
     * Updates the status of an existing pass (used by Wardens to approve/reject).
     *
     * @param passId    ID of the pass to update
     * @param newStatus Target status string (e.g., APPROVED, REJECTED)
     * @return The updated OutPass entity
     */
    public OutPass updatePassStatus(Long passId, String newStatus) {
        OutPass pass = getPassById(passId);

        OutPassStatus targetStatus;
        try {
            targetStatus = OutPassStatus.valueOf(newStatus.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid status value: " + newStatus);
        }

        // Only Wardens typically move PENDING passes to APPROVED or REJECTED
        if (pass.getStatus() != OutPassStatus.PENDING) {
            throw new IllegalStateException("Status can only be updated for PENDING passes. Current status: " + pass.getStatus());
        }

        pass.setStatus(targetStatus);
        return outPassRepository.save(pass);
    }

    /**
     * Marks the student as EXITED from the campus gate (used by Security Guards).
     * Validates that the pass status is APPROVED.
     *
     * @param passId ID of the pass
     * @return The updated OutPass entity
     */
    public OutPass markExit(Long passId) {
        OutPass pass = getPassById(passId);

        if (pass.getStatus() != OutPassStatus.APPROVED) {
            throw new IllegalStateException("Cannot mark exit: Pass is not in APPROVED state. Current status: " + pass.getStatus());
        }

        pass.setStatus(OutPassStatus.EXITED);
        return outPassRepository.save(pass);
    }

    /**
     * Marks the student as RETURNED to the campus gate (used by Security Guards).
     * Updates ActualInTime and sets status to RETURNED.
     *
     * @param passId ID of the pass
     * @return The updated OutPass entity
     */
    public OutPass markReturn(Long passId) {
        return markReturn(passId, LocalDateTime.now());
    }

    /**
     * Overloaded markReturn method allowing explicit actualInTime for deterministic testing or manual audit.
     *
     * @param passId       ID of the pass
     * @param actualInTime Time when student returned
     * @return The updated OutPass entity
     */
    public OutPass markReturn(Long passId, LocalDateTime actualInTime) {
        OutPass pass = getPassById(passId);

        if (pass.getStatus() != OutPassStatus.EXITED) {
            throw new IllegalStateException("Cannot mark return: Pass is not in EXITED state. Current status: " + pass.getStatus());
        }

        pass.setActualInTime(actualInTime != null ? actualInTime : LocalDateTime.now());
        pass.setStatus(OutPassStatus.RETURNED);
        return outPassRepository.save(pass);
    }

    /**
     * Returns a list of all out-passes where status is 'EXITED' and current time is past 'ExpectedInTime'.
     * Strict Constraint: Uses standard indexed for loop with an 'i' counter. No streams or enhanced for-loops.
     *
     * @return List of late out-passes
     */
    public List<OutPass> getDefaulters() {
        return getDefaulters(LocalDateTime.now());
    }

    /**
     * Overloaded getDefaulters method allowing explicit evaluation time for deterministic testing.
     * Strict Constraint: Uses standard indexed for loop with an 'i' counter. No streams or enhanced for-loops.
     *
     * @param currentTime Reference time to evaluate lateness against
     * @return List of late out-passes
     */
    public List<OutPass> getDefaulters(LocalDateTime currentTime) {
        List<OutPass> allPasses = outPassRepository.findAll();
        List<OutPass> defaulters = new ArrayList<>();

        for (int i = 0; i < allPasses.size(); i++) {
            OutPass pass = allPasses.get(i);
            if (OutPassStatus.EXITED.equals(pass.getStatus())
                    && pass.getExpectedInTime() != null
                    && currentTime.isAfter(pass.getExpectedInTime())) {
                defaulters.add(pass);
            }
        }

        return defaulters;
    }

    /**
     * Retrieves an OutPass by ID or throws an exception if not found.
     *
     * @param passId ID of the pass
     * @return OutPass entity
     */
    public OutPass getPassById(Long passId) {
        return outPassRepository.findById(passId)
                .orElseThrow(() -> new IllegalArgumentException("OutPass not found with ID: " + passId));
    }

    /**
     * Retrieves all out-passes in the system.
     *
     * @return List of all OutPass entities
     */
    public List<OutPass> getAllPasses() {
        return outPassRepository.findAll();
    }

    /**
     * Uses Gemini to rewrite a student's rough leave reason as a formal, professional sentence.
     *
     * @param roughReason The student's informal reason text
     * @return A polished formal reason string
     */
    public String suggestReason(String roughReason) {
        if (roughReason == null || roughReason.trim().isEmpty()) {
            throw new IllegalArgumentException("Reason cannot be empty for suggestion.");
        }
        return geminiService.suggestFormalReason(roughReason);
    }

    /**
     * Delegates a chat message to the Gemini AI Campus Concierge.
     *
     * @param message The student's message
     * @return The AI's reply
     */
    public String chatWithConcierge(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Please type a message.";
        }
        return geminiService.chatWithConcierge(message);
    }
}
