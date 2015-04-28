package com.troop.freedcam.camera2.parameters.modes;

import android.annotation.TargetApi;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.os.Handler;

import com.troop.freedcam.camera2.BaseCameraHolderApi2;

/**
 * Created by troop on 28.04.2015.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class WhiteBalanceApi2 extends BaseModeApi2
{


    public WhiteBalanceApi2(Handler handler, BaseCameraHolderApi2 baseCameraHolderApi2) {
        super(handler, baseCameraHolderApi2);
        int[] values = cameraHolder.characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
        if (values.length > 1)
            this.isSupported = true;
    }

    enum WhiteBalanceValues
    {

        OFF,
        AUTO,
        INCANDESCENT,
        FLUORESCENT,
        WARM_FLUORESCENT,
        DAYLIGHT,
        CLOUDY_DAYLIGHT,
        TWILIGHT,
        SHADE,
    }

    @Override
    public boolean IsSupported()
    {
        return this.isSupported;
    }

    @Override
    public void SetValue(String valueToSet, boolean setToCamera)
    {
        if (valueToSet.contains("unknown"))
        {
            String t = valueToSet.substring(valueToSet.length() -2);
            int i = Integer.parseInt(t);
            cameraHolder.setIntKeyToCam(CaptureRequest.CONTROL_AWB_MODE, i);
        }
        else
        {
            WhiteBalanceValues sceneModes = Enum.valueOf(WhiteBalanceValues.class, valueToSet);
            cameraHolder.setIntKeyToCam(CaptureRequest.CONTROL_AWB_MODE, sceneModes.ordinal());
        }
        //cameraHolder.mPreviewRequestBuilder.build();
    }

    @Override
    public String GetValue()
    {
        if (cameraHolder != null && cameraHolder.mPreviewRequest != null)
        {
            int i = cameraHolder.mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AWB_MODE);
            WhiteBalanceValues sceneModes = WhiteBalanceValues.values()[i];
            return sceneModes.toString();
        }
        else
            return "AUTO";
    }

    @Override
    public String[] GetValues()
    {
        int[] values = cameraHolder.characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
        String[] retvals = new String[values.length];
        for (int i = 0; i < values.length; i++)
        {
            try {
                WhiteBalanceValues sceneModes = WhiteBalanceValues.values()[values[i]];
                retvals[i] = sceneModes.toString();
            }
            catch (Exception ex)
            {
                if (i < 10)
                    retvals[i] = "unknown awb 0" + values[i];
                else
                    retvals[i] = "unknown awb " + values[i];
            }

        }
        return retvals;
    }
}
