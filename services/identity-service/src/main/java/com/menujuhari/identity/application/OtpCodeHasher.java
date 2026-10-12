package com.menujuhari.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Hash kode OTP: SHA-256(salt hex || kode).
 * OTP hanya hidup 5 menit & satu kali pakai, jadi SHA-256 + salt per-challenge
 * sudah memadai (PBKDF2/argon2 tidak diperlukan untuk rahasia pendek umur pendek).
 */
public final class OtpCodeHasher {

	private static final SecureRandom RANDOM = new SecureRandom();

	private OtpCodeHasher() {
	}

	public static String newSalt() {
		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	public static String hash(String salt, String code) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest((salt + code).getBytes(StandardCharsets.US_ASCII));
			return HexFormat.of().formatHex(hashed);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 tidak tersedia", e);
		}
	}

	public static boolean matches(String code, String salt, String expectedHash) {
		byte[] actual = hash(salt, code).getBytes(StandardCharsets.US_ASCII);
		byte[] expected = expectedHash.getBytes(StandardCharsets.US_ASCII);
		return MessageDigest.isEqual(actual, expected);
	}
}
