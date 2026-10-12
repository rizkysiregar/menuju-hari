package com.menujuhari.identity.application;

public class RateLimitExceededException extends RuntimeException {

	public RateLimitExceededException() {
		super("Rate limit exceeded");
	}
}
