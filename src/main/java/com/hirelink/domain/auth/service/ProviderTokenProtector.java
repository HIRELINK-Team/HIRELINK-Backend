package com.hirelink.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.hirelink.global.exception.SocialAuthException;

@Component
public class ProviderTokenProtector {
    private final boolean enabled;
    private final String key;
    public ProviderTokenProtector(@Value("${auth.withdrawal.enabled:false}") boolean enabled,@Value("${auth.withdrawal.encryption-key:}") String key) { this.enabled=enabled; this.key=key; }
    public boolean enabled() { return enabled; }
    public void requireReady() {
        try { if(!enabled || Base64.getDecoder().decode(key).length!=32) throw new IllegalArgumentException(); }
        catch(IllegalArgumentException ex) { throw new SocialAuthException(HttpStatus.SERVICE_UNAVAILABLE,"WITHDRAWAL_NOT_CONFIGURED","탈퇴 서비스 설정을 확인해주세요."); }
    }
    public String encrypt(String token,String identity) {
        requireReady(); if(token==null || token.isBlank()) return null;
        try {
            byte[] nonce=new byte[12]; new SecureRandom().nextBytes(nonce);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(Base64.getDecoder().decode(key),"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(identity.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted=cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)); byte[] output=Arrays.copyOf(nonce,nonce.length+encrypted.length);
            System.arraycopy(encrypted,0,output,nonce.length,encrypted.length); return Base64.getEncoder().encodeToString(output);
        } catch(java.security.GeneralSecurityException ex) { throw new IllegalStateException("소셜 연결 정보 암호화 실패"); }
    }
    public String decrypt(String token,String identity) {
        requireReady();
        try {
            byte[] data=Base64.getDecoder().decode(token);
            if(data.length<29) throw new IllegalArgumentException();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(Base64.getDecoder().decode(key),"AES"),new GCMParameterSpec(128,Arrays.copyOf(data,12)));
            cipher.updateAAD(identity.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(data,12,data.length-12),StandardCharsets.UTF_8);
        } catch(java.security.GeneralSecurityException | IllegalArgumentException ex) {
            throw new SocialAuthException(HttpStatus.BAD_GATEWAY,"SOCIAL_UNLINK_FAILED","소셜 연결 해제를 완료하지 못했습니다.");
        }
    }
}
