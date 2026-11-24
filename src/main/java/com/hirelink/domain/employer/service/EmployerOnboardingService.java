package com.hirelink.domain.employer.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.hirelink.domain.employer.dto.*;
import com.hirelink.domain.employer.entity.EmployerProfile;
import com.hirelink.domain.employer.repository.EmployerProfileRepository;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.workplace.client.NtsBusinessRegistrationClient;
import com.hirelink.domain.workplace.dto.BusinessRegistrationVerifyResponse;
import com.hirelink.domain.workplace.service.BusinessRegistrationOcrService;
import com.hirelink.domain.workplace.service.WorkplaceService;
import com.hirelink.global.exception.PhoneNotVerifiedException;
import com.hirelink.global.exception.SocialAuthException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
@Transactional
public class EmployerOnboardingService {
    private final UserAccountRepository users;
    private final EmployerProfileRepository profiles;
    private final WorkplaceService workplaces;
    private final BusinessRegistrationOcrService ocr;
    private final NtsBusinessRegistrationClient nts;
    private final JdbcTemplate jdbc;

    public EmployerOnboardingService(UserAccountRepository users, EmployerProfileRepository profiles,
            WorkplaceService workplaces, BusinessRegistrationOcrService ocr, NtsBusinessRegistrationClient nts,
            JdbcTemplate jdbc) {
        this.users = users; this.profiles = profiles; this.workplaces = workplaces;
        this.ocr = ocr; this.nts = nts; this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public EmployerOnboardingStatusResponse status(Long userId) {
        employer(userId, false);
        Instant completed = profiles.findById(userId).map(EmployerProfile::getOnboardingCompletedAt).orElse(null);
        return new EmployerOnboardingStatusResponse(userId, completed == null ? "REQUIRED" : "COMPLETED",
                completed == null ? "BASIC_INFORMATION" : "DASHBOARD", completed);
    }

    public EmployerOnboardingAttemptResponse restart(Long userId, String loginSession) {
        UserAccount user = employer(userId, true);
        requireIncomplete(userId);
        if (!user.isPhoneVerified()) { throw new PhoneNotVerifiedException(); }
        requireLoginSession(loginSession);
        UUID attempt = UUID.randomUUID();
        Instant expiry = Instant.now().plusSeconds(1800);
        jdbc.update("delete from employer_onboarding_attempt where user_id=?", userId);
        jdbc.update("insert into employer_onboarding_attempt(user_id, attempt_id, login_session_id, expires_at) values (?,?,?,?)",
                userId, attempt, loginSession, Timestamp.from(expiry));
        return new EmployerOnboardingAttemptResponse(attempt, expiry);
    }

    // 재업로드 실패 시에도 이전 파일의 성공 이력을 재사용하지 않는다.
    @Transactional(noRollbackFor = RuntimeException.class)
    public EmployerOnboardingOcrResponse recognize(Long userId, String loginSession, UUID attempt, MultipartFile file) {
        requireAttempt(userId, loginSession, attempt);
        jdbc.update("update employer_onboarding_attempt set ocr_number=null, ocr_completed_at=null, "
                + "verified_business_number=null, business_verified_at=null where user_id=?", userId);
        var result = ocr.recognizeFile(file);
        requireAttempt(userId, loginSession, attempt);
        jdbc.update("update employer_onboarding_attempt set ocr_number=?, ocr_completed_at=? where user_id=?",
                normalize(result.businessRegistrationNumber()), Timestamp.from(Instant.now()), userId);
        return new EmployerOnboardingOcrResponse(result.businessRegistrationNumber());
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public BusinessRegistrationVerifyResponse verifyBusiness(Long userId, String loginSession, UUID attempt, String number) {
        requireAttempt(userId, loginSession, attempt);
        requireOcr(userId);
        jdbc.update("update employer_onboarding_attempt set verified_business_number=null, business_verified_at=null where user_id=?", userId);
        String normalized = normalize(number);
        boolean valid = nts.isActiveBusiness(normalized);
        requireAttempt(userId, loginSession, attempt);
        if (valid) {
            jdbc.update("update employer_onboarding_attempt set verified_business_number=?, business_verified_at=? where user_id=?",
                    normalized, Timestamp.from(Instant.now()), userId);
        }
        return new BusinessRegistrationVerifyResponse(formatBusiness(normalized), valid,
                valid ? "확인 가능한 사업자등록번호입니다." : "확인할 수 없는 사업자등록번호입니다.");
    }

    public EmployerOnboardingCompleteResponse complete(Long userId, String loginSession, UUID attempt, EmployerOnboardingRequest request) {
        UserAccount user = requireAttempt(userId, loginSession, attempt);
        requireOcr(userId);
        String verified = jdbc.queryForObject("select verified_business_number from employer_onboarding_attempt where user_id=?",
                String.class, userId);
        if (!normalize(request.workplace().getBusinessRegistrationNumber()).equals(verified)) {
            throw error(HttpStatus.CONFLICT, "BUSINESS_VERIFICATION_REQUIRED", "최종 사업자등록번호를 인증해주세요.");
        }
        // 기존 부분 등록 데이터가 있어도 최종 제출 전까지는 유지하며, 성공 시에만 새 입력을 반영한다.
        EmployerProfile profile = profiles.findById(userId).orElseGet(() -> new EmployerProfile(user));
        user.updateName(request.profile().getName());
        user.updateAcquisitionChannel(request.profile().getAcquisitionChannel());
        profiles.saveAndFlush(profile);
        // 이 서비스가 국세청을 다시 조회한다. 실패 시 프로필·회원 이름 변경·사업장 저장을 함께 롤백한다.
        var workplace = workplaces.createWorkplace(userId, request.workplace());
        requireAttempt(userId, loginSession, attempt);
        Instant completed = Instant.now();
        profile.completeOnboarding(completed);
        profiles.saveAndFlush(profile);
        jdbc.update("delete from employer_onboarding_attempt where user_id=?", userId);
        return new EmployerOnboardingCompleteResponse(userId, workplace.workplaceId(), completed);
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void deleteExpiredAttempts() {
        jdbc.update("delete from employer_onboarding_attempt where expires_at <= ?", Timestamp.from(Instant.now()));
    }

    private UserAccount employer(Long userId, boolean lock) {
        UserAccount user = (lock ? users.findByIdForUpdate(userId) : users.findById(userId))
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
        if (user.isWithdrawn()) { throw new WithdrawnAccountException(); }
        if (user.getRole() != MemberRole.EMPLOYER) { throw new AccessDeniedException("사업주만 사용할 수 있습니다."); }
        return user;
    }

    private void requireIncomplete(Long userId) {
        if (profiles.findById(userId).map(EmployerProfile::getOnboardingCompletedAt).isPresent()) {
            throw error(HttpStatus.CONFLICT, "EMPLOYER_ONBOARDING_COMPLETED", "이미 사업주 온보딩을 완료했습니다.");
        }
    }

    private UserAccount requireAttempt(Long userId, String loginSession, UUID attempt) {
        UserAccount user = employer(userId, true);
        requireIncomplete(userId);
        if (!user.isPhoneVerified()) { throw new PhoneNotVerifiedException(); }
        requireLoginSession(loginSession);
        Long valid = jdbc.queryForObject("select count(*) from employer_onboarding_attempt where user_id=? and attempt_id=? "
                + "and login_session_id=? and expires_at>?", Long.class, userId, attempt, loginSession, Timestamp.from(Instant.now()));
        if (valid == 0) {
            throw error(HttpStatus.CONFLICT, "EMPLOYER_ONBOARDING_RESTART_REQUIRED", "이름·유입 경로부터 다시 입력해주세요.");
        }
        return user;
    }

    private void requireLoginSession(String value) {
        if (value == null || value.isBlank()) {
            throw error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "다시 로그인해주세요.");
        }
    }

    private void requireOcr(Long userId) {
        Long count = jdbc.queryForObject("select count(*) from employer_onboarding_attempt where user_id=? "
                + "and ocr_completed_at is not null and ocr_number is not null", Long.class, userId);
        if (count == 0) { throw error(HttpStatus.CONFLICT, "BUSINESS_DOCUMENT_REQUIRED", "사업자등록증을 업로드하고 번호 인식을 완료해주세요."); }
    }

    private SocialAuthException error(HttpStatus status, String code, String message) { return new SocialAuthException(status, code, message); }
    private String normalize(String number) { return number.replace("-", ""); }
    private String formatBusiness(String number) { return number.substring(0, 3) + "-" + number.substring(3, 5) + "-" + number.substring(5); }
}
