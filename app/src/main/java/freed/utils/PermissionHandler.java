package freed.utils;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;

/**
 * Created by troop on 09.03.2017.
 */

public class PermissionHandler
{
    private final String TAG = PermissionHandler.class.getSimpleName();
    private PermissionCallback callbackToReturn;

    public interface PermissionCallback
    {
        void permissionGranted(boolean granted);
    }

    Activity activity;

    public PermissionHandler(Activity activity)
    {
        this.activity = activity;
    }

    public boolean hasCameraPermission(PermissionCallback callbackToReturn)
    {
        return hasPermission(callbackToReturn, Manifest.permission.CAMERA);
    }

    public boolean hasRecordAudioPermission(PermissionCallback callbackToReturn)
    {
        return hasPermission(callbackToReturn, Manifest.permission.RECORD_AUDIO);
    }

    public boolean hasExternalSDPermission(PermissionCallback callbackToReturn)
    {
        return hasPermission(callbackToReturn, Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    public boolean hasLocationPermission(PermissionCallback callbackToReturn)
    {
        this.callbackToReturn = callbackToReturn;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED)
            {
                Log.d(TAG, "Request LocationPermission");
                activity.requestPermissions(new String[]{
                        Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},1);
                return false;
            }
            return true;
        }
        return true;
    }

    public boolean hasWifiPermission(PermissionCallback callbackToReturn) {
        this.callbackToReturn = callbackToReturn;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            if (activity.checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE)
                    != PackageManager.PERMISSION_GRANTED)
            {
                Log.d(TAG, "Request wifiPermission");
                activity.requestPermissions(new String[]{
                        Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE},1);
                return false;
            }
            return true;
        }
        return true;
    }

    private boolean hasPermission(PermissionCallback callbackToReturn, String permission)
    {
        this.callbackToReturn = callbackToReturn;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            if (activity.checkSelfPermission(permission)
                    != PackageManager.PERMISSION_GRANTED)
            {
                Log.d(TAG, "Request Permission:"+permission);
                activity.requestPermissions(new String[]{
                        permission},1);
                return false;
            }
            return true;
        }
        return true;
    }


    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        if (callbackToReturn == null)
            return;
        boolean allGranted = true;
        for (int i = 0; i < permissions.length;i++) {
            String perm = permissions[i];
            allGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
            if (!allGranted)
                callbackToReturn.permissionGranted(false);
            break;
        }
        callbackToReturn.permissionGranted(true);
        callbackToReturn = null;
    }
}
