package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDate;

@Service 
@Slf4j
public class SmsService {
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.sms.africas-talking.api-key:}")    private String atApiKey;
    @Value("${app.sms.africas-talking.username:sandbox}") private String atUsername;
    @Value("${app.sms.africas-talking.sender-id:LoanSaaS}") private String atSenderId;
    @Value("${app.sms.twilio.account-sid:}")         private String twilioAccountSid;
    @Value("${app.sms.twilio.auth-token:}")          private String twilioAuthToken;
    @Value("${app.sms.twilio.from-number:}")         private String twilioFromNumber;
    @Value("${app.sms.enabled:false}")               private boolean smsEnabled;

    @Async 
    public void sendLoanApproved(Loan loan) {
        String phone = phone(loan); 
        if (phone == null) return;
        send(phone, String.format("Congratulations! Loan %s for %s %s approved. Funds disbursed shortly. -%s",
            loan.getReferenceNumber(), loan.getCurrency(), fmt(loan.getAmount()), orgName(loan)));
    }

    @Async 
    public void sendLoanRejected(Loan loan) {
        String phone = phone(loan); 
        if (phone == null) return;
        send(phone, String.format("Your application for loan %s was not approved at this time. Contact us for details. -%s",
            loan.getReferenceNumber(), orgName(loan)));
    }

    @Async 
    public void sendLoanDisbursed(Loan loan, String method) {
        String phone = phone(loan); 
        if (phone == null) return;
        send(phone, String.format("Funds sent! %s %s for loan %s has been disbursed via %s. -%s",
            loan.getCurrency(), fmt(loan.getDisbursedAmount()), loan.getReferenceNumber(), method, orgName(loan)));
    }

    @Async 
    public void sendPaymentDue(Loan loan, double amount, String dueDate) {
        String phone = phone(loan); 
        if (phone == null) return;
        send(phone, String.format("REMINDER: Payment of %s %s due %s for loan %s. Pay now to avoid penalties. -%s",
            loan.getCurrency(), fmt(amount), dueDate, loan.getReferenceNumber(), orgName(loan)));
    }

    @Async 
    public void sendPaymentConfirmed(Loan loan, double amount) {
        String phone = phone(loan); 
        if (phone == null) return;
        send(phone, String.format("Payment of %s %s received for %s. Balance: %s %s. -%s",
            loan.getCurrency(), fmt(amount), loan.getReferenceNumber(),
            loan.getCurrency(), fmt(loan.getOutstandingBalance()), orgName(loan)));
    }

    @Async 
    public void sendLoanOverdue(Loan loan, int days) {
        String phone = phone(loan); 
        if (phone == null) return;
        send(phone, String.format("URGENT: Loan %s is %d day(s) overdue. Outstanding: %s %s. Contact us immediately. -%s",
            loan.getReferenceNumber(), days, loan.getCurrency(), fmt(loan.getOutstandingBalance()), orgName(loan)));
    }

    @Async 
    public void sendCustom(String phone, String message) { 
        send(phone, message); 
    }

    private void send(String to, String msg) {
        String normalized = normalizePhone(to);
        if (normalized == null) { log.warn("Skipping SMS — invalid phone number: {}", to); return; }
        if (!smsEnabled) { 
            log.info("[SMS SIMULATION] {} -> {}", normalized, msg); 
            return; 
        }
        if (trySendAT(normalized, msg)) return;
        if (trySendTwilio(normalized, msg)) return;
        log.warn("All SMS providers failed for {}", normalized);
    }

    /**
     * Twilio (and most SMS gateways) require strict E.164: a leading '+', country
     * code, then digits only — no spaces, dashes, or parentheses. Phone numbers
     * entered by borrowers/staff often look like "+250 788 000 000" or "0788000000",
     * either of which Twilio silently rejects. Defaults local-format numbers
     * (starting with 0) to Rwanda's country code — adjust if you serve other countries.
     */
    private String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String digits = raw.replaceAll("[^+0-9]", "");
        if (digits.isEmpty()) return null;
        if (digits.startsWith("+")) return digits;
        if (digits.startsWith("250")) return "+" + digits;
        if (digits.startsWith("0")) return "+250" + digits.substring(1);
        return "+250" + digits;
    }

    private boolean trySendAT(String to, String msg) {
        if (atApiKey == null || atApiKey.isBlank()) return false;
        try {
            HttpHeaders h = new HttpHeaders();
            h.set("apiKey", atApiKey); 
            h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            String body = "username=" + enc(atUsername) + "&to=" + enc(to) + "&message=" + enc(msg) + "&from=" + enc(atSenderId);
            restTemplate.exchange("https://api.africastalking.com/version1/messaging", HttpMethod.POST, new HttpEntity<>(body, h), String.class);
            log.debug("SMS sent via Africa's Talking to {}", to); 
            return true;
        } catch (Exception e) {
            log.warn("AT SMS failed: {}", e.getMessage()); 
            return false;
        }
    }

    private boolean trySendTwilio(String to, String msg) {
        if (twilioAccountSid == null || twilioAccountSid.isBlank()) return false;
        try {
            String url = "https://api.twilio.com/2010-04-01/Accounts/" + twilioAccountSid + "/Messages.json";
            HttpHeaders h = new HttpHeaders();
            h.setBasicAuth(twilioAccountSid, twilioAuthToken); 
            h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            String body = "To=" + enc(to) + "&From=" + enc(twilioFromNumber) + "&Body=" + enc(msg);
            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
            log.info("SMS sent via Twilio successfully to recipient target: {}", to); 
            return true;
        } catch (Exception e) {
            log.warn("Twilio SMS failed: {}", e.getMessage()); 
            return false;
        }
    }

    private String enc(String s) {
        try { 
            return java.net.URLEncoder.encode(s, "UTF-8"); 
        } catch (Exception e) { 
            return s; 
        }
    }
    
    private String fmt(Double v) { 
        return v == null ? "0" : String.format("%,.2f", v); 
    }
    
    private String phone(Loan l) {
        return (l.getBorrower() != null && l.getBorrower().getPhone() != null && !l.getBorrower().getPhone().isBlank()) 
            ? l.getBorrower().getPhone() : null;
    }
    
    private String orgName(Loan l) { 
        return l.getOrganization() != null ? l.getOrganization().getName() : "LoanSaaS"; 
    }
}