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

package freed.cam.apis.basecamera.modules;

import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.Nullable;
import freed.utils.Log;

import com.troop.freedcam.R;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;

import freed.cam.apis.basecamera.CameraWrapperInterface;
import freed.utils.AppSettingsManager;

/**
 * Created by troop on 09.12.2014.
 */
public abstract class ModuleHandlerAbstract implements ModuleHandlerInterface
{
    public enum CaptureStates
    {
        video_recording_stop,
        video_recording_start,
        image_capture_stop,
        image_capture_start,
        continouse_capture_start,
        continouse_capture_stop,
        continouse_capture_work_start,
        continouse_capture_work_stop,
        cont_capture_stop_while_working,
        cont_capture_stop_while_notworking,
    }

    public interface CaptureStateChanged
    {
        void onCaptureStateChanged(CaptureStates captureStates);
    }

    private final ArrayList<CaptureStateChanged> onCaptureStateChangedListners;

    private final String TAG = ModuleHandlerAbstract.class.getSimpleName();
    public AbstractMap<String, ModuleInterface> moduleList;
    protected ModuleInterface currentModule;
    protected CameraWrapperInterface cameraUiWrapper;

    protected CaptureStateChanged workerListner;

    //holds all listner for the modulechanged event
    private final ArrayList<ModuleChangedEvent> moduleChangedListner;
    //holds all listner for recorstatechanged
    private final ArrayList<I_RecorderStateChanged> RecorderStateListners;
    private Handler uihandler;
    private HandlerThread mBackgroundThread;
    protected Handler mBackgroundHandler;



    protected AppSettingsManager appSettingsManager;

    public ModuleHandlerAbstract(CameraWrapperInterface cameraUiWrapper)
    {
        this.cameraUiWrapper = cameraUiWrapper;
        moduleList = new HashMap<>();
        moduleChangedListner = new ArrayList<>();
        RecorderStateListners = new ArrayList<>();
        this.appSettingsManager = cameraUiWrapper.GetAppSettingsManager();
        onCaptureStateChangedListners = new ArrayList<>();
        uihandler = new Handler(Looper.getMainLooper());
        startBackgroundThread();

        workerListner = new CaptureStateChanged() {
            @Override
            public void onCaptureStateChanged(final CaptureStates captureStates)
            {
                for (int i = 0; i < onCaptureStateChangedListners.size(); i++)
                {

                    if (onCaptureStateChangedListners.get(i) == null) {
                        onCaptureStateChangedListners.remove(i);
                        i--;
                    }
                    else
                    {
                        final int pos = i;
                        uihandler.post(new Runnable() {
                            @Override
                            public void run() {
                                onCaptureStateChangedListners.get(pos).onCaptureStateChanged(captureStates);
                            }
                        });

                    }
                }
            }
        };
    }

    /**
     * Load the new module
     * @param name of the module to load
     */
    @Override
    public void SetModule(String name) {
        if (currentModule !=null) {
            currentModule.DestroyModule();
            currentModule.SetCaptureStateChangedListner(null);
            currentModule = null;
        }
        currentModule = moduleList.get(name);
        currentModule.InitModule();
        ModuleHasChanged(currentModule.ModuleName());
        currentModule.SetCaptureStateChangedListner(workerListner);
        Log.d(TAG, "Set Module to " + name);
    }

    @Override
    public String GetCurrentModuleName() {
        if (currentModule != null)
            return currentModule.ModuleName();
        else return cameraUiWrapper.getResString(R.string.module_picture);
    }

    @Override
    public @Nullable ModuleInterface GetCurrentModule() {
        if (currentModule != null)
            return currentModule;
        return null;
    }

    @Override
    public boolean DoWork() {
        if (currentModule != null) {
            currentModule.DoWork();
            return true;
        }
        else
            return false;
    }

    @Override
    public void SetWorkListner(CaptureStateChanged workerListner)
    {
        if (!onCaptureStateChangedListners.contains(workerListner))
            onCaptureStateChangedListners.add(workerListner);
    }


    public void CLEARWORKERLISTNER()
    {
        if (onCaptureStateChangedListners != null)
            onCaptureStateChangedListners.clear();
    }

    /**
     * Add a listner for Moudlechanged events
     * @param listner the listner for the event
     */
    public  void addListner(ModuleChangedEvent listner)
    {
        if (!moduleChangedListner.contains(listner))
            moduleChangedListner.add(listner);
    }

    /**
     * Gets thrown when the module has changed
     * @param module the new module that gets loaded
     */
    public void ModuleHasChanged(final String module)
    {
        if (moduleChangedListner.size() == 0)
            return;
        for (int i = 0; i < moduleChangedListner.size(); i++)
        {
            if (moduleChangedListner.get(i) == null) {
                moduleChangedListner.remove(i);
                i--;
            }
            else
            {
                final int toget = i;
                uihandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (moduleChangedListner.size() > 0)
                            moduleChangedListner.get(toget).onModuleChanged(module);
                    }
                });

            }
        }
    }


    public void AddRecoderChangedListner(I_RecorderStateChanged recorderStateChanged)
    {
        RecorderStateListners.add(recorderStateChanged);
    }

    public void onRecorderstateChanged(int state)
    {
        for (I_RecorderStateChanged lisn : RecorderStateListners)
            lisn.RecordingStateChanged(state);
    }

    //clears all listner this happens when the camera gets destroyed
    public void CLEAR()
    {
        moduleChangedListner.clear();
        RecorderStateListners.clear();
        stopBackgroundThread();
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread()
    {
        Log.d(TAG,"stopBackgroundThread");
        if(mBackgroundThread == null)
            return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            mBackgroundThread.quitSafely();
        }
        else
            mBackgroundThread.quit();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            Log.WriteEx(e);
        }
    }

}
