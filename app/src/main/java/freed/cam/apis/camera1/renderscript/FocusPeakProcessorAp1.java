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

package freed.cam.apis.camera1.renderscript;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Build.VERSION_CODES;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RSInvalidStateException;
import android.renderscript.RSRuntimeException;
import android.renderscript.Type.Builder;
import freed.utils.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;

import com.troop.freedcam.R;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import freed.cam.apis.basecamera.CameraStateEvents;
import freed.cam.apis.basecamera.CameraWrapperInterface;
import freed.cam.apis.basecamera.FocuspeakProcessor;
import freed.cam.apis.basecamera.Size;
import freed.cam.apis.basecamera.modules.ModuleChangedEvent;
import freed.cam.apis.camera1.CameraHolder;
import freed.cam.apis.camera1.I_AspectRatio;
import freed.utils.FreeDPool;
import freed.utils.RenderScriptHandler;


/**
 * Created by troop on 24.08.2015.
 */
@TargetApi(VERSION_CODES.KITKAT)
public class FocusPeakProcessorAp1 implements PreviewCallback, CameraStateEvents,ModuleChangedEvent, FocuspeakProcessor
{
    private final String TAG = FocusPeakProcessorAp1.class.getSimpleName();
    private final I_AspectRatio output;
    private final CameraWrapperInterface cameraUiWrapper;

    private int mHeight;
    private int mWidth;
    private Surface mSurface;

    private boolean enable;
    private boolean doWork;
    private boolean isWorking;
    private final RenderScriptHandler renderScriptHandler;
    private int expectedByteSize;
    private final BlockingQueue<byte[]> frameQueue = new ArrayBlockingQueue<>(2);

