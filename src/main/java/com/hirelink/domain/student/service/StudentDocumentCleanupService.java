package com.hirelink.domain.student.service;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.hirelink.domain.student.client.StudentDocumentStorageClient;
import com.hirelink.domain.student.entity.StudentDocumentCleanup;
import com.hirelink.domain.student.repository.StudentDocumentCleanupRepository;
import com.hirelink.global.exception.StudentDocumentException;

@Service
public class StudentDocumentCleanupService {
	private final StudentDocumentCleanupRepository cleanup;
	private final StudentDocumentStorageClient storage;
	public StudentDocumentCleanupService(StudentDocumentCleanupRepository cleanup, StudentDocumentStorageClient storage) {
		this.cleanup = cleanup; this.storage = storage;
	}
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void reserve(String key) {
		// 외부 업로드 후 프로세스 종료·DB 롤백이 나도 연결되지 않은 파일을 회수한다.
		cleanup.saveAndFlush(new StudentDocumentCleanup(key, Instant.now().plusSeconds(3600)));
	}
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void deleteDue(String key) {
		cleanup.findDueForUpdate(key, Instant.now()).ifPresent(task -> {
			try { storage.delete(key); cleanup.delete(task); }
			catch (StudentDocumentException exception) { task.retryAfter(Instant.now().plusSeconds(300)); }
		});
	}
}
