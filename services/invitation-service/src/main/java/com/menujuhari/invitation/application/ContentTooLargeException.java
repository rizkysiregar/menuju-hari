package com.menujuhari.invitation.application;

public class ContentTooLargeException extends RuntimeException {

	public ContentTooLargeException() {
		super("Konten terlalu besar");
	}
}
