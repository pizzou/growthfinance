package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.*;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Base64;

@Service @RequiredArgsConstructor @Slf4j
public class MfaService {
    private final UserRepository userRepository;
    private final DefaultSecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(new DefaultCodeGenerator(),new SystemTimeProvider());

    @Transactional
    public MfaSetupResponse beginSetup(Long userId,String issuer) {
        User user=userRepository.findById(userId).orElseThrow(()->new RuntimeException("User not found"));
        String secret=secretGenerator.generate();
        user.setTwoFactorSecret(secret); user.setTwoFactorEnabled(false);
        userRepository.save(user);
        return new MfaSetupResponse(secret,generateQr(user.getEmail(),secret,issuer));
    }

    @Transactional
    public boolean confirmSetup(Long userId,String code) {
        User user=userRepository.findById(userId).orElseThrow(()->new RuntimeException("User not found"));
        if(user.getTwoFactorSecret()==null) throw new RuntimeException("MFA setup not started");
        boolean valid=codeVerifier.isValidCode(user.getTwoFactorSecret(),code);
        if(valid){user.setTwoFactorEnabled(true);userRepository.save(user);}
        return valid;
    }

    public boolean verifyCode(User user,String code) {
        if(!user.isTwoFactorEnabled()||user.getTwoFactorSecret()==null) return true;
        return codeVerifier.isValidCode(user.getTwoFactorSecret(),code);
    }

    @Transactional
    public void disable(Long userId) {
        User user=userRepository.findById(userId).orElseThrow(()->new RuntimeException("User not found"));
        user.setTwoFactorEnabled(false); user.setTwoFactorSecret(null);
        userRepository.save(user);
    }

    private String generateQr(String email,String secret,String issuer) {
        try {
            QrData data=new QrData.Builder().label(email).secret(secret)
                .issuer(issuer!=null?issuer:"LoanSaaS Pro")
                .algorithm(HashingAlgorithm.SHA1).digits(6).period(30).build();
            return "data:"+qrGenerator.getImageMimeType()+";base64,"+
                Base64.getEncoder().encodeToString(qrGenerator.generate(data));
        } catch(QrGenerationException e){throw new RuntimeException("QR generation failed",e);}
    }

    public record MfaSetupResponse(String secret,String qrCodeDataUri){}
}
