package com.quoridor.mobile;

import static org.junit.Assert.*;

import org.junit.Test;

public final class PollCadenceTest {
    @Test
    public void idleAndFailuresReduceRequestsButNewMovesRestoreResponsiveness() {
        PollCadence cadence = new PollCadence();
        cadence.received(4);
        assertEquals(1, cadence.seconds());
        for (int i = 0; i < 100; i++) cadence.received(4);
        assertEquals(6, cadence.seconds());
        for (int i = 0; i < 100; i++) cadence.failed();
        assertEquals(30, cadence.seconds());
        cadence.received(5);
        assertEquals(1, cadence.seconds());
    }
}
