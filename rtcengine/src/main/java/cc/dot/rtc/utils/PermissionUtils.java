package cc.dot.rtc.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import org.webrtc.Logging;

/**
 * Created by xiang on 11/09/2018.
 */

public class PermissionUtils {

    private static final String TAG = PermissionUtils.class.getSimpleName();
    private static final int REQUEST_CODE = 0xFF;

    private static String[] PERMISSIONS = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
    private Callback mCallback;

    public boolean hasPermissions(Context context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (PackageManager.PERMISSION_DENIED == context.checkSelfPermission(Manifest.permission.CAMERA)) {
                Logging.w(TAG, "has not permission " + Manifest.permission.CAMERA);
                return false;
            }
            if (PackageManager.PERMISSION_DENIED == context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)) {
                Logging.w(TAG, "has not permission " + Manifest.permission.CAMERA);
                return false;
            }

        }

        return true;
    }

    public void requestPermissions(Activity activity, Callback callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        for (String p : PERMISSIONS) {
            if (PackageManager.PERMISSION_DENIED == activity.checkSelfPermission(p)) {
                Logging.w(TAG, "request permission " + p);
                this.mCallback = callback;
                startRequestPermission(activity);
                break;
            }
        }


    }

    private void startRequestPermission(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        activity.requestPermissions(PERMISSIONS, REQUEST_CODE);
    }

    /**
     * if not permission  throw Error
     *
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (REQUEST_CODE != requestCode) {
            return;
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < grantResults.length; i++) {

            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Logging.w(TAG, "denied permission " + permissions[i]);
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append("denied permission " + permissions[i]);

            }

        }


        if (mCallback != null) {
            if (sb.length() > 0) {
                mCallback.onFailure(sb.toString());
            } else {
                mCallback.onSuccess();
            }
        }
    }

    public interface Callback {
        void onSuccess();

        void onFailure(String msg);
    }
}
