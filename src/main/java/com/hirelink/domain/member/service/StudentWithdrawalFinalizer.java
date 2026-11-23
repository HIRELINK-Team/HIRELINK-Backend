package com.hirelink.domain.member.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.auth.repository.AuthAccountRepository;
import com.hirelink.domain.auth.repository.SocialAuthSessionRepository;
import com.hirelink.domain.auth.repository.RefreshTokenRepository;
import com.hirelink.domain.auth.service.ProviderUnlinkClient;
import com.hirelink.domain.student.client.StudentDocumentStorageClient;
import com.hirelink.domain.student.repository.StudentEnrollmentDocumentRepository;
import com.hirelink.domain.student.repository.StudentDocumentCleanupRepository;

@Service
public class StudentWithdrawalFinalizer {
    private final UserAccountRepository users;
    private final AuthAccountRepository accounts;
    private final SocialAuthSessionRepository sessions;
    private final RefreshTokenRepository tokens;
    private final ProviderUnlinkClient unlink;
    private final StudentDocumentStorageClient storage;
    private final StudentEnrollmentDocumentRepository documents;
    private final StudentDocumentCleanupRepository cleanup;
    public StudentWithdrawalFinalizer(UserAccountRepository users,AuthAccountRepository accounts,SocialAuthSessionRepository sessions,
            RefreshTokenRepository tokens,ProviderUnlinkClient unlink,StudentDocumentStorageClient storage,
            StudentEnrollmentDocumentRepository documents,StudentDocumentCleanupRepository cleanup) {
        this.users=users; this.accounts=accounts; this.sessions=sessions; this.tokens=tokens; this.unlink=unlink; this.storage=storage; this.documents=documents; this.cleanup=cleanup;
    }
    @Transactional(timeout=120)
    public void finalizeDue(Long userId) {
        // 소셜 로그인과 같은 순서: 소셜 식별자 → 사용자. 경계 시각의 복구/최종 탈퇴를 직렬화한다.
        var connections=accounts.findAllByUserAccountIdOrderById(userId);
        connections.stream().map(a->a.getProvider().name()+":"+a.getProviderUserId()).sorted().forEach(identity->sessions.lockIdentity(hash(identity)));
        var user=users.findByIdForUpdate(userId).orElseThrow();
        Instant now=Instant.now();
        if(user.getRole()!=MemberRole.STUDENT || user.getWithdrawalRequestedAt()==null || user.getWithdrawalFinalizedAt()!=null
                || now.isBefore(user.getWithdrawalRequestedAt().plus(Duration.ofDays(30)))) return;
        var saved=documents.findAllByUserId(userId);
        for(var doc:saved) {
            if(doc.getObjectKey()!=null) storage.delete(doc.getObjectKey());
            if(doc.getDraftObjectKey()!=null) storage.delete(doc.getDraftObjectKey());
        }
        for(String type:java.util.List.of("enrollment","transcript","topik")) {
            for(var task:cleanup.findAllByObjectKeyStartingWith(type+"/"+userId+"/")) {
                storage.delete(task.getObjectKey()); cleanup.delete(task);
            }
        }
        // 외부 실패 시 완료로 표시하지 않는다. 재시도는 이미 삭제/해제된 결과도 허용한다.
        for(var account:connections) {
            unlink.unlink(account);
            sessions.deleteByIdentity(account.getProvider().name(),account.getProviderUserId());
        }
        documents.deleteAll(saved); tokens.deleteAllByUserAccountId(userId); accounts.deleteAll(connections);
        user.finalizeWithdrawal(now);
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
