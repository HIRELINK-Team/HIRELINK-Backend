package com.hirelink.domain.student.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.hirelink.domain.auth.dto.RegistrationRenewalConfirmationResponse;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.student.entity.StudentProfile;
import com.hirelink.domain.student.entity.StudentRegistrationVerification;
import com.hirelink.domain.student.entity.StudyProgramType;
import com.hirelink.domain.student.repository.StudentProfileRepository;
import com.hirelink.domain.student.repository.StudentRegistrationVerificationRepository;
import com.hirelink.domain.student.repository.StudyProgramRepository;
import com.hirelink.global.exception.SocialAuthException;
import com.hirelink.global.exception.StudentProfileNotFoundException;
import com.hirelink.global.exception.WithdrawnAccountException;
import org.springframework.http.HttpStatus;

@Service
public class StudentRegistrationStatusService {
    private final UserAccountRepository users;
    private final StudentProfileRepository profiles;
    private final StudentRegistrationVerificationRepository registrations;
    private final StudyProgramRepository programs;
    private final StudentBadgeService badges;

    public StudentRegistrationStatusService(UserAccountRepository users, StudentProfileRepository profiles,
            StudentRegistrationVerificationRepository registrations, StudyProgramRepository programs, StudentBadgeService badges) {
        this.users=users; this.profiles=profiles; this.registrations=registrations; this.programs=programs; this.badges=badges;
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public RegistrationRenewalConfirmationResponse current(Long userId) {
        var user=users.findById(userId).orElseThrow(()->new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
        if (user.isWithdrawn()) { throw new WithdrawnAccountException(); }
        if (user.getRole()!=MemberRole.STUDENT) { throw new AccessDeniedException("본인의 등록증 정보만 조회할 수 있습니다."); }
        var profile=profiles.findById(userId).orElseThrow(StudentProfileNotFoundException::new);
        var registration=registrations.findById(userId).orElseThrow(()->new SocialAuthException(
                HttpStatus.CONFLICT,"REGISTRATION_REQUIRED","등록증 인증 정보가 필요합니다."));
        return response(profile,registration);
    }

    public StudyProgramType studyProgram(StudentProfile profile) {
        return profile.getStudyProgramId()==null?null:programs.findById(profile.getStudyProgramId())
                .map(value->StudyProgramType.fromProgramName(value.getProgramName())).orElse(null);
    }

    public boolean supported(String rawVisa, StudyProgramType program) {
        return StudyProgramType.supportsRegistration(rawVisa) && program!=null;
    }

    public String displayVisa(String rawVisa, StudyProgramType program) {
        return supported(rawVisa,program)?program.getVisaCode():rawVisa;
    }

    public RegistrationRenewalConfirmationResponse response(StudentProfile profile, StudentRegistrationVerification registration) {
        var program=studyProgram(profile);
        boolean supported=supported(registration.getVisaCode(),program);
        var badge=badges.status(profile);
        return new RegistrationRenewalConfirmationResponse(profile.getUserAccount().getName(),registration.getNationality(),
                registration.getVisaCode(),displayVisa(registration.getVisaCode(),program),program,registration.getStayExpiresOn(),
                supported,supported?null:"현재 체류자격 또는 학업과정은 서비스 지원 대상이 아닙니다.",badge.active(),badge.issuedAt());
    }
}
