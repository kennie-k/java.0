package com.kenyarealestate.verification.repository;

import com.kenyarealestate.verification.entity.Report;
import com.kenyarealestate.verification.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {
    Page<Report> findByStatus(ReportStatus status, Pageable pageable);
    Page<Report> findByReporterId(UUID reporterId, Pageable pageable);
}
