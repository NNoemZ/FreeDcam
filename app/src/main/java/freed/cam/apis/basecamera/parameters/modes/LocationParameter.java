/*
 *
 *     Copyright (C) 2015 Ingo Fuchs
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

package freed.cam.apis.basecamera.parameters.modes;

import com.troop.freedcam.R;

import freed.cam.apis.basecamera.CameraWrapperInterface;
import freed.utils.AppSettingsManager;
import freed.utils.PermissionHandler;

/**
 * Created by troop on 21.07.2015.
 * if you get fine loaction error ignore it, permission are set in app project where everything
 * gets builded
 */
public class LocationParameter extends AbstractModeParameter
{
    private final CameraWrapperInterface cameraUiWrapper;


    public LocationParameter(CameraWrapperInterface cameraUiWrapper)
    {
        this.cameraUiWrapper = cameraUiWrapper;
    }

    @Override
    public boolean IsSupported() {
        return true;
    }

    @Override
    public String GetValue()
    {
        if (cameraUiWrapper == null ||cameraUiWrapper.GetAppSettingsManager() == null)
            return cameraUiWrapper.getResString(R.string.off_);
        if (cameraUiWrapper.GetAppSettingsManager().getApiString(AppSettingsManager.SETTING_LOCATION).equals(""))
            cameraUiWrapper.GetAppSettingsManager().setApiString(AppSettingsManager.SETTING_LOCATION, cameraUiWrapper.getResString(R.string.off_));
        return cameraUiWrapper.GetAppSettingsManager().getApiString(AppSettingsManager.SETTING_LOCATION);
    }

    @Override
    public String[] GetValues() {
        return new String[] { cameraUiWrapper.getResString(R.string.off_), cameraUiWrapper.getResString(R.string.on_) };
    }

    @Override
    public void SetValue(String valueToSet, boolean setToCamera)
    {
        cameraUiWrapper.GetAppSettingsManager().setApiString(AppSettingsManager.SETTING_LOCATION, valueToSet);
        if (valueToSet.equals(cameraUiWrapper.getResString(R.string.off_)))
            cameraUiWrapper.getActivityInterface().getLocationHandler().stopLocationListining();
        if (valueToSet.equals(cameraUiWrapper.getResString(R.string.on_)) && cameraUiWrapper.getActivityInterface().getPermissionHandler().hasLocationPermission(onLocationPermission))
            cameraUiWrapper.getActivityInterface().getLocationHandler().startLocationListing();
    }

    private PermissionHandler.PermissionCallback onLocationPermission = new PermissionHandler.PermissionCallback() {
        @Override
        public void permissionGranted(boolean granted) {
            cameraUiWrapper.getActivityInterface().getLocationHandler().startLocationListing();
        }
    };

}
