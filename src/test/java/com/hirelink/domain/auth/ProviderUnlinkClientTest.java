package com.hirelink.domain.auth;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import com.hirelink.domain.auth.entity.*;
import com.hirelink.domain.auth.service.*;
import com.hirelink.global.exception.SocialAuthException;
import static org.assertj.core.api.Assertions.*;

class ProviderUnlinkClientTest {
    private final ProviderTokenProtector protector=new ProviderTokenProtector(true,"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

    @Test void encryptionIsRandomAuthenticatedAndBoundToProviderIdentity() {
        String encrypted=protector.encrypt("refresh-secret","GOOGLE:123");
        assertThat(protector.decrypt(encrypted,"GOOGLE:123")).isEqualTo("refresh-secret");
        assertThat(protector.encrypt("refresh-secret","GOOGLE:123")).isNotEqualTo(encrypted);
        assertThatThrownBy(()->protector.decrypt(encrypted,"GOOGLE:456")).isInstanceOf(SocialAuthException.class);
        assertThatThrownBy(()->new ProviderTokenProtector(false,"").requireReady()).isInstanceOf(SocialAuthException.class);
        assertThatThrownBy(()->new ProviderTokenProtector(true,"bad-key").requireReady()).isInstanceOf(SocialAuthException.class);
    }

    @Test void sendsCorrectProviderCredentialsAndOnlyAcceptsConfirmedOrAlreadyRevokedResults() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var sent=new AtomicReference<String>(); var authorization=new AtomicReference<String>();
        int[] status={200}; String[] body={"{\"id\":123}"};
        server.createContext("/",exchange->{
            sent.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes=body[0].getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(status[0],bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            var client=new ProviderUnlinkClient(protector,"test-admin",base+"/revoke",base+"/v1/user/unlink");
            var google=new AuthAccount(null,AuthProvider.GOOGLE,"123",null);
            assertThatThrownBy(()->client.requireReady(google)).isInstanceOf(SocialAuthException.class).extracting("code").isEqualTo("SOCIAL_REAUTH_REQUIRED");
            google.saveRevocationToken(protector.encrypt("refresh secret","GOOGLE:123"));
            client.unlink(google); assertThat(sent.get()).isEqualTo("token=refresh+secret"); assertThat(authorization.get()).isNull();
            status[0]=400; body[0]="{\"error\":\"invalid_token\"}"; client.unlink(google);
            body[0]="{\"error\":\"invalid_client\"}";
            assertThatThrownBy(()->client.unlink(google)).isInstanceOf(SocialAuthException.class).hasMessage("소셜 연결 해제를 완료하지 못했습니다.");
            var kakao=new AuthAccount(null,AuthProvider.KAKAO,"123",null);
            status[0]=200; body[0]="{\"id\":123}"; client.unlink(kakao);
            assertThat(sent.get()).contains("target_id_type=user_id","target_id=123"); assertThat(authorization.get()).isEqualTo("KakaoAK test-admin");
            body[0]="{\"id\":456}"; assertThatThrownBy(()->client.unlink(kakao)).isInstanceOf(SocialAuthException.class);
            status[0]=400; body[0]="{\"code\":-101}"; client.unlink(kakao);
            status[0]=401; body[0]="{\"code\":-401}"; assertThatThrownBy(()->client.unlink(kakao)).isInstanceOf(SocialAuthException.class);
            status[0]=503; body[0]="sensitive upstream content";
            assertThatThrownBy(()->client.unlink(kakao)).isInstanceOf(SocialAuthException.class).hasMessage("소셜 연결 해제를 완료하지 못했습니다.");
        } finally {server.stop(0);}
    }
}
