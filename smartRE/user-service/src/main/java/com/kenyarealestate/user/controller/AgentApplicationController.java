package com.kenyarealestate.user.controller;

import com.kenyarealestate.user.dto.*;
import com.kenyarealestate.user.entity.AgentApplicationStatus;
import com.kenyarealestate.user.service.AgentApplicationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/agent-applications")
public class AgentApplicationController {

    private final AgentApplicationService svc;

    public AgentApplicationController(AgentApplicationService svc) {
        this.svc = svc;
    }

    @PostMapping
    public ResponseEntity<AgentApplicationResponse> submit(
            Authentication auth, @Valid @RequestBody SubmitAgentApplicationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(svc.submit(auth.getName(), req));
    }

    @GetMapping("/mine")
    public ResponseEntity<AgentApplicationResponse> mine(Authentication auth) {
        return ResponseEntity.ok(svc.getMine(auth.getName()));
    }

    @GetMapping("/admin/queue")
    public ResponseEntity<Page<AgentApplicationResponse>> adminQueue(
            @RequestParam(defaultValue = "SUBMITTED") AgentApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Sort.Direction dir = "DESC".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return ResponseEntity.ok(svc.getQueue(status, PageRequest.of(page, size, Sort.by(dir, sortBy))));
    }

    @PutMapping("/admin/{id}/review")
    public ResponseEntity<AgentApplicationResponse> review(
            @PathVariable UUID id, @Valid @RequestBody AdminAgentApplicationReviewRequest req, Authentication auth) {
        return ResponseEntity.ok(svc.review(id, req, auth.getName()));
    }
}
