package com.hirelink.domain.auth.service;

import static com.hirelink.domain.auth.service.RegistrationDocumentSupport.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.hirelink.domain.auth.client.ApickRegistrationClient;
import com.hirelink.domain.auth.client.RegistrationOcrClient;
import com.hirelink.domain.auth.dto.RegistrationRenewalFrontResponse;
import com.hirelink.domain.auth.dto.RegistrationRenewalVerificationResponse;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.student.dto.StudentBadgeResponse;
import com.hirelink.domain.student.entity.StudentRegistrationVerification;
import com.hirelink.domain.student.repository.StudentProfileRepository;
import com.hirelink.domain.student.repository.StudentRegistrationVerificationRepository;
import com.hirelink.domain.student.service.StudentBadgeService;
import com.hirelink.global.exception.SocialAuthException;
import com.hirelink.global.exception.StudentProfileNotFoundException;
import com.hirelink.global.exception.WithdrawnAccountException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class StudentRegistrationRenewalService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private final UserAccountRepository users;
    private final StudentProfileRepository profiles;
    private final StudentRegistrationVerificationRepository registrations;
    private final RegistrationCipher cipher;
    private final RegistrationOcrClient ocr;
    private final ApickRegistrationClient apick;
    private final ObjectMapper mapper;
    private final StudentBadgeService badges;
    private final boolean enabled;

    public StudentRegistrationRenewalService(UserAccountRepository users, StudentProfileRepository profiles,
            StudentRegistrationVerificationRepository registrations, RegistrationCipher cipher,
            RegistrationOcrClient ocr, ApickRegistrationClient apick, ObjectMapper mapper, StudentBadgeService badges,
            @Value("${registration-verification.enabled:false}") boolean enabled) {
        this.users=users; this.profiles=profiles; this.registrations=registrations; this.cipher=cipher;
        this.ocr=ocr; this.apick=apick; this.mapper=mapper; this.badges=badges; this.enabled=enabled;
    }

    // 재촬영 실패도 이전 미확정 결과의 무효화를 유지한다. 최종 등록값은 건드리지 않는다.
    @Transactional(noRollbackFor=SocialAuthException.class, timeout=120)
    public RegistrationRenewalFrontResponse front(Long userId, MultipartFile file) {
        var registration=member(userId);
        byte[] bytes=readImage(file);
        try {
            registration.clearRenewal(); registrations.flush();
            var result=ocr.recognize(bytes,file.getContentType(),true);
            String name=text(result,"name"), country=countryCode(text(result,"nationality")), visa=text(result,"visaCode");
            String number=text(result,"registrationNumber");
            LocalDate issued=date(result,"issueDate");
            if (name.length()>100 || !number.matches("[0-9]{6}[5-8][0-9]{6}") || issued.isAfter(LocalDate.now(KOREA))) {
                throw RegistrationOcrClient.unreadable();
            }
            SocialSignupRegistrationService.requireSupportedVisa(visa);
            UUID id=UUID.randomUUID(); Instant until=Instant.now().plusSeconds(1800);
            registration.startRenewal(id,until);
            ObjectNode payload=mapper.createObjectNode().put("registrationNumber",number).put("issueDate",issued.toString());
            registration.saveRenewalPayload(cipher.encrypt(mapper.writeValueAsString(payload),aad(userId,id)));
            return new RegistrationRenewalFrontResponse(id,name,country,visa,number.substring(0,6)+"-"+number.substring(6),issued,until);
        } finally { Arrays.fill(bytes,(byte)0); }
    }

    @Transactional(noRollbackFor=SocialAuthException.class, timeout=120)
    public RegistrationRenewalVerificationResponse back(Long userId, UUID id, MultipartFile file) {
        var registration=draft(userId,id);
        ObjectNode front=payload(registration,userId,id);
        if (!front.path("issueDate").isString()) { throw frontRequired(); }
        byte[] bytes=readImage(file);
        try {
            registration.resetRenewalBack(); registrations.flush();
            var result=ocr.recognize(bytes,file.getContentType(),false);
            LocalDate expiry=date(result,"stayExpiresOn"), issued=date(front,"issueDate");
            var serial=result.path("serialNumber");
            String serialNumber=serial.isString()?serial.stringValue():null;
            if ((serialNumber!=null && !serialNumber.matches("[0-9]{11}"))
                    || (!issued.isBefore(LocalDate.of(2011,1,1)) && serialNumber==null)) { throw RegistrationOcrClient.unreadable(); }
            requireUnexpired(registration);
            apick.verify(text(front,"registrationNumber"),issued,serialNumber);
            requireUnexpired(registration);
            boolean pdf=expiry.isBefore(LocalDate.now(KOREA));
            front.remove("issueDate"); front.put("serialNumber",serialNumber);
            registration.verifyRenewalCard(expiry,Instant.now(),pdf?cipher.encrypt(mapper.writeValueAsString(front),aad(userId,id)):null);
            return response(registration,serialNumber,expiry,false,pdf);
        } finally { Arrays.fill(bytes,(byte)0); }
    }

    @Transactional(noRollbackFor=SocialAuthException.class, timeout=120)
    public RegistrationRenewalVerificationResponse stayDocument(Long userId, UUID id, MultipartFile file) {
        var registration=draft(userId,id);
        if (registration.getRenewalCardVerifiedAt()==null) {
            throw new SocialAuthException(HttpStatus.CONFLICT,"REGISTRATION_BACK_REQUIRED","등록증 앞면과 뒷면의 진위 확인을 먼저 완료해주세요.");
        }
        if (!registration.getRenewalCardExpiresOn().isBefore(LocalDate.now(KOREA))) {
            throw new SocialAuthException(HttpStatus.CONFLICT,"STAY_DOCUMENT_NOT_REQUIRED","카드의 체류기간이 유효하여 PDF 제출이 필요하지 않습니다.");
        }
        ObjectNode front=payload(registration,userId,id);
        byte[] bytes=readFile(file,true);
        try {
            registration.resetRenewalDocument(); registrations.flush();
            var result=ocr.recognizeStayDocument(bytes);
            requireUnexpired(registration);
            String number; LocalDate expiry;
            try { number=text(result,"registrationNumber"); expiry=date(result,"stayExpiresOn"); }
            catch (SocialAuthException exception) { throw RegistrationOcrClient.unreadableDocument(); }
            if (!number.matches("[0-9]{6}[5-8][0-9]{6}")) { throw RegistrationOcrClient.unreadableDocument(); }
            if (!number.equals(text(front,"registrationNumber"))) {
                throw new SocialAuthException(HttpStatus.UNPROCESSABLE_ENTITY,"STAY_DOCUMENT_MISMATCH","등록증과 증빙서류의 등록번호가 일치하지 않습니다. 본인의 PDF를 다시 선택해주세요.");
            }
            if (expiry.isBefore(LocalDate.now(KOREA))) {
                throw new SocialAuthException(HttpStatus.UNPROCESSABLE_ENTITY,"STAY_DOCUMENT_EXPIRED","증빙서류의 체류기간이 지났습니다. 유효한 PDF를 다시 선택해주세요.");
            }
            String serial=front.path("serialNumber").isString()?front.path("serialNumber").stringValue():null;
            registration.verifyRenewalDocument(expiry,Instant.now());
            return response(registration,serial,expiry,true,false);
        } finally { Arrays.fill(bytes,(byte)0); }
    }

    @Transactional(timeout=120)
    public StudentBadgeResponse confirm(Long userId, UUID id) {
        var registration=draft(userId,id);
        if (registration.getRenewalCardVerifiedAt()==null) {
            throw new SocialAuthException(HttpStatus.CONFLICT,"REGISTRATION_BACK_REQUIRED","등록증 앞면과 뒷면의 진위 확인을 먼저 완료해주세요.");
        }
        LocalDate expiry=registration.getRenewalCardExpiresOn(); Instant documentTime=null;
        if (expiry.isBefore(LocalDate.now(KOREA))) {
            expiry=registration.getRenewalDocumentExpiresOn(); documentTime=registration.getRenewalDocumentVerifiedAt();
            if (expiry==null || documentTime==null || expiry.isBefore(LocalDate.now(KOREA))) {
                throw new SocialAuthException(HttpStatus.CONFLICT,"VALID_STAY_DOCUMENT_REQUIRED","유효한 최신 체류기간 증빙을 확인해주세요. 갱신 결과가 만료되었다면 앞면부터 다시 진행해주세요.");
            }
        }
        var profile=profiles.findById(userId).orElseThrow(StudentProfileNotFoundException::new);
        boolean wasActive=badges.status(profile).active();
        registration.confirmRenewal(expiry,documentTime);
        profile.updateVerifiedStayExpiration(expiry);
        badges.issueIfEligible(profile,!wasActive);
        registrations.flush(); profiles.flush();
        return badges.status(profile);
    }

    private StudentRegistrationVerification member(Long userId) {
        var user=users.findByIdForUpdate(userId).orElseThrow(()->new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
        if (user.isWithdrawn()) { throw new WithdrawnAccountException(); }
        if (user.getRole()!=MemberRole.STUDENT) { throw new AccessDeniedException("유학생 본인의 등록증만 갱신할 수 있습니다."); }
        if (!profiles.existsById(userId)) { throw new StudentProfileNotFoundException(); }
        if (!enabled) { throw new SocialAuthException(HttpStatus.SERVICE_UNAVAILABLE,"REGISTRATION_VERIFICATION_UNAVAILABLE","등록증 확인 서비스를 이용할 수 없습니다."); }
        cipher.requireConfigured();
        return registrations.findById(userId).orElseThrow(()->new SocialAuthException(HttpStatus.CONFLICT,"REGISTRATION_REQUIRED","가입 시 등록증 인증을 완료한 회원만 갱신할 수 있습니다."));
    }
    private StudentRegistrationVerification draft(Long userId,UUID id) {
        var registration=member(userId);
        if (!id.equals(registration.getRenewalId())) { throw new SocialAuthException(HttpStatus.CONFLICT,"REGISTRATION_RENEWAL_NOT_FOUND","본인의 최신 갱신 결과를 확인해주세요."); }
        requireUnexpired(registration); return registration;
    }
    private void requireUnexpired(StudentRegistrationVerification registration) {
        if (!registration.getRenewalExpiresAt().isAfter(Instant.now())) {
            registration.clearRenewal();
            throw new SocialAuthException(HttpStatus.CONFLICT,"REGISTRATION_RENEWAL_EXPIRED","갱신 시간이 지났습니다. 앞면부터 다시 진행해주세요.");
        }
    }
    private ObjectNode payload(StudentRegistrationVerification registration,Long userId,UUID id) {
        if (registration.getRenewalPayload()==null) { throw frontRequired(); }
        return (ObjectNode)mapper.readTree(cipher.decrypt(registration.getRenewalPayload(),aad(userId,id)));
    }
    private String aad(Long userId,UUID id) { return "renewal:"+userId+":"+id; }
    private SocialAuthException frontRequired() {
        return new SocialAuthException(HttpStatus.CONFLICT,"REGISTRATION_FRONT_REQUIRED","등록증 앞면부터 다시 촬영해주세요.");
    }
    private RegistrationRenewalVerificationResponse response(StudentRegistrationVerification registration,String serial,LocalDate expiry,boolean document,boolean pdf) {
        return new RegistrationRenewalVerificationResponse(registration.getRenewalId(),serial,expiry,true,document,pdf,registration.getRenewalExpiresAt());
    }
}
