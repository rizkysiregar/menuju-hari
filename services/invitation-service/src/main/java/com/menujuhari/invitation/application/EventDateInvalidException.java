package com.menujuhari.invitation.application;

public class EventDateInvalidException extends RuntimeException {

	public EventDateInvalidException() {
		super("Tanggal acara tidak valid");
	}
}
