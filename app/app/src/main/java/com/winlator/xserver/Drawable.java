package com.winlator.xserver;

import android.graphics.Bitmap;

import androidx.annotation.Nullable;

import com.winlator.core.Callback;
import com.winlator.math.Mathf;
import com.winlator.renderer.GPUImage;
import com.winlator.renderer.Texture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Drawable extends XResource {
    public final short width;
    public final short height;
    public final Visual visual;
    private Texture texture = new Texture(this);
    private ByteBuffer data;
    private boolean useSharedData;
    private Runnable onDrawListener;
    private boolean offscreenStorage = false;
    private Callback<Drawable> onDestroyListener;
    public final Object renderLock = new Object();

    static {
        System.loadLibrary("winlator");
    }

    public Drawable(int id, int width, int height, Visual visual) {
        super(id);
        this.width = (short)width;
        this.height = (short)height;
        this.visual = visual;
        this.data = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
    }

    public static Drawable fromBitmap(Bitmap bitmap) {
        Drawable drawable = new Drawable(0, bitmap.getWidth(), bitmap.getHeight(), null);
        fromBitmap(bitmap, drawable.data);
        return drawable;
    }

    public boolean isOffscreenStorage() {
        return offscreenStorage;
    }

    public void setOffscreenStorage(boolean offscreenStorage) {
        this.offscreenStorage = offscreenStorage;
    }

    public Texture getTexture() {
        return texture;
    }

    public void setTexture(Texture texture) {
        if (texture instanceof GPUImage) {
            data = ((GPUImage)texture).getVirtualData();
            ((GPUImage)texture).addUser(this);
        }
        this.texture = texture;
    }

    @Nullable
    public ByteBuffer getData() {
        return data;
    }

    public void setData(ByteBuffer data) {
        this.data = data;
    }

    private short getStride() {
        return texture instanceof GPUImage ? ((GPUImage)texture).getStride() : width;
    }

    public Runnable getOnDrawListener() {
        return onDrawListener;
    }

    public void setOnDrawListener(Runnable onDrawListener) {
        this.onDrawListener = onDrawListener;
    }

    public Callback<Drawable> getOnDestroyListener() {
        return onDestroyListener;
    }

    public void setOnDestroyListener(Callback<Drawable> onDestroyListener) {
        this.onDestroyListener = onDestroyListener;
    }

    /** Makes this drawable's own buffer big enough for the whole write, so nothing is dropped. */
    private void growIfNeeded(int x, int y, int width, int rows, int stride) {
        if (data == null || useSharedData || texture instanceof GPUImage) return;
        if (stride <= 0 || width <= 0 || rows <= 0 || x < 0 || y < 0) return;
        long needed = ((long)(y + rows - 1) * stride + x + width) * 4;
        if (needed <= data.capacity() || needed > (1 << 29)) return;
        ByteBuffer bigger = ByteBuffer.allocateDirect((int)needed).order(ByteOrder.LITTLE_ENDIAN);
        data.rewind();
        bigger.put(data);
        bigger.rewind();
        data = bigger;
    }

    /**
     * How many of the requested rows can be touched safely: the native code does no bounds check, and the
     * real buffer can be smaller than width*height (windows sharing a texture of another size).
     */
    private static int fitRows(ByteBuffer buffer, int x, int y, int width, int rows, int stride) {
        if (buffer == null || stride <= 0 || width <= 0 || rows <= 0 || x < 0 || y < 0) return 0;
        long pixels = buffer.capacity() / 4;
        long lastRow = (pixels - x - width) / stride;
        if (pixels - x - width < 0) return 0;
        long fit = lastRow - y + 1;
        if (fit <= 0) return 0;
        return (int)Math.min(rows, fit);
    }

    public void drawImage(short srcX, short srcY, short dstX, short dstY, short width, short height, byte depth, ByteBuffer data, short totalWidth, short totalHeight) {
        if (this.data == null) return;

        if (depth == 1) {
            drawBitmap(width, height, data, this.data);
        }
        else if (depth == 24 || depth == 32) {
            dstX = (short)Mathf.clamp(dstX, 0, this.width-1);
            dstY = (short)Mathf.clamp(dstY, 0, this.height-1);
            if ((dstX + width) > this.width) width = (short)((this.width - dstX));
            if ((dstY + height) > this.height) height = (short)((this.height - dstY));

            if (this.getStride() > 0 && dstX + width > this.getStride()) width = (short)Math.max(0, this.getStride() - dstX);
            if (totalWidth > 0 && srcX + width > totalWidth) width = (short)Math.max(0, totalWidth - srcX);
            growIfNeeded(dstX, dstY, width, height, this.getStride());
            int rows = Math.min(fitRows(this.data, dstX, dstY, width, height, this.getStride()),
                                fitRows(data, srcX, srcY, width, height, totalWidth));
            if (rows < height) android.util.Log.w("StudioDrawable", "drawImage clamped rows " + height + "->" + rows + " dst=" + dstX + "," + dstY + " w=" + width + " stride=" + this.getStride() + " cap=" + this.data.capacity() + " src=" + data.capacity() + " totalW=" + totalWidth);
            if (rows > 0) copyArea(srcX, srcY, dstX, dstY, width, (short)rows, totalWidth, this.getStride(), data, this.data);
        }

        this.data.rewind();
        data.rewind();

        forceUpdate();
    }

    public ByteBuffer getImage(short x, short y, short width, short height) {
        ByteBuffer dstData = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
        if (this.data == null) return dstData;

        x = (short)Mathf.clamp(x, 0, this.width-1);
        y = (short)Mathf.clamp(y, 0, this.height-1);
        if ((x + width) > this.width) width = (short)(this.width - x);
        if ((y + height) > this.height) height = (short)(this.height - y);

        int rows = fitRows(this.data, x, y, width, height, this.getStride());
        if (rows > 0) copyArea(x, y, (short)0, (short)0, width, (short)rows, this.getStride(), width, this.data, dstData);

        this.data.rewind();
        dstData.rewind();
        return dstData;
    }

    public void copyArea(short srcX, short srcY, short dstX, short dstY, short width, short height, Drawable drawable) {
        copyArea(srcX, srcY, dstX, dstY, width, height, drawable, GraphicsContext.Function.COPY);
    }

    public void copyArea(short srcX, short srcY, short dstX, short dstY, short width, short height, Drawable drawable, GraphicsContext.Function gcFunction) {
        if (this.data == null || drawable.data == null) return;

        dstX = (short)Mathf.clamp(dstX, 0, this.width-1);
        dstY = (short)Mathf.clamp(dstY, 0, this.height-1);
        if ((dstX + width) > this.width) width = (short)(this.width - dstX);
        if ((dstY + height) > this.height) height = (short)(this.height - dstY);

        if (this.getStride() > 0 && dstX + width > this.getStride()) width = (short)Math.max(0, this.getStride() - dstX);
        if (drawable.getStride() > 0 && srcX + width > drawable.getStride()) width = (short)Math.max(0, drawable.getStride() - srcX);
        growIfNeeded(dstX, dstY, width, height, this.getStride());
        int rows = Math.min(fitRows(this.data, dstX, dstY, width, height, this.getStride()),
                            fitRows(drawable.data, srcX, srcY, width, height, drawable.getStride()));
        if (rows < height) android.util.Log.w("StudioDrawable", "copyArea clamped rows " + height + "->" + rows);
        if (rows > 0) {
            if (gcFunction == GraphicsContext.Function.COPY) {
                copyArea(srcX, srcY, dstX, dstY, width, (short)rows, drawable.getStride(), this.getStride(), drawable.data, this.data);
            }
            else copyAreaOp(srcX, srcY, dstX, dstY, width, (short)rows, drawable.getStride(), this.getStride(), drawable.data, this.data, gcFunction.ordinal());
        }

        this.data.rewind();
        drawable.data.rewind();

        forceUpdate();
    }

    public void fillColor(int color) {
        fillRect(0, 0, width, height, color);
    }

    public void fillRect(int x, int y, int width, int height, int color) {
        if (this.data == null) return;
        x = (short)Mathf.clamp(x, 0, this.width-1);
        y = (short)Mathf.clamp(y, 0, this.height-1);
        if ((x + width) > this.width) width = (short)((this.width - x));
        if ((y + height) > this.height) height = (short)((this.height - y));

        growIfNeeded(x, y, width, height, this.getStride());
        int rows = fitRows(this.data, x, y, width, height, this.getStride());
        if (rows > 0) fillRect((short)x, (short)y, (short)width, (short)rows, color, this.getStride(), this.data);
        this.data.rewind();
        forceUpdate();
    }

    public void drawLines(int color, int lineWidth, short... points) {
        for (int i = 2; i < points.length; i += 2) {
            drawLine(points[i-2], points[i-1], points[i+0], points[i+1], color, (short)lineWidth);
        }
    }

    public void drawLine(int x0, int y0, int x1, int y1, int color, int lineWidth) {
        if (this.data == null) return;
        x0 = Mathf.clamp(x0, 0, width-lineWidth);
        y0 = Mathf.clamp(y0, 0, height-lineWidth);
        x1 = Mathf.clamp(x1, 0, width-lineWidth);
        y1 = Mathf.clamp(y1, 0, height-lineWidth);

        drawLine((short)x0, (short)y0, (short)x1, (short)y1, color, (short)lineWidth, this.getStride(), this.data);

        this.data.rewind();
        forceUpdate();
    }

    public void drawAlphaMaskedBitmap(byte foreRed, byte foreGreen, byte foreBlue, byte backRed, byte backGreen, byte backBlue, Drawable srcDrawable, Drawable maskDrawable) {
        if (this.data == null || srcDrawable.data == null || maskDrawable.data == null) return;
        drawAlphaMaskedBitmap(foreRed, foreGreen, foreBlue, backRed, backGreen, backBlue, srcDrawable.data, maskDrawable.data, this.data);
        this.data.rewind();

        forceUpdate();
    }

    public void forceUpdate() {
        if (!offscreenStorage || useSharedData) {
            texture.setNeedsUpdate(true);
            if (onDrawListener != null) onDrawListener.run();
        }
    }

    public boolean isUseSharedData() {
        return useSharedData;
    }

    public void setUseSharedData(boolean useSharedData) {
        this.useSharedData = useSharedData;
    }

    private static native void drawBitmap(short width, short height, ByteBuffer srcData, ByteBuffer dstData);

    private static native void drawAlphaMaskedBitmap(byte foreRed, byte foreGreen, byte foreBlue, byte backRed, byte backGreen, byte backBlue, ByteBuffer srcData, ByteBuffer maskData, ByteBuffer dstData);

    private static native void copyArea(short srcX, short srcY, short dstX, short dstY, short width, short height, short srcStride, short dstStride, ByteBuffer srcData, ByteBuffer dstData);

    private static native void copyAreaOp(short srcX, short srcY, short dstX, short dstY, short width, short height, short srcStride, short dstStride, ByteBuffer srcData, ByteBuffer dstData, int gcFunction);

    private static native void fillRect(short x, short y, short width, short height, int color, short stride, ByteBuffer data);

    private static native void drawLine(short x0, short y0, short x1, short y1, int color, short lineWidth, short stride, ByteBuffer data);

    private static native void fromBitmap(Bitmap bitmap, ByteBuffer data);
}