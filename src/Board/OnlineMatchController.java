package Board;

import Online.OnlineClient;
import Online.OnlineException;
import Online.OnlineState;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/** Owns one online membership; all public mutations and listener calls run on the event thread. */
final class OnlineMatchController implements AutoCloseable {
    enum Action {
        CREATE,
        JOIN,
        MATCH
    }

    interface Listener {
        void onState(OnlineState state);

        void onConnectionProblem(boolean reconnecting);

        void onFailure(String code);

        void onBusy(boolean busy);
    }

    private static final int POLL_INTERVAL_MILLIS = 1000;
    private final Listener listener;
    private final Timer pollTimer;
    private OnlineClient client;
    private Request worker;
    private volatile long generation;
    private volatile boolean active;
    private volatile boolean connected;
    private volatile boolean busy;
    private boolean reconnecting;
    private boolean closed;

    OnlineMatchController(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "Online listener is required");
        pollTimer = new Timer(POLL_INTERVAL_MILLIS, event -> poll());
        pollTimer.setCoalesce(true);
    }

    void start(URI server, Action action, int count, String name, String code) {
        requireEventThread();
        if (closed) {
            throw new IllegalStateException("The online controller is closed");
        }
        Objects.requireNonNull(server, "Server address is required");
        Objects.requireNonNull(action, "Online action is required");
        Objects.requireNonNull(name, "Player name is required");
        Objects.requireNonNull(code, "Match code is required");
        leave();
        active = true;
        long requestGeneration = generation;
        launch(
                new Request(requestGeneration, null, true, false) {
                    @Override
                    OnlineState request(OnlineClient connection) throws IOException {
                        return switch (action) {
                            case CREATE -> connection.createPrivate(count, name);
                            case JOIN -> connection.joinPrivate(code, name);
                            case MATCH -> connection.findMatch(count, name);
                        };
                    }

                    @Override
                    OnlineClient initialClient() {
                        return new OnlineClient(server);
                    }
                });
    }

    void move(int row, int col, long expectedRevision) {
        requireEventThread();
        if (!canSubmitMove()) {
            return;
        }
        launch(
                new Request(generation, client, false, false) {
                    @Override
                    OnlineState request(OnlineClient connection) throws IOException {
                        return connection.move(row, col, expectedRevision);
                    }
                });
    }

    void wall(int row, int col, GameState.Orientation orientation, long expectedRevision) {
        requireEventThread();
        Objects.requireNonNull(orientation, "Wall orientation is required");
        if (!canSubmitMove()) {
            return;
        }
        launch(
                new Request(generation, client, false, false) {
                    @Override
                    OnlineState request(OnlineClient connection) throws IOException {
                        return connection.wall(row, col, orientation, expectedRevision);
                    }
                });
    }

    boolean active() {
        return active;
    }

    boolean connected() {
        return connected;
    }

    boolean busy() {
        return busy;
    }

    /** Invalidates pending callbacks immediately and leaves only this controller's membership. */
    void leave() {
        requireEventThread();
        generation++;
        pollTimer.stop();
        Request previous = worker;
        worker = null;
        if (previous != null) {
            // Entry must receive its credential before cleanup can release a reserved seat.
            previous.cancel(!previous.initial);
        }
        OnlineClient previousClient = client;
        client = null;
        active = false;
        connected = false;
        reconnecting = false;
        setBusy(false);
        closeLater(previousClient);
    }

    @Override
    public void close() {
        requireEventThread();
        if (!closed) {
            closed = true;
            leave();
        }
    }

    private boolean canSubmitMove() {
        return !closed && active && connected && client != null && worker == null;
    }

    private void poll() {
        if (!closed && active && client != null && worker == null) {
            launch(
                    new Request(generation, client, false, true) {
                        @Override
                        OnlineState request(OnlineClient connection) throws IOException {
                            return connection.state();
                        }
                    });
        }
    }

    private void launch(Request request) {
        worker = request;
        setBusy(true);
        request.execute();
    }

    private void setBusy(boolean next) {
        if (busy != next) {
            busy = next;
            listener.onBusy(next);
        }
    }

    private void setConnectionProblem(boolean next) {
        if (reconnecting != next) {
            reconnecting = next;
            listener.onConnectionProblem(next);
        }
    }

    private static void requireEventThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Online controls must run on the event thread");
        }
    }

    private static void closeLater(OnlineClient connection) {
        if (connection == null) {
            return;
        }
        Thread cleanup =
                new Thread(
                        () -> {
                            try {
                                connection.close();
                            } catch (IOException ignored) {
                                // Membership cleanup is best effort; the server also expires it.
                            }
                        },
                        "quoridor-online-leave");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    private abstract class Request extends SwingWorker<OnlineState, Void> {
        private final long requestGeneration;
        private final boolean initial;
        private final boolean polling;
        private OnlineClient connection;

        Request(long requestGeneration, OnlineClient connection, boolean initial, boolean polling) {
            this.requestGeneration = requestGeneration;
            this.connection = connection;
            this.initial = initial;
            this.polling = polling;
        }

        abstract OnlineState request(OnlineClient connection) throws IOException;

        OnlineClient initialClient() {
            throw new IllegalStateException("Only an initial request creates a client");
        }

        @Override
        protected OnlineState doInBackground() throws IOException {
            try {
                if (connection == null) {
                    connection = initialClient();
                }
                return request(connection);
            } finally {
                if (isCancelled() || requestGeneration != generation) {
                    closeLater(connection);
                }
            }
        }

        @Override
        protected void done() {
            if (requestGeneration != generation || worker != this) {
                return;
            }
            worker = null;
            try {
                OnlineState state = get();
                if (initial) {
                    client = connection;
                }
                connected = true;
                setConnectionProblem(false);
                if (requestGeneration != generation) {
                    return;
                }
                if (state.status() == OnlineState.Status.CLOSED) {
                    pollTimer.stop();
                } else {
                    pollTimer.start();
                }
                listener.onState(state);
            } catch (CancellationException ignored) {
                // A newer generation owns the interface after leave, restart or close.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                failed(interrupted);
            } catch (ExecutionException failed) {
                failed(failed.getCause());
            } finally {
                if (requestGeneration == generation) {
                    setBusy(false);
                }
            }
        }

        private void failed(Throwable error) {
            if (initial) {
                active = false;
                connected = false;
                closeLater(connection);
                listener.onFailure(failureCode(error));
            } else if (terminalFailure(error)) {
                pollTimer.stop();
                OnlineClient previousClient = client;
                client = null;
                active = false;
                connected = false;
                reconnecting = false;
                closeLater(previousClient);
                listener.onFailure(failureCode(error));
            } else if (polling) {
                connected = false;
                setConnectionProblem(true);
            } else {
                if (!(error instanceof OnlineException)) {
                    connected = false;
                    setConnectionProblem(true);
                }
                listener.onFailure(failureCode(error));
            }
        }

        private boolean terminalFailure(Throwable error) {
            return error instanceof OnlineException online
                    && (online.httpStatus() == 401
                            || online.httpStatus() == 404
                            || online.httpStatus() == 410
                            || online.code().equals("NO_MEMBERSHIP"));
        }

        private String failureCode(Throwable error) {
            if (error instanceof OnlineException online) {
                if (online.httpStatus() == 401) {
                    return online.code().equals("NO_MEMBERSHIP") ? "NO_MEMBERSHIP" : "UNAUTHORIZED";
                }
                if (online.httpStatus() == 404) {
                    return "ROOM_NOT_FOUND";
                }
                if (online.httpStatus() == 410) {
                    return "ROOM_CLOSED";
                }
                return online.code();
            }
            if (error instanceof IllegalArgumentException
                    || error instanceof NullPointerException) {
                return initial ? "INVALID_SERVER" : "INVALID_REQUEST";
            }
            return "CONNECTION_FAILED";
        }
    }
}
