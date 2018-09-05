package cc.dot.rtc.exception;

import android.annotation.TargetApi;
import android.os.Build;

/**
 * Created by xiang on 05/09/2018.
 */

public class BuilderException extends RuntimeException {
    private static final String TAG = BuilderException.class.getSimpleName();

    public BuilderException() {
    }

    public BuilderException(String message) {
        super(message);
    }

    public BuilderException(String message, Throwable cause) {
        super(message, cause);
    }

    public BuilderException(Throwable cause) {
        super(cause);
    }

    @TargetApi(Build.VERSION_CODES.N)
    public BuilderException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
