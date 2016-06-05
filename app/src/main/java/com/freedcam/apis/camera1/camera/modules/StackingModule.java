package com.freedcam.apis.camera1.camera.modules;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;

import com.freedcam.apis.basecamera.camera.modules.AbstractModuleHandler;
import com.freedcam.apis.basecamera.camera.modules.AbstractModuleHandler.CaptureModes;
import com.freedcam.apis.basecamera.camera.modules.I_Callbacks;
import com.freedcam.apis.basecamera.camera.modules.ModuleEventHandler;
import com.freedcam.apis.camera1.camera.CameraHolderApi1;
import com.freedcam.apis.KEYS;
import com.freedcam.apis.camera1.camera.parameters.modes.StackModeParameter;
import com.freedcam.ui.handler.MediaScannerManager;
import com.freedcam.utils.AppSettingsManager;
import com.freedcam.utils.FreeDPool;
import com.freedcam.utils.Logger;
import com.freedcam.utils.StringUtils;
import com.imageconverter.ScriptC_imagestack;
import com.imageconverter.ScriptField_MinMaxPixel;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by GeorgeKiarie on 13/04/2016.
 */
public class StackingModule extends PictureModule implements I_Callbacks.PictureCallback
{
    final String TAG = StackingModule.class.getSimpleName();
    private boolean KeepStacking = false;
    private int FrameCount = 0;
    private String SessionFolder="";

    private Allocation mAllocationInput;
    private Allocation mAllocationOutput;
    private ScriptField_MinMaxPixel medianMinMax;
    private ScriptC_imagestack imagestack;
    private RenderScript mRS;
    private List<File> capturedPics;

    public StackingModule(CameraHolderApi1 cameraHandler, ModuleEventHandler eventHandler, Context context, AppSettingsManager appSettingsManager) {
        super(cameraHandler, eventHandler,context,appSettingsManager);
        name = ModuleHandler.MODULE_STACKING;

    }

    @Override
    public String ModuleName() {
        return name;
    }

    @Override
    public boolean DoWork() {
        Logger.d(TAG, "isWorking: " + isWorking + " KeepStacking: " + KeepStacking);
        if (!isWorking && !KeepStacking)
        {
            FrameCount = 0;
            SessionFolder = StringUtils.GetDCIMFolder(appSettingsManager.GetWriteExternal())+ StringUtils.getStringDatePAttern().format(new Date()) + "/";
            Logger.d(TAG,"Start Stacking");
            KeepStacking = true;
            capturedPics = new ArrayList<>();
            initRsStuff();
            ParameterHandler.ZSL.SetValue("off", true);
            changeWorkState(AbstractModuleHandler.CaptureModes.continouse_capture_start);
            final String picFormat = ParameterHandler.PictureFormat.GetValue();
            if (!picFormat.equals(KEYS.JPEG))
                ParameterHandler.PictureFormat.SetValue(KEYS.JPEG,true);
            isWorking =true;
            cameraHolder.TakePicture(null, this);
            return true;
        }
        else if (KeepStacking)
        {
            Logger.d(TAG, "Stop Stacking");
            KeepStacking = false;
            if (isWorking)
                changeWorkState(CaptureModes.cont_capture_stop_while_working);
            else
                changeWorkState(CaptureModes.cont_capture_stop_while_notworking);
            return false;
        }
        return false;

    }

    @Override
    public void onPictureTaken(final byte[] data) {
        Logger.d(TAG, "Take Picture Callback");
        FreeDPool.Execute(new Runnable() {
            @Override
            public void run() {
                File f = new File(StringUtils.getFilePath(appSettingsManager.GetWriteExternal(), ".jpg"));
                processData(data, f);
            }
        });
    }

    @Override
    public String ShortName() {
        return "Stack";
    }

    @Override
    public String LongName() {
        return "Stacking";
    }

    @Override
    public boolean IsWorking() {
        return isWorking;
    }

    @Override
    public void InitModule()
    {

    }

    @Override
    public void DestroyModule(){

    }

