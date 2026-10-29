package Online;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/** Bounded HTTP transport shared by the JVM and Android; never changes global URL defaults. */
final class UrlConnectionTransport {
    record Response(int status, String contentType, byte[] body, boolean bodyUnavailable) {}

    private static final int CONNECT_TIMEOUT_MILLIS = 6000;
    private static final Semaphore IN_FLIGHT = new Semaphore(32);
    private static final ScheduledThreadPoolExecutor DEADLINES = deadlineExecutor();

    private UrlConnectionTransport() {}

    static Response request(URI uri, byte[] body, String credential, Duration timeout)
            throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Online request was interrupted");
        }
        if (!IN_FLIGHT.tryAcquire()) {
            throw new IOException("Online transport is busy");
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout((int) timeout.toMillis());
            connection.setRequestProperty("Accept", Wire.CONTENT_TYPE);
            connection.setRequestProperty("Accept-Encoding", "gzip");
            connection.setRequestProperty("Connection", "close");
            if (credential != null) {
                connection.setRequestProperty("Authorization", "Bearer " + credential);
            }
            connection.setRequestMethod(body == null ? "GET" : "POST");
            if (body != null) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(body.length);
                connection.setRequestProperty("Content-Type", Wire.CONTENT_TYPE);
            }
            try (Deadline deadline = new Deadline(connection, timeout)) {
                try {
                    deadline.check();
                    if (body != null) {
                        try (OutputStream output = connection.getOutputStream()) {
                            deadline.check();
                            output.write(body);
                        }
                    }
                    deadline.check();
                    int status = connection.getResponseCode();
                    deadline.check();
                    if (status >= 300 && status < 400) {
                        throw new IOException("Online server redirects are not allowed");
                    }
                    if (status < 100 || status > 599) {
                        throw new IOException("The server returned an invalid HTTP status");
                    }
                    String encoding = connection.getContentEncoding();
                    boolean gzip = "gzip".equalsIgnoreCase(encoding);
                    if (encoding != null && !encoding.equalsIgnoreCase("identity") && !gzip) {
                        throw new IOException("The server returned an unsupported encoding");
                    }
                    long declaredLength = connection.getContentLengthLong();
                    if (declaredLength > Wire.RESPONSE_LIMIT) {
                        throw new IOException("Online response is too large");
                    }
                    InputStream stream =
                            status >= 400
                                    ? connection.getErrorStream()
                                    : connection.getInputStream();
                    byte[] response;
                    if (stream == null) {
                        response = new byte[0];
                    } else {
                        try (InputStream input = stream) {
                            response = readBounded(input, deadline);
                        }
                    }
                    deadline.check();
                    // JVM URLConnection can discard a fixed-length POST's 401 body when
                    // automatic authentication would require replaying the request. Do not
                    // retry it: the caller can still surface the HTTP authentication status.
                    boolean inaccessibleAuthenticationBody = status == 401 && stream == null;
                    if (!inaccessibleAuthenticationBody
                            && declaredLength >= 0
                            && declaredLength != response.length) {
                        throw new IOException("The server returned an incomplete response");
                    }
                    if (gzip) {
                        try (InputStream decoded =
                                new GZIPInputStream(new ByteArrayInputStream(response))) {
                            response = readBounded(decoded, deadline);
                        }
                    }
                    return new Response(
                            status, connection.getContentType(), response, stream == null);
                } catch (IOException failed) {
                    deadline.check();
                    throw failed;
                }
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            IN_FLIGHT.release();
        }
    }

    private static byte[] readBounded(InputStream input, Deadline deadline) throws IOException {
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (true) {
            deadline.check();
            int count =
                    input.read(
                            buffer,
                            0,
                            Math.min(buffer.length, Wire.RESPONSE_LIMIT + 1 - response.size()));
            deadline.check();
            if (count < 0) {
                return response.toByteArray();
            }
            if (count > Wire.RESPONSE_LIMIT - response.size()) {
                throw new IOException("Online response is too large");
            }
            response.write(buffer, 0, count);
        }
    }

    private static ScheduledThreadPoolExecutor deadlineExecutor() {
        ScheduledThreadPoolExecutor executor =
                new ScheduledThreadPoolExecutor(
                        2,
                        task -> {
                            Thread thread = new Thread(task, "quoridor-http-deadline");
                            thread.setDaemon(true);
                            return thread;
                        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    private static final class Deadline implements AutoCloseable {
        private final HttpURLConnection connection;
        private final Thread owner = Thread.currentThread();
        private final long end;
        private final ScheduledFuture<?> watcher;
        private volatile boolean interrupted;
        private volatile boolean timedOut;

        Deadline(HttpURLConnection connection, Duration timeout) {
            this.connection = connection;
            end = System.nanoTime() + timeout.toNanos();
            watcher =
                    DEADLINES.scheduleWithFixedDelay(
                            this::abortIfExpired, 0, 50, TimeUnit.MILLISECONDS);
        }

        void check() throws IOException {
            if (owner.isInterrupted() || interrupted) {
                interrupted = true;
                owner.interrupt();
                throw new InterruptedIOException("Online request was interrupted");
            }
            if (timedOut || System.nanoTime() - end >= 0) {
                timedOut = true;
                throw new SocketTimeoutException("Online server request timed out");
            }
        }

        private void abortIfExpired() {
            if (owner.isInterrupted()) {
                interrupted = true;
            } else if (System.nanoTime() - end >= 0) {
                timedOut = true;
            } else {
                return;
            }
            connection.disconnect();
        }

        @Override
        public void close() {
            watcher.cancel(false);
        }
    }
}
