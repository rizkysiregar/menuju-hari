package com.menujuhari.invitation.application;

public class InvitationNotFoundException extends RuntimeException {

	public InvitationNotFoundException() {
		super("Invitation tidak ditemukan");
	}
}