    private void initRsStuff()
    {
        if(mRS == null)
        {
            mRS = RenderScript.create(context);
            mRS.setPriority(RenderScript.Priority.LOW);
        }
        int mWidth = Integer.parseInt(ParameterHandler.PictureSize.GetValue().split("x")[0]);
        int mHeight = Integer.parseInt(ParameterHandler.PictureSize.GetValue().split("x")[1]);
        Type.Builder tbIn2 = new Type.Builder(mRS, Element.RGBA_8888(mRS));
        tbIn2.setX(mWidth);
        tbIn2.setY(mHeight);
        mAllocationInput = Allocation.createTyped(mRS, tbIn2.create(), Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        mAllocationOutput = Allocation.createTyped(mRS, tbIn2.create(), Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        imagestack = new ScriptC_imagestack(mRS);
        imagestack.set_Width(mWidth);
        imagestack.set_Height(mHeight);
        if (ParameterHandler.imageStackMode.GetValue().equals(StackModeParameter.MEDIAN))
        {
            medianMinMax = new ScriptField_MinMaxPixel(mRS, mWidth * mHeight);
            imagestack.bind_medianMinMaxPixel(medianMinMax);
        }

        imagestack.set_gCurrentFrame(mAllocationInput);
        imagestack.set_gLastFrame(mAllocationOutput);
    }


    private void processData(byte[] data, File file)
    {
        cameraHolder.StartPreview();
        Logger.d(TAG, "start preview");
        Logger.d(TAG,"The Data Is " + data.length + " bytes Long" + " and the path is " + file.getAbsolutePath());
        //create file to save
        File f = new File(SessionFolder+StringUtils.getStringDatePAttern().format(new Date())+".jpg");
        //save file
        saveBytesToFile(data,f);
        //add file for later stack
        capturedPics.add(f);
        //Add file to media storage that its visible by mtp
        MediaScannerManager.ScanMedia(context, f);

        isWorking = false;
        //notice ui/shutterbutton about the current workstate
        changeWorkState(CaptureModes.continouse_capture_work_stop);
        cameraHolder.SendUIMessage("Captured Picture: " + FrameCount++);
        //Take next picture for later stacking aslong keepstacking is true
        if (KeepStacking)
        {
            changeWorkState(CaptureModes.continouse_capture_work_start);
            Logger.d(TAG, "keepstacking take next pic");
            isWorking = true;
            cameraHolder.TakePicture(null, this);
        }
        else //keepstacking is false, lets start mergin all pics
        {
            Logger.d(TAG, "End of Stacking create bitmap and compress");
            FrameCount = 0;
            isWorking = true;
            changeWorkState(CaptureModes.continouse_capture_work_start);

            for (File s : capturedPics)
            {
                cameraHolder.SendUIMessage("Stacked: " + FrameCount++ + "/"+ capturedPics.size());
                stackImage(s);
            }
            changeWorkState(AbstractModuleHandler.CaptureModes.continouse_capture_work_stop);
            int mWidth = Integer.parseInt(ParameterHandler.PictureSize.GetValue().split("x")[0]);
            int mHeight = Integer.parseInt(ParameterHandler.PictureSize.GetValue().split("x")[1]);
            final Bitmap outputBitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
            mAllocationOutput.copyTo(outputBitmap);
            File stackedImg = new File(SessionFolder + StringUtils.getStringDatePAttern().format(new Date()) + "_Stack.jpg");
            SaveBitmapToFile(outputBitmap,stackedImg);
            isWorking = false;
            changeWorkState(CaptureModes.continouse_capture_stop);
            MediaScannerManager.ScanMedia(context, stackedImg);
            eventHandler.WorkFinished(file);
        }
    }

    private void stackImage(File file)
    {
        mAllocationInput.copyFrom(BitmapFactory.decodeFile(file.getAbsolutePath()));
        Logger.d(TAG, "Copied data to inputalloc");

        Logger.d(TAG, "setted inputalloc to RS");
        if (ParameterHandler.imageStackMode.GetValue().equals(StackModeParameter.AVARAGE))
            imagestack.forEach_stackimage_avarage(mAllocationOutput);
        else if (ParameterHandler.imageStackMode.GetValue().equals(StackModeParameter.AVARAGE1x2))
            imagestack.forEach_stackimage_avarage1x2(mAllocationOutput);
        else if (ParameterHandler.imageStackMode.GetValue().equals(StackModeParameter.AVARAGE1x3))
            imagestack.forEach_stackimage_avarage1x3(mAllocationOutput);
        else if (ParameterHandler.imageStackMode.GetValue().equals(StackModeParameter.AVARAGE3x3))
            imagestack.forEach_stackimage_avarage3x3(mAllocationOutput);
        else if(ParameterHandler.imageStackMode.GetValue().equals(StackModeParameter.LIGHTEN))
            imagestack.forEach_stackimage_lighten(mAllocationOutput);
        else if(ParameterHandler.imageStackMode.GetValue().equals(StackModeParameter.LIGHTEN_V))
            imagestack.forEach_stackimage_lightenV(mAllocationOutput);
        else if (ParameterHandler.imageStackMode.GetValue().equals(StackModeParameter.MEDIAN))
        {
            imagestack.bind_medianMinMaxPixel(medianMinMax);
            imagestack.forEach_stackimage_median(mAllocationOutput);
        }
    }


}
