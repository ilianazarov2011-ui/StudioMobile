package com.winlator.renderer;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Keep;

import com.winlator.xserver.Drawable;
import com.winlator.xserver.XServer;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

public class GPUImage extends Texture {
    private long hardwareBufferPtr;
    private long imageKHRPtr;
    private ByteBuffer virtualData;
    private short stride;
    private boolean locked = false;
    private int nativeHandle;
    private final ArrayList<WeakReference<Drawable>> users = new ArrayList<>();

    static {
        System.loadLibrary("winlator");
    }

    public GPUImage(Drawable owner) {
        this(owner, true, true);
    }

    public GPUImage(Drawable owner, boolean cpuAccess) {
        this(owner, cpuAccess, true);
    }

    public GPUImage(Drawable owner, boolean cpuAccess, boolean useHALPixelFormatBGRA8888) {
        super(owner);
        hardwareBufferPtr = createHardwareBuffer(owner.width, owner.height, cpuAccess, useHALPixelFormatBGRA8888);
        if (cpuAccess && hardwareBufferPtr != 0) {
            virtualData = lockHardwareBuffer(hardwareBufferPtr);
            locked = true;
        }
    }

    @Override
    public void allocateTexture(short width, short height, ByteBuffer data) {
        if (isAllocated()) return;
        super.allocateTexture(width, height, null);
        imageKHRPtr = createImageKHR(hardwareBufferPtr, textureId);
    }

    @Override
    public void updateFromDrawable() {
        if (!isAllocated() && owner != null) allocateTexture(owner.width, owner.height, null);
        needsUpdate = false;
    }

    public short getStride() {
        return stride;
    }

    @Keep
    private void setStride(short stride) {
        this.stride = stride;
    }

    public int getNativeHandle() {
        return nativeHandle;
    }

    @Keep
    private void setNativeHandle(int nativeHandle) {
        this.nativeHandle = nativeHandle;
    }

    public ByteBuffer getVirtualData() {
        return virtualData;
    }

    /** Registers a drawable whose CPU data aliases this image's mapped memory (see destroy()). */
    public void addUser(Drawable drawable) {
        synchronized (users) {
            for (WeakReference<Drawable> ref : users) if (ref.get() == drawable) return;
            users.add(new WeakReference<>(drawable));
        }
    }

    @Override
    public void destroy() {
        final ByteBuffer mapped = virtualData;
        final long hardwareBuffer = hardwareBufferPtr;
        final boolean wasLocked = locked;

        // Other drawables (redirected windows share this image) may still write into the mapped
        // memory. Detach them first, otherwise the X server thread memcpy()s into freed memory.
        synchronized (users) {
            for (WeakReference<Drawable> ref : users) {
                Drawable user = ref.get();
                if (user == null || mapped == null || user.getData() != mapped) continue;

                Texture current = user.getTexture();
                if (current == this) user.setData(null);
                else if (current instanceof GPUImage) user.setData(((GPUImage)current).getVirtualData());
                else user.setData(ByteBuffer.allocateDirect(user.width * user.height * 4).order(ByteOrder.LITTLE_ENDIAN));
            }
            users.clear();
        }

        destroyImageKHR(imageKHRPtr);
        virtualData = null;
        imageKHRPtr = 0;
        hardwareBufferPtr = 0;

        // Free the CPU mapping a little later, so a native copy already in flight can finish.
        new Handler(Looper.getMainLooper()).postDelayed(() -> destroyHardwareBuffer(hardwareBuffer, wasLocked), 2000);
        super.destroy();
    }

    public long getHardwareBufferPtr() {
        return hardwareBufferPtr;
    }

    public static GPUImage createOrObtain(XServer xServer, Drawable drawable, boolean cpuAccess, boolean useHALPixelFormatBGRA8888) {
        final Texture texture = drawable.getTexture();
        if (!(texture instanceof GPUImage)) {
            xServer.getRenderer().xServerView.queueEvent(texture::destroy);
            GPUImage gpuImage = new GPUImage(drawable, cpuAccess, useHALPixelFormatBGRA8888);
            drawable.setTexture(gpuImage);
            return gpuImage;
        }
        else return (GPUImage)texture;
    }

    private native long createHardwareBuffer(short width, short height, boolean cpuAccess, boolean useHALPixelFormatBGRA8888);

    private native void destroyHardwareBuffer(long hardwareBufferPtr, boolean locked);

    private native ByteBuffer lockHardwareBuffer(long hardwareBufferPtr);

    private native long createImageKHR(long hardwareBufferPtr, int textureId);

    private native void destroyImageKHR(long imageKHRPtr);
}