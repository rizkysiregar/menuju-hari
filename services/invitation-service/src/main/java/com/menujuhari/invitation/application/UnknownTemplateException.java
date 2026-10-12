package com.menujuhari.invitation.application;

public class UnknownTemplateException extends RuntimeException {

	public UnknownTemplateException() {
		super("Template tidak dikenal");
	}
}
