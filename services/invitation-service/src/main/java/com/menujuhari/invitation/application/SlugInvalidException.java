package com.menujuhari.invitation.application;

public class SlugInvalidException extends RuntimeException {

	public SlugInvalidException() {
		super("Format slug tidak valid");
	}
}
