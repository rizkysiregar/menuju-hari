package com.menujuhari.invitation.application;

public class ContentInvalidException extends RuntimeException {

	public ContentInvalidException() {
		super("Konten tidak valid");
	}
}
