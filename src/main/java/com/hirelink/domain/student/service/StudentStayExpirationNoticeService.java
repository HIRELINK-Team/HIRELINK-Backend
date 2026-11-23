package com.hirelink.domain.student.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.student.dto.StudentStayExpirationNoticeResponse;
import com.hirelink.domain.student.repository.StudentProfileRepository;
import com.hirelink.domain.student.repository.StudentRegistrationVerificationRepository;
import com.hirelink.global.exception.StudentProfileNotFoundException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class StudentStayExpirationNoticeService {
    private static final ZoneId KOREA=ZoneId.of("Asia/Seoul");
    private final UserAccountRepository users;
    private final StudentProfileRepository profiles;
    private final StudentRegistrationVerificationRepository registrations;

    public StudentStayExpirationNoticeService(UserAccountRepository users, StudentProfileRepository profiles,
            StudentRegistrationVerificationRepository registrations) {
        this.users=users; this.profiles=profiles; this.registrations=registrations;
    }

    @Transactional
    public StudentStayExpirationNoticeResponse claim(Long userId) {
        // 갱신 최종 저장과 같은 회원 잠금으로 만료일 변경 및 동시 접속을 직렬화한다.
        var user=users.findByIdForUpdate(userId).orElseThrow(()->new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
        if (user.isWithdrawn()) { throw new WithdrawnAccountException(); }
        if (user.getRole()!=MemberRole.STUDENT) { throw new AccessDeniedException("본인의 체류 만료 안내만 조회할 수 있습니다."); }
        if (!profiles.existsById(userId)) { throw new StudentProfileNotFoundException(); }
        var registration=registrations.findById(userId).orElse(null);
        if (registration==null) { return new StudentStayExpirationNoticeResponse(false,null,null,null); }
        var expiry=registration.getStayExpiresOn();
        long days=ChronoUnit.DAYS.between(LocalDate.now(KOREA),expiry);
        if (days<0 || days>30 || registration.hasExpiryNotice(expiry)) {
            return new StudentStayExpirationNoticeResponse(false,expiry,days,null);
        }
        registration.recordExpiryNotice(expiry);
        registrations.flush();
        return new StudentStayExpirationNoticeResponse(true,expiry,days,
                days==0?"오늘 체류기간이 만료돼요":"체류기간 만료까지 "+days+"일 남았어요.");
    }
}
