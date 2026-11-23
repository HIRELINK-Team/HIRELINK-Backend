package com.hirelink.domain.student.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.employmentmatch.repository.EmploymentMatchRepository;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.student.client.StudentDocumentStorageClient;
import com.hirelink.domain.student.dto.StudentDocumentContent;
import com.hirelink.domain.student.entity.StudentDocumentId;
import com.hirelink.domain.student.repository.StudentEnrollmentDocumentRepository;
import com.hirelink.global.exception.EmploymentMatchNotFoundException;
import com.hirelink.global.exception.StudentDocumentException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class EmployerStudentDocumentService {
	private final UserAccountRepository users;
	private final EmploymentMatchRepository matches;
	private final StudentEnrollmentDocumentRepository documents;
	private final StudentDocumentStorageClient storage;
	private final Clock clock;

	public EmployerStudentDocumentService(UserAccountRepository users, EmploymentMatchRepository matches,
			StudentEnrollmentDocumentRepository documents, StudentDocumentStorageClient storage, Clock clock) {
		this.users = users;
		this.matches = matches;
		this.documents = documents;
		this.storage = storage;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public StudentDocumentContent read(Long employerId, Long matchId, String kind) {
		UserAccount employer = users.findById(employerId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (employer.isWithdrawn()) { throw new WithdrawnAccountException(); }
		if (employer.getRole() != MemberRole.EMPLOYER) { throw denied(); }
		if (!Set.of("enrollment", "transcript", "topik").contains(kind)) {
			throw new StudentDocumentException(HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT_TYPE", "서류 종류를 확인해주세요.");
		}

		var match = matches.findActiveById(matchId).orElseThrow(EmploymentMatchNotFoundException::new);
		var application = match.getEmploymentOffer().getApplication();
		if (!application.getJobPosting().getWorkplace().getEmployerUser().getId().equals(employerId)) {
			throw denied();
		}
		UserAccount student = application.getStudentProfile().getUserAccount();
		if (student.getRole() != MemberRole.STUDENT || student.getWithdrawalFinalizedAt() != null
				|| (student.isWithdrawn() && !student.canRestore(Instant.now(clock)))) { throw denied(); }

		var document = documents.findById(new StudentDocumentId(student.getId(), kind.toUpperCase(Locale.ROOT)))
				.orElseThrow(this::notRegistered);
		if (document.getRegisteredAt() == null || document.getObjectKey() == null) { throw notRegistered(); }
		if (!document.getObjectKey().startsWith(kind + "/" + student.getId() + "/")) {
			throw new StudentDocumentException(HttpStatus.BAD_GATEWAY, "STUDENT_DOCUMENT_STORAGE_FAILED", "서류 저장소를 이용할 수 없습니다.");
		}
		return storage.download(document.getObjectKey());
	}

	private AccessDeniedException denied() {
		return new AccessDeniedException("해당 서류를 열람할 수 없습니다.");
	}

	private StudentDocumentException notRegistered() {
		return new StudentDocumentException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_REGISTERED", "최종 등록한 서류가 없습니다.");
	}
}