    public FocusPeakProcessorAp1(I_AspectRatio output, CameraWrapperInterface cameraUiWrapper, Context context, RenderScriptHandler renderScriptHandler)
    {
        Log.d(TAG, "Ctor");
        this.output = output;
        this.cameraUiWrapper = cameraUiWrapper;
        Context context1 = context;
        this.renderScriptHandler = renderScriptHandler;
        this.cameraUiWrapper.GetModuleHandler().addListner(this);
        SurfaceTextureListener previewSurfaceListner = new SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                mWidth = width;
                mHeight = height;
                Log.d(TAG, "SurfaceSizeAvail");
                mSurface = new Surface(surface);
                try {
                    if (FocusPeakProcessorAp1.this.renderScriptHandler.GetOut() != null && FocusPeakProcessorAp1.this.renderScriptHandler.GetOut().getUsage() == Allocation.USAGE_IO_OUTPUT)
                        FocusPeakProcessorAp1.this.renderScriptHandler.GetOut().setSurface(mSurface);
                    else {
                        Log.d(TAG, "Allocout null or not USAGE_IO_OUTPUT");
                        Size size = new Size(FocusPeakProcessorAp1.this.cameraUiWrapper.GetAppSettingsManager().previewSize.get());
                        reset(size.width, size.height);
                    }
                } catch (NullPointerException ex) {
                    Log.WriteEx(ex);
                }

                clear_preview("onSurfaceTextureAvailable");
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "SurfaceSizeChanged");
                mSurface = new Surface(surface);
                try {
                    if (FocusPeakProcessorAp1.this.renderScriptHandler.GetOut() != null)
                        FocusPeakProcessorAp1.this.renderScriptHandler.GetOut().setSurface(mSurface);
                    else {
                        Log.d(TAG, "Allocout null");

                    }
                } catch (RSInvalidStateException ex) {
                    Log.WriteEx(ex);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                Log.d(TAG, "SurfaceDestroyed");
                clear_preview("onSurfaceTextureDestroyed");
                mSurface = null;


                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        };
        output.setSurfaceTextureListener(previewSurfaceListner);
        clear_preview("Ctor");
    }

    @Override
    public boolean isEnabled() {
        return enable;
    }

    @Override
    public void Enable(boolean enable)
    {
        Log.d(TAG, "Enable:" + enable);
        this.enable = enable;
        setEnable(this.enable);
    }

    private void setEnable(boolean enabled)
    {
        Log.d(TAG, "setEnable" + enabled);
        if (enabled)
        {
            Size size = new Size(cameraUiWrapper.GetParameterHandler().PreviewSize.GetValue());
            reset(size.width, size.height);
            startPeak();
            Log.d(TAG, "Set PreviewCallback");
            Log.d(TAG, "enable focuspeak");
            show_preview();
        }
        else
        {
            Log.d(TAG, "stop focuspeak");
            cameraUiWrapper.GetCameraHolder().ResetPreviewCallback();
            clear_preview("setEnable");

        }
        if(cameraUiWrapper.GetParameterHandler().Focuspeak != null && cameraUiWrapper.GetParameterHandler().Focuspeak.IsSupported())
            cameraUiWrapper.GetParameterHandler().Focuspeak.onValueHasChanged(enabled +"");
    }

    private void clear_preview(String from)
    {
        if (!doWork || !enable) {
            output.setAlpha(0);
            Log.d(TAG, "Preview cleared from:" + from);
        }
    }
    private void show_preview()
    {
        if (doWork && enable) {
            output.setAlpha(1);
            Log.d(TAG, "Preview show from:" + "setEnable");
        }
    }

    private void startPeak()
    {
        FreeDPool.Execute(new Runnable() {
            @Override
            public void run() {
                isWorking = true;
                byte[] tmp;
                while (enable)
                {
                    try {
                        //take one stored frame for processing
                        tmp = frameQueue.take();
                        renderScriptHandler.GetIn().copyFrom(tmp);

                        renderScriptHandler.freedcamScript.forEach_focuspeakcam1(renderScriptHandler.GetOut());
                        renderScriptHandler.GetOut().ioSend();
                        //pass frame back to camera that it get reused
                        ((CameraHolder) cameraUiWrapper.GetCameraHolder()).GetCamera().addCallbackBuffer(tmp);
                    } catch (InterruptedException | NullPointerException e) {
                        Log.WriteEx(e);
                    }
                    finally
                    {
                        //clear frames and pass them back the camera
                        while (frameQueue.size()>0)
                            try {
                                ((CameraHolder) cameraUiWrapper.GetCameraHolder()).GetCamera().addCallbackBuffer(frameQueue.take());
                            } catch (InterruptedException | NullPointerException e)
                            {
                                Log.WriteEx(e);
                            }
                        frameQueue.clear();
                    }
                }
                isWorking = false;
            }
        });
    }

    public boolean isEnable() { return enable;}

    private void reset(int width, int height)
    {
        try {
            mHeight = height;
            mWidth = width;
            expectedByteSize = mHeight * mWidth *
                    ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;

            Log.d(TAG, "reset allocs to :" + width + "x" + height);
            try {
                cameraUiWrapper.GetCameraHolder().ResetPreviewCallback();
            } catch (NullPointerException ex)
            {
                Log.WriteEx(ex);
            }

            Builder tbIn = new Builder(renderScriptHandler.GetRS(), Element.U8(renderScriptHandler.GetRS()));
            tbIn.setX(mWidth);
            tbIn.setY(mHeight);
            tbIn.setYuvFormat(ImageFormat.NV21);
            if (renderScriptHandler.GetOut()!= null)
                renderScriptHandler.GetOut().setSurface(null);


            Builder tbOut = new Builder(renderScriptHandler.GetRS(), Element.RGBA_8888(renderScriptHandler.GetRS()));
            tbOut.setX(mWidth);
            tbOut.setY(mHeight);
            renderScriptHandler.SetAllocsTypeBuilder(tbIn,tbOut,Allocation.USAGE_SCRIPT, Allocation.USAGE_SCRIPT | Allocation.USAGE_IO_OUTPUT);

            if (mSurface != null)
                renderScriptHandler.GetOut().setSurface(mSurface);
            else
                Log.d(TAG, "surfaceNull");
            renderScriptHandler.freedcamScript.set_gCurrentFrame(renderScriptHandler.GetIn());
            Log.d(TAG, "script done enabled: " + enable);
            ((CameraHolder) cameraUiWrapper.GetCameraHolder()).SetPreviewCallback(this);
        }
        catch (RSRuntimeException ex)
        {
            onCameraError("RenderScript Failed");
            clear_preview("reset()");
        }
    }
    @Override
    public void SetAspectRatio(int w, int h)
    {
        Log.d(TAG, "SetAspectRatio enable: " + enable);
        output.setAspectRatio(w, h);
        if (enable)
            reset(w,h);
    }

    @Override
    public void Reset(int width, int height) {

    }

    @Override
    public Surface getInputSurface() {
        return null;
    }

    @Override
    public void setOutputSurface(Surface output) {

    }

    @Override
    public void kill() {

    }


    @Override
    public void onPreviewFrame(byte[] data, Camera camera)
    {
        if (data == null)
            return;
        else if (!enable)
        {
            Log.d(TAG, "onPreviewFrame enabled:" + enable);
            camera.addCallbackBuffer(data);
            cameraUiWrapper.GetCameraHolder().ResetPreviewCallback();
            clear_preview("onPreviewFrame();");
            return;
        }
        else if (!doWork) {
            camera.addCallbackBuffer(data);
            return;
        }
        else if (expectedByteSize != data.length) {
            Log.d(TAG, "frame size does not match rendersize");
            Camera.Size s = camera.getParameters().getPreviewSize();
            reset(s.width, s.height);
            return;
        }
        //if limit is reached pass one frame back to the camera that i can get resused
        if (frameQueue.size() == 2)
        {
            try {
                camera.addCallbackBuffer(frameQueue.take());
            } catch (InterruptedException e) {
                Log.WriteEx(e);
            }
        }
        //store new frame, dont pass it back to the camera else it get cleaned
        frameQueue.add(data);

    }

    @Override
    public void onCameraOpen(String message)
    {

    }

    @Override
    public void onCameraOpenFinish(String message) {

    }

    @Override
    public void onCameraClose(String message) {

    }

    @Override
    public void onPreviewOpen(String message)
    {
        Log.d(TAG, "onPreviewOpen enable:" + enable);
        clear_preview("onPreviewOpen");
        //setEnable(enable);
    }

    @Override
    public void onPreviewClose(String message)
    {
    }

    @Override
    public void onCameraError(String error) {
    }

    @Override
    public void onCameraStatusChanged(String status) {
    }

    @Override
    public void onModuleChanged(String module)
    {
        Log.d(TAG, "onModuleChanged(String):" + module + " enabled:" + enable);
        if (module.equals(cameraUiWrapper.getResString(R.string.module_picture))
                ||module.equals(cameraUiWrapper.getResString(R.string.module_hdr))
                ||module.equals(cameraUiWrapper.getResString(R.string.module_interval)))
        {
            setDoWork(true);
            setEnable(enable);

        }
        else {
            setDoWork(false);
            setEnable(enable);
        }
    }

    private void setDoWork(boolean work) {
        doWork = work;}

}
