package com.menujuhari.identity.application;

public interface OtpMailer {

	void sendOtp(String to, String code);
}
