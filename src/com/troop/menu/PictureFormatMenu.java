package com.troop.menu;

import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import com.troop.freecam.MainActivity;
import com.troop.freecam.camera.CameraManager;
import com.troop.freecam.manager.parameters.ParametersManager;
import com.troop.freecam.utils.DeviceUtils;

import java.security.PublicKey;

/**
 * Created by George on 12/6/13.
 */
public class PictureFormatMenu extends BaseMenu  {
    public PictureFormatMenu(CameraManager camMan, MainActivity activity) {
        super(camMan, activity);
    }
    MainActivity mainActivity;

    private String Pict;

    String[] picf;
    String xxx;

    @Override
    public void onClick(View v)
    {

        if(camMan.Running)
        {
            try
            {
                //TODO get the values from the camera parameters
                String Values = "jpeg,raw,bayer-mipi-10bggr,bayer-mipi-10rggb,bayer-mipi-10grgb,bayer-mipi-10gbrg,bayer-qcom-10bggr,bayer-qcom-10rggb,bayer-qcom-10grgb,bayer-qcom-10gbrg";

                if(DeviceUtils.isForcedDragon())
                {
                    picf = Values.split(",");
                }
                else
                {
                    picf = camMan.parametersManager.getParameters().get("picture-format-values").split(",");
                }


            }
            catch (Exception ex)
            {

            }
        }
        if (picf != null && !picf.equals(""))
        {
            PopupMenu popupMenu = new PopupMenu(activity, super.GetPlaceHolder());
            //popupMenu.getMenuInflater().inflate(R.menu.menu_popup_flash, popupMenu.getMenu().);
            for (int i = 0; i < picf.length; i++) {
                popupMenu.getMenu().add((CharSequence) picf[i]);
            }
            popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    String tmp = item.toString();
                    try
                    {
                    camMan.parametersManager.getParameters().set("picture-format", tmp);
                    camMan.parametersManager.string_set(tmp);
                        //camMan.parametersManager.getParameters().set("raw-size","4208x3120");
                        camMan.Restart(false);
                    }
                    catch (Exception ex)
                    {
                        ex.printStackTrace();
                    }
                    //camMan.parametersManager.GetCamP(tmp);
                    

                    Log.d("CurrentP", tmp);
                    xxx = tmp;
                    

                    //activity.buttonPictureFormat.SetValue(tmp);

                    if (camMan.Settings.Cameras.is3DMode())
                        preferences.edit().putString(ParametersManager.Preferences_PictureFormat, tmp).commit();
                    if (camMan.Settings.Cameras.is2DMode())
                        preferences.edit().putString(ParametersManager.Preferences_PictureFormat, tmp).commit();
                    if (camMan.Settings.Cameras.isFrontMode())
                        preferences.edit().putString(ParametersManager.Preferences_PictureFormat, tmp).commit();
                    //preferences.edit().putString("color", tmp).commit();


                    return true;
                }
            });

            popupMenu.show();
        }

    }

    public String PictureString()
    {
        return xxx;
    }


}
