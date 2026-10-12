package com.menujuhari.identity.application;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.menujuhari.identity.domain.otp.OtpChallenge;
import com.menujuhari.identity.domain.otp.OtpChallengeRepository;
import com.menujuhari.identity.domain.otp.OtpStatus;
import com.menujuhari.identity.domain.user.User;
import com.menujuhari.identity.domain.user.UserRepository;
import com.menujuhari.identity.infra.config.IdentityProperties;
import com.menujuhari.identity.infra.ratelimit.RedisRateLimiter;
import com.menujuhari.shared.security.SessionService;

/**
 * Logika OTP sesuai AUTH-001..005 di PROJECT_PLAN.md:
 * - kode 6 digit, TTL 5 menit, satu kali pakai
 * - cooldown 60 detik, rate limit per email (jam/hari) dan per IP (jam)
 * - maksimal 5 percobaan verifikasi per challenge
 * - respons selalu generik (anti email enumeration)
 */
@Service
public class OtpService {

	private static final Logger log = LoggerFactory.getLogger(OtpService.class);
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final DateTimeFormatter HOUR_BUCKET = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC);
	private static final DateTimeFormatter DAY_BUCKET = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

	private final OtpChallengeRepository otpRepository;
	private final UserRepository userRepository;
	private final SessionService sessionService;
	private final RedisRateLimiter rateLimiter;
	private final OtpMailer mailer;
	private final IdentityProperties.Otp config;

	public OtpService(OtpChallengeRepository otpRepository, UserRepository userRepository,
			SessionService sessionService, RedisRateLimiter rateLimiter, OtpMailer mailer,
			IdentityProperties props) {
		this.otpRepository = otpRepository;
		this.userRepository = userRepository;
		this.sessionService = sessionService;
		this.rateLimiter = rateLimiter;
		this.mailer = mailer;
		this.config = props.otp();
	}

	public void requestOtp(String rawEmail, String clientIp) {
		String email = normalizeEmail(rawEmail);
		String emailKey = sha256Hex(email);

		rateLimiter.requirePermitOnce("rl:otp:cd:" + emailKey, config.cooldown());
		rateLimiter.requirePermit("rl:otp:eh:" + emailKey + ":" + hourBucket(), config.maxPerEmailPerHour(),
				Duration.ofHours(1));
		rateLimiter.requirePermit("rl:otp:ed:" + emailKey + ":" + dayBucket(), config.maxPerEmailPerDay(),
				Duration.ofDays(1));
		rateLimiter.requirePermit("rl:otp:ih:" + sha256Hex(clientIp) + ":" + hourBucket(), config.maxPerIpPerHour(),
				Duration.ofHours(1));

		String code = generateCode();
		String salt = OtpCodeHasher.newSalt();
		Instant expiresAt = Instant.now().plus(config.ttl());
		otpRepository.save(new OtpChallenge(email, OtpCodeHasher.hash(salt, code), salt, expiresAt));

		mailer.sendOtp(email, code);
		log.info("OTP diminta untuk {}", maskEmail(email));
	}

	@Transactional
	public VerifyResult verify(String rawEmail, String rawCode) {
		String email = normalizeEmail(rawEmail);
		String code = rawCode == null ? "" : rawCode.trim();

		OtpChallenge challenge = otpRepository
				.findFirstByEmailAndStatusOrderByCreatedAtDesc(email, OtpStatus.ACTIVE)
				.orElseThrow(OtpVerificationException::new);

		if (challenge.getExpiresAt().isBefore(Instant.now())) {
			otpRepository.updateStatus(challenge.getId(), OtpStatus.EXPIRED);
			throw new OtpVerificationException();
		}

		if (!OtpCodeHasher.matches(code, challenge.getSalt(), challenge.getCodeHash())) {
			int attempts = otpRepository.incrementAttempts(challenge.getId());
			if (attempts >= config.maxAttempts()) {
				otpRepository.updateStatus(challenge.getId(), OtpStatus.EXHAUSTED);
			}
			throw new OtpVerificationException();
		}

		otpRepository.updateStatus(challenge.getId(), OtpStatus.VERIFIED);
		User user = upsertUser(email);
		String sessionToken = sessionService.create(user.getId(), user.getEmail(), user.getRole().name());
		log.info("Verifikasi OTP sukses untuk {}", maskEmail(email));
		return new VerifyResult(sessionToken, user);
	}

	private User upsertUser(String email) {
		return userRepository.findByEmailAndDeletedAtIsNull(email)
				.orElseGet(() -> userRepository.save(User.newCustomer(email)));
	}

	private String generateCode() {
		return String.format("%06d", RANDOM.nextInt(1_000_000));
	}

	private static String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase();
	}

	private static String sha256Hex(String input) {
		return OtpCodeHasher.hash("", input).substring(0, 32);
	}

	private static String hourBucket() {
		return HOUR_BUCKET.format(Instant.now());
	}

	private static String dayBucket() {
		return DAY_BUCKET.format(Instant.now());
	}

	private static String maskEmail(String email) {
		int at = email.indexOf('@');
		if (at <= 2) {
			return "***" + email.substring(Math.max(0, at));
		}
		return email.substring(0, 2) + "***" + email.substring(at);
	}

	public record VerifyResult(String sessionToken, User user) {
	}
}
