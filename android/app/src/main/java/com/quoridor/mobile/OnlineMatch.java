package com.quoridor.mobile;

import android.os.Handler;
import android.os.Looper;

import Board.GameState;

import Online.OnlineClient;
import Online.OnlineException;
import Online.OnlineState;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Single-worker room lifecycle. UI callbacks are fenced when this owner is closed. */
final class OnlineMatch implements AutoCloseable {
    interface Listener {
        void onState(OnlineState state);

        void onFailure(boolean entering, boolean terminal);
    }

    enum Entry {
        CREATE,
        JOIN,
        RANDOM
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final OnlineClient client;
    // Listener and replay fields belong to the main thread; worker results never capture an
    // Activity.
    private Listener listener;
    private OnlineState latestState;
    private boolean hasFailure;
    private boolean failureEntering;
    private boolean failureTerminal;
    private volatile boolean closed;
    private volatile boolean foreground = true;
    private volatile boolean requestPending;
    private boolean entered;
    private boolean terminal;
    private Future<?> poll;
    private final PollCadence cadence = new PollCadence();
    private Future<?> entry;

    OnlineMatch(String endpoint, Listener listener) {
        client = new OnlineClient(endpoint);
        this.listener = listener;
    }

    void setListener(Listener replacement) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("Online listener changes require the main thread");
        }
        listener = replacement;
        if (replacement == null || closed) {
            return;
        }
        if (latestState != null) {
            replacement.onState(latestState);
        }
        // A failure after the last snapshot must be replayed as well, especially an expired room.
        if (listener == replacement && !closed && hasFailure) {
            replacement.onFailure(failureEntering, failureTerminal);
        }
    }

    boolean busy() {
        return requestPending;
    }

    void enter(Entry mode, int count, String name, String code) {
        requestPending = true;
        entry =
                worker.submit(
                        () -> {
                            try {
                                OnlineState state =
                                        switch (mode) {
                                            case CREATE -> client.createPrivate(count, name);
                                            case JOIN -> client.joinPrivate(code, name);
                                            case RANDOM -> client.findMatch(count, name);
                                        };
                                entered = true;
                                received(state);
                            } catch (IOException failure) {
                                failed(true, true);
                            }
                        });
    }

    void move(int row, int col, long revision) {
        action(() -> client.move(row, col, revision));
    }

    void wall(GameState.Wall wall, long revision) {
        action(() -> client.wall(wall.row(), wall.col(), wall.orientation(), revision));
    }

    private interface Request {
        OnlineState run() throws IOException;
    }

    private void action(Request request) {
        if (closed) {
            return;
        }
        requestPending = true;
        worker.execute(
                () -> {
                    cancelPoll();
                    if (closed || terminal) {
                        return;
                    }
                    try {
                        received(request.run());
                    } catch (IOException failure) {
                        // Refresh after a rejected/stale move; do not replay a potentially accepted
                        // action.
                        try {
                            received(client.state());
                        } catch (IOException unavailable) {
                            handlePollFailure(unavailable);
                        }
                    }
                });
    }

    private void received(OnlineState state) {
        cadence.received(state.revision());
        terminal = state.status() == OnlineState.Status.CLOSED;
        requestPending = false;
        ui.post(
                () -> {
                    if (closed) {
                        return;
                    }
                    latestState = state;
                    hasFailure = false;
                    if (listener != null) {
                        listener.onState(state);
                    }
                });
        schedulePoll();
    }

    private void schedulePoll() {
        cancelPoll();
        if (!closed && entered && !terminal && foreground) {
            poll = worker.schedule(this::refresh, cadence.seconds(), TimeUnit.SECONDS);
        }
    }

    private void refresh() {
        if (closed || terminal || !foreground) {
            return;
        }
        try {
            received(client.state());
        } catch (IOException failure) {
            handlePollFailure(failure);
        }
    }

    private void handlePollFailure(IOException failure) {
        cadence.failed();
        boolean expired =
                failure instanceof OnlineException online
                        && (online.httpStatus() == 401
                                || online.httpStatus() == 404
                                || online.httpStatus() == 410);
        if (expired) {
            terminal = true;
        }
        failed(false, expired);
        schedulePoll();
    }

    private void failed(boolean entering, boolean ended) {
        requestPending = false;
        ui.post(
                () -> {
                    if (closed) {
                        return;
                    }
                    hasFailure = true;
                    failureEntering = entering;
                    failureTerminal = ended;
                    if (listener != null) {
                        listener.onFailure(entering, ended);
                    }
                });
    }

    void setForeground(boolean active) {
        foreground = active;
        if (!closed) {
            worker.execute(
                    () -> {
                        if (active) {
                            schedulePoll();
                        } else {
                            cancelPoll();
                        }
                    });
        }
    }

    private void cancelPoll() {
        if (poll != null) {
            poll.cancel(false);
            poll = null;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        listener = null;
        // Let a reserved-seat response finish so close() can release its captured credential.
        if (entry != null) {
            entry.cancel(false);
        }
        worker.execute(
                () -> {
                    cancelPoll();
                    try {
                        client.close();
                    } catch (IOException ignored) {
                        /* Server expiry remains authoritative. */
                    }
                });
        worker.shutdown();
    }
}
