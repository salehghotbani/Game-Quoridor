package Online;

import java.io.IOException;

/** A stable protocol error code suitable for translation by the desktop interface. */
public final class OnlineException extends IOException {
    private static final long serialVersionUID = 1L;
    private final String code;
    private final int httpStatus;

    public OnlineException(String code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
