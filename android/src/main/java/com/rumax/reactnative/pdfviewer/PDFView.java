package com.rumax.reactnative.pdfviewer;

/*
 * Created by Maksym Rusynyk on 06/03/2018.
 *
 * This source code is licensed under the MIT license
 */

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.support.v7.widget.AppCompatImageView;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.animation.AlphaAnimation;
import android.view.animation.DecelerateInterpolator;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PDFView extends AppCompatImageView {
    public class Configurator {
        public Configurator defaultPage(int page) {
            return this;
        }
        public Configurator swipeHorizontal(boolean isHorizontal) {
            return this;
        }
        public Configurator onLoad(PDFView pdfView) {
            return this;
        }
        public Configurator onError(PDFView pdfView) {
            return this;
        }
        public Configurator onPageChange(PDFView pdfView) {
            return this;
        }
        public Configurator onPageScroll(PDFView pdfView) {
            return this;
        }
        public Configurator spacing(int space) {
            return this;
        }
        public Configurator enableAnnotationRendering(boolean enable) {
            return this;
        }
        public Configurator load() {
            return this;
        }
    }
    public Configurator fromStream(InputStream input) {
        return new Configurator();
    }
    public Configurator fromBytes(byte[] bytes) {
        return new Configurator();
    }
    public final static String EVENT_ON_LOAD = "onLoad";
    public final static String EVENT_ON_ERROR = "onError";
    public final static String EVENT_ON_PAGE_CHANGED = "onPageChanged";
    public final static String EVENT_ON_SCROLLED = "onScrolled";
    private ThemedReactContext context;
    private Bitmap bitmap;
    private Canvas canvas;
    private Rect rect;
    private String resource;
    private File downloadedFile;
    private AsyncDownload downloadTask = null;
    private String resourceType = null;
    private Configurator configurator = null;
    private boolean sourceChanged = true;
    private ReadableMap urlProps;
    private int fadeInDuration = 0;
    private boolean enableAnnotations = false;
    private float lastPositionOffset = 0;
    /**
     * The default buffer size
     */
    public static final int DEFAULT_BUFFER_SIZE = 8192;

    public PDFView(ThemedReactContext context) {
        super(context, null);
        this.context = context;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        this.setClipToOutline(true);
    }

    //    @Override
    public void loadComplete(int numberOfPages) {
        AlphaAnimation fadeIn = new AlphaAnimation(0, 1);
        fadeIn.setInterpolator(new DecelerateInterpolator());
        fadeIn.setDuration(fadeInDuration);
        this.setAlpha(1);
        this.startAnimation(fadeIn);

        reactNativeMessageEvent(EVENT_ON_LOAD, null);
    }

    //    @Override
    public void onError(Throwable t) {
        reactNativeMessageEvent(EVENT_ON_ERROR, "error: " + t.getMessage());
    }

    //    @Override
    public void onPageChanged(int page, int pageCount) {
        WritableMap event = Arguments.createMap();
        event.putInt("page", page);
        event.putInt("pageCount", pageCount);
        reactNativeEvent(EVENT_ON_PAGE_CHANGED, event);
    }

    //    @Override
    public void onPageScrolled(int page, float positionOffset) {
        if (lastPositionOffset != positionOffset && (positionOffset == 0 || positionOffset == 1)) {
            // Only 0 and 1 are currently supported
            lastPositionOffset = positionOffset;
            WritableMap event = Arguments.createMap();
            event.putDouble("offset", positionOffset);
            reactNativeEvent(EVENT_ON_SCROLLED, event);
        }
    }

    private void reactNativeMessageEvent(String eventName, String message) {
        WritableMap event = Arguments.createMap();
        event.putString("message", message);
        reactNativeEvent(eventName, event);
    }

    private void reactNativeEvent(String eventName, WritableMap event) {
        ReactContext reactContext = (ReactContext) this.getContext();
        reactContext
                .getJSModule(RCTEventEmitter.class)
                .receiveEvent(this.getId(), eventName, event);
    }

    private void setupAndLoad() {
        lastPositionOffset = 0;
        this.setAlpha(0);
        configurator
                .defaultPage(0)
                .swipeHorizontal(false)
                .onLoad(this)
                .onError(this)
                .onPageChange(this)
                .onPageScroll(this)
                .spacing(10)
                .enableAnnotationRendering(enableAnnotations)
                .load();
        sourceChanged = false;
    }

    private void copyInputStreamToFile(InputStream inputStream, File file) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            int read;
            byte[] bytes = new byte[DEFAULT_BUFFER_SIZE];
            while ((read = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, read);
            }
        }
    }

    private void renderFromFile(String filePath) {
        File file;
        try {
            if (filePath.startsWith("/")) { // absolute path, using FS
                file = new File(filePath);
            } else { // from assets
                AssetManager assetManager = context.getAssets();
                InputStream input = assetManager.open(filePath, AssetManager.ACCESS_BUFFER);
                file = File.createTempFile("pdf_", ".pdf", context.getCacheDir());
                this.copyInputStreamToFile(input, file);
            }
//            configurator = this.fromStream(input);
//            setupAndLoad();
            System.out.println(file);
            ParcelFileDescriptor pdfFile = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer renderer = new PdfRenderer(pdfFile);

            DisplayMetrics metrics = new DisplayMetrics();
            Objects.requireNonNull(this.context.getCurrentActivity()).getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int screenWidth = metrics.widthPixels;

            final int pageCount = renderer.getPageCount();
            int totalHeight = 0;
            for (int i = 0; i < pageCount; i++) {
                PdfRenderer.Page page = renderer.openPage(i);
                int pageWidth = page.getWidth();
                int pageHeight = page.getHeight();
                int transformedHeight = (int)((pageHeight / (double)pageWidth) * screenWidth);
                totalHeight += transformedHeight;
                page.close();
            }
            this.bitmap = Bitmap.createBitmap(screenWidth, totalHeight, Bitmap.Config.ARGB_8888);
            this.canvas = new Canvas(bitmap);


            List<Bitmap> bitmaps = new ArrayList<>();
            for (int i = 0; i < pageCount; i++) {
                PdfRenderer.Page page = renderer.openPage(i);
                int pageWidth = page.getWidth();
                int pageHeight = page.getHeight();
                int transformedHeight = (int)((pageHeight / (double)pageWidth) * screenWidth);
                Bitmap pageBitmap = Bitmap.createBitmap(screenWidth, transformedHeight, Bitmap.Config.ARGB_8888);
                page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                bitmaps.add(pageBitmap);
                page.close();

            }

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setFilterBitmap(true);
            paint.setDither(true);
            int heightCount = 0;
            for (Bitmap bitmap : bitmaps) {
                int bottom = bitmap.getHeight() + heightCount;
                Rect srcRect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
                Rect destRect = new Rect(0, heightCount, bitmap.getWidth(), bottom);
                this.canvas.drawBitmap(bitmap, srcRect, destRect, paint);
                this.canvas.drawLine(0, bottom + 1, bitmap.getWidth(), bottom + 1, paint);
                heightCount += bitmap.getHeight() + 1;
            }

            this.setImageBitmap(this.bitmap);

            // close the renderer
            renderer.close();
            pdfFile.close();
            if (!filePath.startsWith("/")) {
                file.delete();
            }
        } catch (IOException e) {
            onError(e);
        }
    }

    private void renderFromBase64() {
        try {
            byte[] bytes = Base64.decode(resource, 0);
            configurator = this.fromBytes(bytes);
            setupAndLoad();
        } catch (IllegalArgumentException e) {
            onError(new IOException(Errors.E_INVALID_BASE64.getCode()));
        }
    }

    private void renderFromUrl() {
        File dir = context.getCacheDir();
        try {
            downloadedFile = File.createTempFile("pdfDocument", "pdf", dir);
        } catch (IOException e) {
            onError(e);
            return;
        }

        downloadTask = new AsyncDownload(context, resource, downloadedFile, urlProps, new AsyncDownload.TaskCompleted() {
            @Override
            public void onComplete(Exception ex) {
                if (ex == null) {
                    renderFromFile(downloadedFile.getAbsolutePath());
                } else {
                    cleanDownloadedFile();
                    onError(ex);
                }
            }
        });
        downloadTask.execute();
    }

    public void render() {
        cleanup();

        if (resource == null) {
            onError(new IOException(Errors.E_NO_RESOURCE.getCode()));
            return;
        }

        if (resourceType == null) {
            onError(new IOException(Errors.E_NO_RESOURCE_TYPE.getCode()));
            return;
        }

        if (!sourceChanged) {
            return;
        }

        System.out.println("resourceType: " + resourceType + ", --> " + resource);

        switch (resourceType) {
            case "url":
                renderFromUrl();
                break;
            case "base64":
                renderFromBase64();
                break;
            case "file":
                renderFromFile(resource);
                break;
            default:
                onError(new IOException(Errors.E_INVALID_RESOURCE_TYPE.getCode() + resourceType));
                break;
        }
    }

    private void cleanup() {
        if (downloadTask != null) {
            downloadTask.cancel(true);
        }
        cleanDownloadedFile();
    }

    private void cleanDownloadedFile() {
        if (downloadedFile != null) {
            if (!downloadedFile.delete()) {
                onError(new IOException(Errors.E_DELETE_FILE.getCode()));
            }
            downloadedFile = null;
        }
    }

    private static boolean isDifferent(String str1, String str2) {
        if (str1 == null || str2 == null) {
            return true;
        }

        return !str1.equals(str2);
    }

    public void setResource(String resource) {
        if (isDifferent(resource, this.resource)) {
            sourceChanged = true;
        }
        this.resource = resource;
    }

    public void setResourceType(String resourceType) {
        if (isDifferent(resourceType, this.resourceType)) {
            sourceChanged = true;
        }
        this.resourceType = resourceType;
    }

    public void onDrop() {
        cleanup();
        sourceChanged = true;
    }

    public void setUrlProps(ReadableMap props) {
        this.urlProps = props;
    }

    public void setFadeInDuration(int duration) {
        this.fadeInDuration = duration;
    }

    public void setEnableAnnotations(boolean enableAnnotations) {
        this.enableAnnotations = enableAnnotations;
    }

    public void reload() {
        sourceChanged = true;
        render();
    }
}
