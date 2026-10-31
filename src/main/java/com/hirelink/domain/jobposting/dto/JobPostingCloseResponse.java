package com.hirelink.domain.jobposting.dto;

import java.time.Instant;

import com.hirelink.domain.jobposting.entity.JobPostingStatus;

public record JobPostingCloseResponse(Long jobPostingId, JobPostingStatus status, Instant closedAt) {
}
