/*
 *
 *     Copyright (C) 2015 George Kiarie
 *     This program is free software; you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation; either version 2 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc.,
 *     51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 * /
 */

package com.freedcam.apis.camera1.parameters.manual;

import android.content.Context;
import android.hardware.Camera.Parameters;
import android.os.Handler;

import com.freedcam.apis.KEYS;
import com.freedcam.apis.basecamera.interfaces.I_CameraHolder;
import com.freedcam.apis.camera1.parameters.ParametersHandler;
import com.freedcam.utils.DeviceUtils.Devices;
import com.freedcam.utils.Logger;
import com.troop.freedcam.R;

/**
 * Created by troop on 25.11.2015.
 */
public class ShutterManualZTE extends BaseManualParameter
{
    private I_CameraHolder baseCameraHolder;
    private final String TAG = ShutterManualZTE.class.getSimpleName();

    /**
     * @param parameters
     * @param parametersHandler
     */
    public ShutterManualZTE(Context context, Parameters parameters, I_CameraHolder baseCameraHolder, ParametersHandler parametersHandler) {
        super(context, parameters, "", "", "", parametersHandler,1);
        this.baseCameraHolder = baseCameraHolder;
        if(parametersHandler.appSettingsManager.getDevice() == Devices.ZTE_ADV)
            stringvalues = context.getResources().getStringArray(R.array.shutter_values_zte_z5s);
        else
            stringvalues = context.getResources().getStringArray(R.array.shutter_values_zte_z7);

        isSupported = true;
    }

    @Override
    public boolean IsSupported() {
        return super.IsSupported();
    }

    @Override
    public boolean IsVisible() {
        return  IsSupported();
    }

    @Override
    public void SetValue(int valueToSet)
    {
        currentInt = valueToSet;
        String shutterstring = stringvalues[currentInt];
        if (shutterstring.contains("/")) {
            String[] split = shutterstring.split("/");
            Double a = Double.parseDouble(split[0]) / Double.parseDouble(split[1]);
            shutterstring = "" + a;
        }
        if(!stringvalues[currentInt].equals(KEYS.AUTO))
        {
            try {
                shutterstring = setExposureTimeToParameter(shutterstring);
            }
            catch (Exception ex)
            {
                Logger.d("Freedcam", "Shutter Set FAil");
            }
        }
        else
        {
            setShutterToAuto();
        }
        Logger.e(TAG, shutterstring);
    }

    private void setShutterToAuto()
    {
        try
        {
            Handler handler = new Handler();
            Runnable r = new Runnable() {
                public void run() {
                    parametersHandler.SetZTESlowShutter();
                    baseCameraHolder.StopPreview();
                    baseCameraHolder.StartPreview();
                }
            };
            handler.postDelayed(r, 1);
        }
        catch (Exception ex)
        {
            Logger.exception(ex);
        }

    }

    private String setExposureTimeToParameter(final String shutterstring)
    {
        try {

            Handler handler = new Handler();
            Runnable r = new Runnable() {
                public void run() {

                    parameters.set("slow_shutter", shutterstring);
                    parameters.set("slow_shutter_addition", "1");
                    parametersHandler.SetParametersToCamera(parameters);

                    if(Double.parseDouble(shutterstring) <= 0.5 && Double.parseDouble(shutterstring) >= 0.0005 ){
                        baseCameraHolder.StopPreview();
                        baseCameraHolder.StartPreview();
                    }
                }
            };
            handler.post(r);
        }
        catch (Exception ex)
        {
            Logger.exception(ex);
        }
        return shutterstring;
    }
}
