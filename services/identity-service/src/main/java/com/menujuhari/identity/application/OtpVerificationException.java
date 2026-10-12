package com.menujuhari.identity.application;

public class OtpVerificationException extends RuntimeException {

	public OtpVerificationException() {
		super("OTP verification failed");
	}
}
