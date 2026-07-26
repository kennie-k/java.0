package com.kenyarealestate.verification.service;

import com.kenyarealestate.verification.dto.report.*;
import com.kenyarealestate.verification.entity.Report;
import com.kenyarealestate.verification.enums.ReportStatus;
import com.kenyarealestate.verification.exception.NotFoundException;
import com.kenyarealestate.verification.exception.VerificationException;
import com.kenyarealestate.verification.repository.ReportRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class ReportService {

    private final ReportRepository reportRepo;

    public ReportService(ReportRepository reportRepo) {
        this.reportRepo = reportRepo;
    }

    public ReportResponse create(UUID reporterId, CreateReportRequest req) {
        Report report = Report.builder()
                .reporterId(reporterId)
                .targetType(req.getTargetType())
                .targetId(req.getTargetId())
                .reason(req.getReason())
                .details(req.getDetails())
                .build();
        return toResponse(reportRepo.save(report));
    }

    @Transactional(readOnly = true)
    public Page<ReportResponse> getQueue(ReportStatus status, Pageable pageable) {
        return reportRepo.findByStatus(status, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ReportResponse> getMine(UUID reporterId, Pageable pageable) {
        return reportRepo.findByReporterId(reporterId, pageable).map(this::toResponse);
    }

    public ReportResponse resolve(UUID reportId, AdminReportResolveRequest req, UUID adminId) {
        Report report = reportRepo.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found: " + reportId));

        switch (req.getDecision().toUpperCase()) {
            case "RESOLVED" -> report.setStatus(ReportStatus.RESOLVED);
            case "DISMISSED" -> report.setStatus(ReportStatus.DISMISSED);
            default -> throw new VerificationException("Invalid decision. Must be RESOLVED or DISMISSED");
        }
        report.setAdminNotes(req.getNotes());
        report.setResolvedBy(adminId);
        report.setResolvedAt(LocalDateTime.now());

        return toResponse(reportRepo.save(report));
    }

    private ReportResponse toResponse(Report r) {
        return ReportResponse.builder()
                .id(r.getId()).reporterId(r.getReporterId())
                .targetType(r.getTargetType()).targetId(r.getTargetId())
                .reason(r.getReason()).details(r.getDetails())
                .status(r.getStatus()).adminNotes(r.getAdminNotes())
                .resolvedBy(r.getResolvedBy()).resolvedAt(r.getResolvedAt())
                .createdAt(r.getCreatedAt()).updatedAt(r.getUpdatedAt())
                .build();
    }
}
