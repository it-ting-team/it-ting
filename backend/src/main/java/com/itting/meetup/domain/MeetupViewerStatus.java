package com.itting.meetup.domain;

public enum MeetupViewerStatus {
	HOST, NONE, PENDING, REJECTED, CONFIRMED, CANCELED, KICKED, MEETUP_CANCELED;

	public static MeetupViewerStatus fromApplication(MeetupApplicationStatus status, boolean deadlinePassed) {
		if (status == null || status == MeetupApplicationStatus.WITHDRAWN) {
			return NONE;
		}
		if (status == MeetupApplicationStatus.PENDING && deadlinePassed) {
			return REJECTED;
		}
		return valueOf(status.name());
	}
}
