package com.kenyarealestate.verification.service;

import com.kenyarealestate.verification.dto.report.AdminReportResolveRequest;
import com.kenyarealestate.verification.dto.report.CreateReportRequest;
import com.kenyarealestate.verification.dto.report.ReportResponse;
import com.kenyarealestate.verification.entity.Report;
import com.kenyarealestate.verification.enums.ReportReason;
import com.kenyarealestate.verification.enums.ReportStatus;
import com.kenyarealestate.verification.enums.ReportTargetType;
import com.kenyarealestate.verification.exception.NotFoundException;
import com.kenyarealestate.verification.exception.VerificationException;
import com.kenyarealestate.verification.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ReportRepository reportRepo;

    @InjectMocks private ReportService reportService;

    private Report buildReport(ReportStatus status) {
        return Report.builder()
                .id(UUID.randomUUID())
                .reporterId(UUID.randomUUID())
                .targetType(ReportTargetType.LISTING)
                .targetId(UUID.randomUUID())
                .reason(ReportReason.FAKE_LISTING)
                .details("Seller asked for off-platform payment")
                .status(status)
                .build();
    }

    private CreateReportRequest createRequest() {
        CreateReportRequest req = new CreateReportRequest();
        req.setTargetType(ReportTargetType.LISTING);
        req.setTargetId(UUID.randomUUID());
        req.setReason(ReportReason.SCAM_AGENT);
        req.setDetails("Asked me to pay via M-Pesa before viewing");
        return req;
    }

    @BeforeEach
    void setup() {
        when(reportRepo.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_savesReportWithReporterIdAndOpenStatus() {
        UUID reporterId = UUID.randomUUID();
        CreateReportRequest req = createRequest();

        ReportResponse res = reportService.create(reporterId, req);

        assertEquals(reporterId, res.getReporterId());
        assertEquals(ReportTargetType.LISTING, res.getTargetType());
        assertEquals(ReportReason.SCAM_AGENT, res.getReason());
        assertEquals(ReportStatus.OPEN, res.getStatus());
        verify(reportRepo).save(any(Report.class));
    }

    @Test
    void getQueue_returnsReportsForRequestedStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Report> page = new PageImpl<>(List.of(buildReport(ReportStatus.OPEN)));
        when(reportRepo.findByStatus(ReportStatus.OPEN, pageable)).thenReturn(page);

        Page<ReportResponse> res = reportService.getQueue(ReportStatus.OPEN, pageable);

        assertEquals(1, res.getContent().size());
        assertEquals(ReportStatus.OPEN, res.getContent().get(0).getStatus());
    }

    @Test
    void getMine_returnsReportsForReporter() {
        UUID reporterId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Report> page = new PageImpl<>(List.of(buildReport(ReportStatus.OPEN)));
        when(reportRepo.findByReporterId(reporterId, pageable)).thenReturn(page);

        Page<ReportResponse> res = reportService.getMine(reporterId, pageable);

        assertEquals(1, res.getContent().size());
        verify(reportRepo).findByReporterId(reporterId, pageable);
    }

    @Test
    void resolve_marksReportResolved_andStampsAdmin() {
        Report report = buildReport(ReportStatus.OPEN);
        UUID adminId = UUID.randomUUID();
        when(reportRepo.findById(report.getId())).thenReturn(Optional.of(report));

        AdminReportResolveRequest req = new AdminReportResolveRequest();
        req.setDecision("resolved");
        req.setNotes("Verified the listing was fake and removed it");

        ReportResponse res = reportService.resolve(report.getId(), req, adminId);

        assertEquals(ReportStatus.RESOLVED, res.getStatus());
        assertEquals(adminId, res.getResolvedBy());
        assertNotNull(res.getResolvedAt());
        assertEquals("Verified the listing was fake and removed it", res.getAdminNotes());
    }

    @Test
    void resolve_marksReportDismissed() {
        Report report = buildReport(ReportStatus.OPEN);
        UUID adminId = UUID.randomUUID();
        when(reportRepo.findById(report.getId())).thenReturn(Optional.of(report));

        AdminReportResolveRequest req = new AdminReportResolveRequest();
        req.setDecision("DISMISSED");

        ReportResponse res = reportService.resolve(report.getId(), req, adminId);

        assertEquals(ReportStatus.DISMISSED, res.getStatus());
    }

    @Test
    void resolve_rejectsInvalidDecision() {
        Report report = buildReport(ReportStatus.OPEN);
        when(reportRepo.findById(report.getId())).thenReturn(Optional.of(report));

        AdminReportResolveRequest req = new AdminReportResolveRequest();
        req.setDecision("MAYBE");

        assertThrows(VerificationException.class,
                () -> reportService.resolve(report.getId(), req, UUID.randomUUID()));
        verify(reportRepo, never()).save(any());
    }

    @Test
    void resolve_throwsWhenReportNotFound() {
        UUID missingId = UUID.randomUUID();
        when(reportRepo.findById(missingId)).thenReturn(Optional.empty());

        AdminReportResolveRequest req = new AdminReportResolveRequest();
        req.setDecision("RESOLVED");

        assertThrows(NotFoundException.class,
                () -> reportService.resolve(missingId, req, UUID.randomUUID()));
        verify(reportRepo, never()).save(any());
    }
}
