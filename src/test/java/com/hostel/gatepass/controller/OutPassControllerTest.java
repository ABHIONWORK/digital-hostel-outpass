package com.hostel.gatepass.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.gatepass.dto.PassRequestDto;
import com.hostel.gatepass.dto.StatusUpdateDto;
import com.hostel.gatepass.model.OutPass;
import com.hostel.gatepass.model.OutPassStatus;
import com.hostel.gatepass.repository.OutPassRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OutPassControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutPassRepository outPassRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        outPassRepository.deleteAll();
    }

    @Test
    @DisplayName("Complete end-to-end OutPass Lifecycle: Request -> Approve -> Exit -> Return")
    void testCompleteOutPassLifecycle() throws Exception {
        LocalDateTime out = LocalDateTime.now().plusHours(1);
        LocalDateTime expectedIn = LocalDateTime.now().plusHours(5);

        PassRequestDto requestDto = new PassRequestDto("STU-100", "Hackathon", out, expectedIn);

        // 1. Student requests pass
        MvcResult createResult = mockMvc.perform(post("/api/outpass/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        OutPass createdPass = objectMapper.readValue(createResult.getResponse().getContentAsString(), OutPass.class);
        Long passId = createdPass.getId();
        assertNotNull(passId);

        // 2. Warden approves pass
        StatusUpdateDto statusUpdateDto = new StatusUpdateDto("APPROVED");
        mockMvc.perform(put("/api/outpass/" + passId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusUpdateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // 3. Security Guard marks exit
        mockMvc.perform(post("/api/outpass/" + passId + "/exit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXITED"));

        // 4. Security Guard marks return
        mockMvc.perform(post("/api/outpass/" + passId + "/return"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.actualInTime").isNotEmpty());

        OutPass finalPass = outPassRepository.findById(passId).orElseThrow();
        assertEquals(OutPassStatus.RETURNED, finalPass.getStatus());
    }

    @Test
    @DisplayName("GET /api/outpass/defaulters returns only late exited students")
    void testDefaultersEndpoint() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        OutPass lateStudent = new OutPass("STU-LATE-01", "Movie", now.minusHours(5), now.minusHours(2), OutPassStatus.EXITED);
        OutPass onTimeStudent = new OutPass("STU-ONTIME-01", "Lab", now.minusHours(1), now.plusHours(2), OutPassStatus.EXITED);

        OutPass[] seedData = new OutPass[]{lateStudent, onTimeStudent};
        for (int i = 0; i < seedData.length; i++) {
            outPassRepository.save(seedData[i]);
        }

        mockMvc.perform(get("/api/outpass/defaulters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentId").value("STU-LATE-01"));
    }

    @Test
    @DisplayName("Attempting to mark exit on a REJECTED pass returns 409 Conflict")
    void testMarkExitOnRejectedPassReturnsConflict() throws Exception {
        OutPass rejectedPass = new OutPass("STU-REJ-01", "Concert", LocalDateTime.now(), LocalDateTime.now().plusHours(3), OutPassStatus.REJECTED);
        rejectedPass = outPassRepository.save(rejectedPass);

        mockMvc.perform(post("/api/outpass/" + rejectedPass.getId() + "/exit"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cannot mark exit")));
    }
}
