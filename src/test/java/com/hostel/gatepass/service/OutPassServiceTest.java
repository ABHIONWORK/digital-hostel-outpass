package com.hostel.gatepass.service;

import com.hostel.gatepass.ai.AiPassAnalysis;
import com.hostel.gatepass.ai.GeminiService;
import com.hostel.gatepass.model.OutPass;
import com.hostel.gatepass.model.OutPassStatus;
import com.hostel.gatepass.repository.OutPassRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OutPassService using Mockito and JUnit 5.
 * Strictly enforces that all mock data setup and assertions use standard indexed for loops only.
 */
@ExtendWith(MockitoExtension.class)
class OutPassServiceTest {

    @Mock
    private OutPassRepository outPassRepository;

    @Mock
    private GeminiService geminiService;

    @InjectMocks
    private OutPassService outPassService;

    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
        // Lenient: only activates when requestPass() is tested; won't fail unrelated tests.
        lenient().when(geminiService.analysePassRequest(any(), any(), any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(new AiPassAnalysis("LOW", "AI stub: low risk pass for unit test.", false));
        lenient().when(outPassRepository.findByStudentId(any())).thenReturn(java.util.Collections.emptyList());
    }

    @Test
    @DisplayName("Edge Case 1: Student tries to markExit on a pass that was REJECTED - Throws IllegalStateException")
    void testMarkExitOnRejectedPassThrowsException() {
        OutPass rejectedPass = new OutPass("STU-101", "Weekend Visit", now.minusHours(1), now.plusHours(5), OutPassStatus.REJECTED);
        rejectedPass.setId(10L);

        when(outPassRepository.findById(10L)).thenReturn(Optional.of(rejectedPass));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> outPassService.markExit(10L)
        );

        assertTrue(exception.getMessage().contains("Cannot mark exit: Pass is not in APPROVED state"));
        verify(outPassRepository, never()).save(any(OutPass.class));
    }

    @Test
    @DisplayName("Edge Case 2: getDefaulters correctly identifies late student while ignoring within-time and non-exited students")
    void testGetDefaultersIdentifiesLateStudentOnly() {
        // Prepare test cases
        OutPass lateExitedPass = new OutPass("STU-LATE", "Medical Emergency", now.minusHours(6), now.minusHours(2), OutPassStatus.EXITED);
        lateExitedPass.setId(1L);

        OutPass onTimeExitedPass = new OutPass("STU-ONTIME", "Library", now.minusHours(1), now.plusHours(3), OutPassStatus.EXITED);
        onTimeExitedPass.setId(2L);

        OutPass lateApprovedNotExitedPass = new OutPass("STU-APPROVED-LATE", "Market", now.minusHours(5), now.minusHours(1), OutPassStatus.APPROVED);
        lateApprovedNotExitedPass.setId(3L);

        OutPass returnedPass = new OutPass("STU-RETURNED", "Dinner", now.minusHours(4), now.minusHours(2), OutPassStatus.RETURNED);
        returnedPass.setId(4L);

        OutPass[] passArray = new OutPass[]{lateExitedPass, onTimeExitedPass, lateApprovedNotExitedPass, returnedPass};

        // Strict Constraint: Ensure all mock data setup in Java uses standard indexed for loops only
        List<OutPass> mockDatabaseList = new ArrayList<>();
        for (int i = 0; i < passArray.length; i++) {
            mockDatabaseList.add(passArray[i]);
        }

        when(outPassRepository.findAll()).thenReturn(mockDatabaseList);

        List<OutPass> defaulters = outPassService.getDefaulters();

        // Verify only 1 defaulter was detected (the late student who has EXITED)
        assertEquals(1, defaulters.size());
        assertEquals("STU-LATE", defaulters.get(0).getStudentId());

        // Strict Constraint: Verify using indexed for loop that no non-defaulter slipped through
        for (int i = 0; i < defaulters.size(); i++) {
            OutPass p = defaulters.get(i);
            assertEquals(OutPassStatus.EXITED, p.getStatus());
            assertTrue(now.isAfter(p.getExpectedInTime()));
        }
    }

    @Test
    @DisplayName("Warden approves a PENDING pass successfully")
    void testUpdatePassStatusToApproved() {
        OutPass pendingPass = new OutPass("STU-201", "Home Visit", now.plusHours(1), now.plusDays(2), OutPassStatus.PENDING);
        pendingPass.setId(5L);

        when(outPassRepository.findById(5L)).thenReturn(Optional.of(pendingPass));
        when(outPassRepository.save(any(OutPass.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OutPass updated = outPassService.updatePassStatus(5L, "APPROVED");

        assertEquals(OutPassStatus.APPROVED, updated.getStatus());
        verify(outPassRepository).save(pendingPass);
    }

    @Test
    @DisplayName("Security Guard marks exit for an APPROVED pass successfully")
    void testMarkExitOnApprovedPass() {
        OutPass approvedPass = new OutPass("STU-301", "Project Meet", now, now.plusHours(4), OutPassStatus.APPROVED);
        approvedPass.setId(20L);

        when(outPassRepository.findById(20L)).thenReturn(Optional.of(approvedPass));
        when(outPassRepository.save(any(OutPass.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OutPass exited = outPassService.markExit(20L);

        assertEquals(OutPassStatus.EXITED, exited.getStatus());
        verify(outPassRepository).save(approvedPass);
    }

    @Test
    @DisplayName("Security Guard marks return for an EXITED pass successfully")
    void testMarkReturnOnExitedPass() {
        OutPass exitedPass = new OutPass("STU-401", "Shopping", now.minusHours(3), now.plusHours(1), OutPassStatus.EXITED);
        exitedPass.setId(30L);

        when(outPassRepository.findById(30L)).thenReturn(Optional.of(exitedPass));
        when(outPassRepository.save(any(OutPass.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OutPass returned = outPassService.markReturn(30L);

        assertEquals(OutPassStatus.RETURNED, returned.getStatus());
        assertNotNull(returned.getActualInTime());
        verify(outPassRepository).save(exitedPass);
    }

    @Test
    @DisplayName("Multiple defaulters test with traditional indexed for-loop verification")
    void testMultipleDefaulters() {
        OutPass p1 = new OutPass("STU-1", "Reason 1", now.minusHours(6), now.minusHours(4), OutPassStatus.EXITED);
        OutPass p2 = new OutPass("STU-2", "Reason 2", now.minusHours(5), now.minusHours(2), OutPassStatus.EXITED);
        OutPass p3 = new OutPass("STU-3", "Reason 3", now.minusHours(1), now.plusHours(2), OutPassStatus.EXITED);

        OutPass[] arr = new OutPass[]{p1, p2, p3};
        List<OutPass> mockList = new ArrayList<>();
        for (int i = 0; i < arr.length; i++) {
            mockList.add(arr[i]);
        }

        when(outPassRepository.findAll()).thenReturn(mockList);

        List<OutPass> defaulters = outPassService.getDefaulters(now);
        assertEquals(2, defaulters.size());

        for (int i = 0; i < defaulters.size(); i++) {
            OutPass p = defaulters.get(i);
            assertTrue(p.getStudentId().equals("STU-1") || p.getStudentId().equals("STU-2"));
        }
    }
}
