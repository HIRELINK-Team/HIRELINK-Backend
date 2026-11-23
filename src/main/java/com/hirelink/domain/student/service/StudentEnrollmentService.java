package com.hirelink.domain.student.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.student.client.EnrollmentOcrClient;
import com.hirelink.domain.student.client.StudentDocumentStorageClient;
import com.hirelink.domain.student.dto.EnrollmentDocumentResponse;
import com.hirelink.domain.student.dto.EnrollmentExtraction;
import com.hirelink.domain.student.dto.EnrollmentPreviewResponse;
import com.hirelink.domain.student.entity.StudentDocumentCleanup;
import com.hirelink.domain.student.entity.StudentEnrollmentDocument;
import com.hirelink.domain.student.entity.StudentProfile;
import com.hirelink.domain.student.repository.StudentDocumentCleanupRepository;
import com.hirelink.domain.student.repository.StudentEnrollmentDocumentRepository;
import com.hirelink.domain.student.repository.StudentProfileRepository;
import com.hirelink.global.exception.StudentDocumentException;
import com.hirelink.global.exception.StudentProfileNotFoundException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class StudentEnrollmentService {
	private static final int MAX_BYTES = 5 * 1024 * 1024;
	private final StudentBadgeService badges;
	private final UserAccountRepository users;
	private final StudentProfileRepository profiles;
	private final StudentEnrollmentDocumentRepository documents;
	private final StudentDocumentCleanupRepository cleanup;
	private final StudentDocumentCleanupService cleaner;
	private final EnrollmentOcrClient ocr;
	private final StudentDocumentStorageClient storage;

	public StudentEnrollmentService(UserAccountRepository users, StudentProfileRepository profiles,
			StudentEnrollmentDocumentRepository documents, StudentDocumentCleanupRepository cleanup,
			StudentDocumentCleanupService cleaner, EnrollmentOcrClient ocr, StudentDocumentStorageClient storage, StudentBadgeService badges) {
		this.badges = badges;
		this.users = users; this.profiles = profiles; this.documents = documents; this.cleanup = cleanup;
		this.cleaner = cleaner; this.ocr = ocr; this.storage = storage;
	}

	@Transactional(timeout = 120)
	public EnrollmentPreviewResponse upload(Long userId, MultipartFile file) {
		student(userId);
		byte[] bytes = read(file);
		try {
			String contentType = contentType(file, bytes);
			EnrollmentExtraction extracted = ocr.recognize(bytes, contentType);
			short grade = extracted.supportedGrade();
			UUID id = UUID.randomUUID();
			String key = "enrollment/" + userId + "/" + id;
			cleaner.reserve(key);
			storage.upload(key, bytes, contentType);
			StudentEnrollmentDocument document = documents.findById(userId).orElseGet(() -> new StudentEnrollmentDocument(userId));
			retire(document.getDraftObjectKey());
			document.stage(id, key, extracted);
			documents.saveAndFlush(document);
			cleanup.deleteById(key);
			return new EnrollmentPreviewResponse(id, extracted.universityName(), extracted.campusName(), grade, extracted);
		} finally { Arrays.fill(bytes, (byte) 0); }
	}

	@Transactional(timeout = 120)
	public EnrollmentDocumentResponse confirm(Long userId, UUID draftId) {
		StudentProfile profile = student(userId);
		StudentEnrollmentDocument document = documents.findById(userId).orElseThrow(this::draftMissing);
		if (!draftId.equals(document.getDraftId())) { throw draftMissing(); }
		short grade = document.extraction().supportedGrade();
		retire(document.getObjectKey());
		document.confirm(grade, Instant.now());
		profile.updateEnrollment(document.getUniversityName(), grade);
		documents.flush();
		badges.issueIfEligible(profile, false);
		profiles.flush();
		return response(document);
	}

	@Transactional
	public EnrollmentDocumentResponse current(Long userId) {
		student(userId);
		StudentEnrollmentDocument document = documents.findById(userId).orElseThrow(this::notRegistered);
		if (document.getRegisteredAt() == null) { throw notRegistered(); }
		return response(document);
	}

	StudentProfile student(Long userId) {
		var user = users.findByIdForUpdate(userId).orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) { throw new WithdrawnAccountException(); }
		if (user.getRole() != MemberRole.STUDENT) { throw new AccessDeniedException("유학생 본인의 서류만 등록할 수 있습니다."); }
		return profiles.findById(userId).orElseThrow(StudentProfileNotFoundException::new);
	}
	void retire(String key) {
		if (key == null) { return; }
		cleanup.save(new StudentDocumentCleanup(key, Instant.now()));
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override public void afterCommit() {
				try { cleaner.deleteDue(key); }
				catch (RuntimeException exception) {
					// 이미 확정된 등록 응답은 유지하고 DB에 남은 삭제 작업은 스케줄러가 재시도한다.
				}
			}
		});
	}
	byte[] read(MultipartFile file) {
		if (file == null || file.isEmpty()) { throw EnrollmentExtraction.unreadable(); }
		if (file.getSize() > MAX_BYTES) { throw tooLarge(); }
		try (var stream = file.getInputStream()) {
			byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
			if (bytes.length > MAX_BYTES) { Arrays.fill(bytes, (byte) 0); throw tooLarge(); }
			return bytes;
		} catch (IOException exception) { throw EnrollmentExtraction.unreadable(); }
	}
	String contentType(MultipartFile file, byte[] bytes) {
		String type = file.getContentType();
		if (("application/pdf".equals(type) && prefix(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII)))
				|| ("image/png".equals(type) && prefix(bytes, new byte[] {(byte) 137,80,78,71,13,10,26,10}))
				|| ("image/jpeg".equals(type) && prefix(bytes, new byte[] {(byte) 255,(byte) 216,(byte) 255}))) { return type; }
		throw new StudentDocumentException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "PDF, JPG, PNG 파일을 등록해주세요.");
	}
	private boolean prefix(byte[] bytes, byte[] prefix) {
		return bytes.length >= prefix.length && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
	}
	private StudentDocumentException tooLarge() { return new StudentDocumentException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "서류 파일은 5MB 이하여야 합니다."); }
	private StudentDocumentException draftMissing() { return new StudentDocumentException(HttpStatus.CONFLICT, "ENROLLMENT_DRAFT_NOT_FOUND", "본인의 최신 추출 결과를 다시 확인해주세요."); }
	private StudentDocumentException notRegistered() { return new StudentDocumentException(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_REGISTERED", "최종 등록한 재학증명서가 없습니다."); }
	private EnrollmentDocumentResponse response(StudentEnrollmentDocument document) {
		return new EnrollmentDocumentResponse(document.getUniversityName(), document.getCampusName(), document.getGradeLevel(), document.getRegisteredAt());
	}
}
