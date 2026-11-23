package com.hirelink.domain.member.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.auth.repository.AuthAccountRepository;
import com.hirelink.domain.auth.repository.RefreshTokenRepository;
import com.hirelink.domain.auth.service.ProviderTokenProtector;
import com.hirelink.domain.auth.service.ProviderUnlinkClient;
import com.hirelink.global.exception.SocialAuthException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class StudentWithdrawalService {
    private final UserAccountRepository users;
    private final AuthAccountRepository accounts;
    private final RefreshTokenRepository refreshTokens;
    private final ProviderTokenProtector protector;
    private final ProviderUnlinkClient unlink;
    public StudentWithdrawalService(UserAccountRepository users,AuthAccountRepository accounts,RefreshTokenRepository refreshTokens,ProviderTokenProtector protector,ProviderUnlinkClient unlink) {
        this.users=users; this.accounts=accounts; this.refreshTokens=refreshTokens; this.protector=protector; this.unlink=unlink;
    }
    @Transactional
    public Result request(Long userId) {
        protector.requireReady();
        var user=users.findByIdForUpdate(userId).orElseThrow(()->new SocialAuthException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","로그인이 필요합니다."));
        if(user.getRole()!=MemberRole.STUDENT) throw new SocialAuthException(HttpStatus.FORBIDDEN,"FORBIDDEN","유학생 본인의 탈퇴만 신청할 수 있습니다.");
        if(user.isWithdrawn()) throw new WithdrawnAccountException();
        var connections=accounts.findAllByUserAccountIdOrderById(userId);
        if(connections.isEmpty()) throw new SocialAuthException(HttpStatus.CONFLICT,"SOCIAL_REAUTH_REQUIRED","소셜 계정으로 다시 로그인해주세요.");
        connections.forEach(unlink::requireReady);
        Instant now=Instant.now(); user.requestWithdrawal(now); refreshTokens.deleteAllByUserAccountId(userId);
        return new Result(now,now.plus(Duration.ofDays(30)));
    }
    public record Result(Instant requestedAt,Instant recoveryDeadline) { }
}
