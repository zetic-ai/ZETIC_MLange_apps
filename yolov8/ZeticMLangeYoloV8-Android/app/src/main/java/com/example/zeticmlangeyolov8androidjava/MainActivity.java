package com.example.zeticmlangeyolov8androidjava;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.zetic.ZeticMLange.ZeticMLangeDataUtils;
import com.zetic.ZeticMLange.ZeticMLangeException;
import com.zetic.ZeticMLange.ZeticMLangeModel;
import com.zetic.ZeticMLangeFeature.ZeticMLangeFeatureYolov8;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {

    private final static String TAG = "ZETIC.MLange Android(Java) sample app - YoloV8";
    private ImageView imageView;
    private TextureView textureView;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    // ZETIC.MLange related variables
    ZeticMLangeModel zeticMLangeYoloVModel;
    ZeticMLangeFeatureYolov8 zeticMLangeFeatureYolov8;

    // YAML file to initialize yolo variables
    final String cocoYamlSamplePath = "coco.yaml";

    // ZETIC.MLange function (1) - Initialization
    private void initZeticMLangeYolov8() {
        try {
            if (zeticMLangeYoloVModel == null) {
                zeticMLangeYoloVModel = new ZeticMLangeModel(this, "yolo-v8n-test");
            }

            File cocoYamlFile = new File(getFilesDir(), cocoYamlSamplePath);
            String cocoYamlPath = cocoYamlFile.getAbsolutePath();
            if (zeticMLangeFeatureYolov8 == null) {
                zeticMLangeFeatureYolov8 = new ZeticMLangeFeatureYolov8(cocoYamlPath);
            }

        } catch (ZeticMLangeException e) {
            throw new RuntimeException(e);
        }
    }

    // ZETIC.MLange function (2) - Run
    private Bitmap runZeticMLangeYolov8(Bitmap bitmap) {
        float[] floatInput = zeticMLangeFeatureYolov8.preprocess(bitmap);
        float[][] floatInputs = {floatInput};
        ByteBuffer[] inputs = ZeticMLangeDataUtils.convertFloatArrayToByteBufferArray(floatInputs);
        try {
            zeticMLangeYoloVModel.run(inputs);
            ByteBuffer[] outputs = zeticMLangeYoloVModel.getOutputBuffers();
            float[] outputFloatArray = ZeticMLangeDataUtils.convertByteBufferToFloatArray(outputs[0]);
            return zeticMLangeFeatureYolov8.postprocess(outputFloatArray);
        } catch (ZeticMLangeException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        _prepareCocoYamlFromAsset();

        // [ZETIC.MLange] - (1) Initialize model and feature extractor
        initZeticMLangeYolov8();

        // Yolo test
        imageView = findViewById(R.id.imageView);
        textureView = findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(this);
    }

    private void _prepareCocoYamlFromAsset() {
        try {
            copyFileFromAssetsToData(this, cocoYamlSamplePath);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        openCamera();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[0];

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    private void createCameraPreviewSession() {
        try {
            final float SCALE_VAL_TO_HIDE_TEXTURE_VIEW = 0.2f;
            final int OFFVAL_TO_HIDE_TEXTURE_VIEW = 0;

            SurfaceTexture texture = textureView.getSurfaceTexture();

            int width = textureView.getWidth();
            int height = textureView.getHeight();

            Matrix txform = new Matrix();
            textureView.getTransform(txform);
            txform.setScale((float) SCALE_VAL_TO_HIDE_TEXTURE_VIEW, (float) SCALE_VAL_TO_HIDE_TEXTURE_VIEW);

            txform.postTranslate(OFFVAL_TO_HIDE_TEXTURE_VIEW, OFFVAL_TO_HIDE_TEXTURE_VIEW);
            textureView.setTransform(txform);

            Surface surface = new Surface(texture);

            imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);

            final CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            captureRequestBuilder.addTarget(imageReader.getSurface());

            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (cameraDevice == null) {
                                return;
                            }
                            cameraCaptureSession = session;
                            try {
                                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if (image == null) {
                return;
            }
            Bitmap bitmap = rotateBitmap(imageToBitmap(image), 90);

            image.close();

            // [ZETIC.MLange] - (1) Process the bitmap image
            Bitmap proc_image = runZeticMLangeYolov8(bitmap);

            runOnUiThread(new Thread(new Runnable() {
                @Override
                public void run() {
                    imageView.setImageBitmap(proc_image);
                }
            }));
        }
    };

    public Bitmap imageToBitmap(Image image) {
        // Get the buffer from the image
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        // Decode the byte array to a Bitmap
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    public Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();
        super.onPause();
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void copyFileFromAssetsToData(Context context, String fileName) throws IOException {
        AssetManager assetManager = context.getAssets();
        InputStream in = assetManager.open(fileName);
        File outFile = new File(context.getFilesDir(), fileName);
        FileOutputStream out = new FileOutputStream(outFile);

        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }

        in.close();
        out.flush();
        out.close();
    }
}