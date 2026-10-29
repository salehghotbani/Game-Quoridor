package com.quoridor.mobile;

/** Bounded idle/failure backoff; a changed room always restores prompt polling. */
final class PollCadence {
    private long revision = -1;
    private int seconds = 1;

    void received(long nextRevision) {
        seconds = nextRevision == revision ? Math.min(6, seconds + 1) : 1;
        revision = nextRevision;
    }

    void failed() {
        seconds = Math.min(30, Math.max(2, seconds * 2));
    }

    int seconds() {
        return seconds;
    }
}
