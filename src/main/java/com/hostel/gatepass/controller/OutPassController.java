package com.hostel.gatepass.controller;

import com.hostel.gatepass.dto.PassRequestDto;
import com.hostel.gatepass.dto.StatusUpdateDto;
import com.hostel.gatepass.model.OutPass;
import com.hostel.gatepass.service.OutPassService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller exposing endpoints for the Digital Hostel Out-Pass System.
 */
@RestController
@RequestMapping("/api/outpass")
@CrossOrigin(origins = "*")
public class OutPassController {

    private final OutPassService outPassService;

    public OutPassController(OutPassService outPassService) {
        this.outPassService = outPassService;
    }

    /**
     * POST /api/outpass/request
     * Student requests a new out-pass.
     */
    @PostMapping("/request")
    public ResponseEntity<OutPass> requestPass(@RequestBody PassRequestDto requestDto) {
        OutPass createdPass = outPassService.requestPass(
                requestDto.getStudentId(),
                requestDto.getReason(),
                requestDto.getOutTime(),
                requestDto.getExpectedInTime()
        );
        return new ResponseEntity<>(createdPass, HttpStatus.CREATED);
    }

    /**
     * PUT /api/outpass/{id}/status
     * Warden approves or rejects an out-pass.
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<OutPass> updateStatus(
            @PathVariable Long id,
            @RequestBody(required = false) StatusUpdateDto statusUpdateDto,
            @RequestParam(required = false) String status) {

        String newStatus = statusUpdateDto != null && statusUpdateDto.getStatus() != null
                ? statusUpdateDto.getStatus()
                : status;

        OutPass updatedPass = outPassService.updatePassStatus(id, newStatus);
        return ResponseEntity.ok(updatedPass);
    }

    /**
     * POST /api/outpass/{id}/exit
     * Security Guard marks a student as exited from campus.
     */
    @PostMapping("/{id}/exit")
    public ResponseEntity<OutPass> markExit(@PathVariable Long id) {
        OutPass updatedPass = outPassService.markExit(id);
        return ResponseEntity.ok(updatedPass);
    }

    /**
     * POST /api/outpass/{id}/return
     * Security Guard marks a student as returned to campus.
     */
    @PostMapping("/{id}/return")
    public ResponseEntity<OutPass> markReturn(@PathVariable Long id) {
        OutPass updatedPass = outPassService.markReturn(id);
        return ResponseEntity.ok(updatedPass);
    }

    /**
     * GET /api/outpass/defaulters
     * Returns a list of students who have exited and are past their expected return time.
     * Strict Constraint: Backed by traditional indexed for loop in OutPassService.
     */
    @GetMapping("/defaulters")
    public ResponseEntity<List<OutPass>> getDefaulters() {
        List<OutPass> defaulters = outPassService.getDefaulters();
        return ResponseEntity.ok(defaulters);
    }

    /**
     * GET /api/outpass
     * Returns all out-passes in the system.
     */
    @GetMapping
    public ResponseEntity<List<OutPass>> getAllPasses() {
        return ResponseEntity.ok(outPassService.getAllPasses());
    }


    /**
     * GET /api/outpass/{id}
     * Retrieves an out-pass by its ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<OutPass> getPassById(@PathVariable Long id) {
        return ResponseEntity.ok(outPassService.getPassById(id));
    }

    /**
     * POST /api/ai/suggest-reason
     * Uses Gemini AI to rewrite a student's rough leave reason into a formal sentence.
     * Called from the frontend "Suggest" button in the request form.
     */
    @PostMapping("/ai/suggest-reason")
    public ResponseEntity<Map<String, String>> suggestReason(@RequestBody Map<String, String> body) {
        String roughReason = body.getOrDefault("reason", "");
        String formal = outPassService.suggestReason(roughReason);
        Map<String, String> response = new HashMap<>();
        response.put("suggestion", formal);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/outpass/ai/chat
     * Uses Gemini AI to chat with the student about hostel rules.
     */
    @PostMapping("/ai/chat")
    public ResponseEntity<Map<String, String>> chatWithConcierge(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        String reply = outPassService.chatWithConcierge(message);
        Map<String, String> response = new HashMap<>();
        response.put("reply", reply);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/outpass/ai/magic-parse
     * Uses Gemini AI to parse a natural language string into exact dates and reason.
     */
    @PostMapping("/ai/magic-parse")
    public ResponseEntity<String> parseMagicPrompt(@RequestBody Map<String, String> body) {
        String prompt = body.getOrDefault("prompt", "");
        // We can just call GeminiService directly or route through OutPassService.
        // Since the user added it to GeminiService, we'll route it via OutPassService for consistency.
        return ResponseEntity.ok(outPassService.parseMagicPrompt(prompt));
    }
}
