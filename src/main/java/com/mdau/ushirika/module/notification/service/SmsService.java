package com.mdau.ushirika.module.notification.service;

/**
 * Pluggable SMS abstraction. Implemented by AfricasTalkingService.
 * In dev, falls back to logging when credentials are not configured.
 */
public interface SmsService {

    /**
     * Send a plain-text SMS to a single phone number.
     * Phone must be in E.164 format: +254XXXXXXXXX
     * Fire-and-forget — runs async, does not throw.
     */
    void send(String phone, String recipientName, String message);

    /**
     * Send an OTP code via SMS.
     */
    default void sendOtp(String phone, String name, String otp) {
        send(phone, name,
                "Your Ushirika Welfare Organization verification code is: " + otp +
                ". It expires in 15 minutes. Do not share it.");
    }

    /**
     * Notify a member that their membership was approved.
     */
    default void sendMembershipApproved(String phone, String name, String memberId) {
        send(phone, name,
                "Congratulations " + name + "! Your Ushirika Welfare Organization membership has been approved. " +
                "Member ID: " + memberId + ". Log in to your member portal to get started.");
    }
}
