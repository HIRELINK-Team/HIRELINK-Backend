package com.hirelink.domain.student.service;

import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.hirelink.domain.student.repository.StudentDocumentCleanupRepository;

@Component
public class StudentDocumentCleanupScheduler {
	private final StudentDocumentCleanupRepository cleanup;
	private final StudentDocumentCleanupService service;
	public StudentDocumentCleanupScheduler(StudentDocumentCleanupRepository cleanup, StudentDocumentCleanupService service) {
		this.cleanup = cleanup; this.service = service;
	}
	@Scheduled(fixedDelayString = "${student-document.cleanup-delay:60000}", initialDelayString = "${student-document.cleanup-initial-delay:60000}")
	public void clean() {
		cleanup.findTop20ByAvailableAtBeforeOrderByAvailableAt(Instant.now()).forEach(task -> service.deleteDue(task.getObjectKey()));
	}
}
