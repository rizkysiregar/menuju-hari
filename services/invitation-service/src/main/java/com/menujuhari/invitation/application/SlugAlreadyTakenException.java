package com.menujuhari.invitation.application;

public class SlugAlreadyTakenException extends RuntimeException {

	public SlugAlreadyTakenException() {
		super("Slug sudah dipakai undangan lain");
	}
}
