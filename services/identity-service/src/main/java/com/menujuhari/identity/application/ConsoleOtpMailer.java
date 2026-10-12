package com.menujuhari.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * HANYA untuk pengembangan lokal tanpa kredensial Gmail:
 * kode OTP dicetak ke log alih-alih dikirim via SMTP.
 * Aktif dengan app.mail.console-fallback=true (JANGAN pernah di produksi).
 */
@Component
@ConditionalOnProperty(name = "app.mail.console-fallback", havingValue = "true")
public class ConsoleOtpMailer implements OtpMailer {

	private static final Logger log = LoggerFactory.getLogger(ConsoleOtpMailer.class);

	@Override
	@Async("mailExecutor")
	public void sendOtp(String to, String code) {
		log.warn("[DEV ONLY] OTP untuk {} adalah: {}", to, code);
	}
}
