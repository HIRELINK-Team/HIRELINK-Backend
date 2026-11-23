package com.hirelink.domain.auth.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.hirelink.domain.auth.entity.AuthAccount;
import com.hirelink.domain.auth.entity.AuthProvider;
import com.hirelink.global.exception.SocialAuthException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ProviderUnlinkClient {
    private final ProviderTokenProtector protector;
    private final String kakaoAdminKey;
    private final URI google;
    private final URI kakao;
    private final RestClient client;
    public ProviderUnlinkClient(ProviderTokenProtector protector,@Value("${auth.withdrawal.kakao-admin-key:}") String kakaoAdminKey,
            @Value("${auth.withdrawal.google-revoke-url:https://oauth2.googleapis.com/revoke}") String google,
            @Value("${auth.withdrawal.kakao-unlink-url:https://kapi.kakao.com/v1/user/unlink}") String kakao) {
        this.protector=protector; this.kakaoAdminKey=kakaoAdminKey;
        this.google=endpoint(google,"oauth2.googleapis.com","/revoke"); this.kakao=endpoint(kakao,"kapi.kakao.com","/v1/user/unlink");
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(10)); client=RestClient.builder().requestFactory(factory).build();
    }
    public void requireReady(AuthAccount account) {
        if(account.getProvider()==AuthProvider.GOOGLE && account.getRevocationToken()==null)
            throw new SocialAuthException(HttpStatus.CONFLICT,"SOCIAL_REAUTH_REQUIRED","소셜 계정으로 다시 로그인한 뒤 탈퇴를 신청해주세요.");
        if(account.getProvider()==AuthProvider.KAKAO && kakaoAdminKey.isBlank())
            throw new SocialAuthException(HttpStatus.SERVICE_UNAVAILABLE,"WITHDRAWAL_NOT_CONFIGURED","탈퇴 서비스 설정을 확인해주세요.");
    }
    public void unlink(AuthAccount account) {
        if(account.getProvider()==AuthProvider.LOCAL) return;
        requireReady(account);
        var form=new LinkedMultiValueMap<String,String>();
        boolean isGoogle=account.getProvider()==AuthProvider.GOOGLE;
        if(isGoogle) form.add("token",protector.decrypt(account.getRevocationToken(),"GOOGLE:"+account.getProviderUserId()));
        else { form.add("target_id_type","user_id"); form.add("target_id",account.getProviderUserId()); }
        try {
            var request=client.post().uri(isGoogle?google:kakao).contentType(MediaType.APPLICATION_FORM_URLENCODED);
            if(!isGoogle) request.header("Authorization","KakaoAK "+kakaoAdminKey);
            boolean done=request.body(form).exchange((req,res)->{
                int status=res.getStatusCode().value();
                if(isGoogle && status==200) return true;
                byte[] body=res.getBody().readNBytes(8192);
                try {
                    var json=JsonMapper.builder().build().readTree(body);
                    if(isGoogle) return status==400 && "invalid_token".equals(json.path("error").asString());
                    return (status==200 && account.getProviderUserId().equals(json.path("id").asString()))
                        || (status==400 && json.path("code").asInt()==-101);
                } catch(RuntimeException ex) { return false; }
            });
            if(!done) throw failed();
        } catch(RestClientException exception) { throw failed(); }
    }
    private URI endpoint(String value,String host,String path) {
        URI uri=URI.create(value);
        boolean local="http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost());
        if((!local && !("https".equals(uri.getScheme()) && host.equals(uri.getHost()))) || !path.equals(uri.getPath())
                || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null) throw new IllegalArgumentException("잘못된 소셜 연결 해제 URL");
        return uri;
    }
    private SocialAuthException failed() { return new SocialAuthException(HttpStatus.BAD_GATEWAY,"SOCIAL_UNLINK_FAILED","소셜 연결 해제를 완료하지 못했습니다."); }
}
