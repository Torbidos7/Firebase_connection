
package com.example.firebase_connection;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.widget.Toast;

import com.example.firebase_connection.customview.OverlayView;
import com.example.firebase_connection.env.BorderedText;
import com.example.firebase_connection.env.ImageUtils;
import com.example.firebase_connection.tflite.Detector;
import com.example.firebase_connection.tflite.TFLiteObjectDetectionAPIModel;
import com.example.firebase_connection.tracking.MultiBoxTracker;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * The activity to perform the image segmentation of Wound
 */
public class SegmentationActivity extends CameraActivity implements OnImageAvailableListener {

  // Configuration values for the prepackaged SSD model.
  private static final int TF_OD_API_INPUT_SIZE = 224;
  private static final boolean TF_OD_API_IS_QUANTIZED = false;
  private static final String TF_OD_API_MODEL_FILE = "mobilentv2_tversky.tflite";
  private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/deeplab_label_map.txt";

  private static final boolean MAINTAIN_ASPECT = false;
  private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
  private static final boolean SAVE_PREVIEW_BITMAP = false;
  private static final float TEXT_SIZE_DIP = 10;
  OverlayView trackingOverlay;

  private Detector detector;

  private long lastProcessingTimeMs;
  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;
  private Bitmap cropCopyBitmap = null;

  private boolean computingDetection = false;

  private long timestamp = 0;

  private Matrix frameToCropTransform;

  private MultiBoxTracker tracker;


  @Override
  public void onPreviewSizeChosen(final Size size, final int rotation) {
    final float textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
    BorderedText borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);

    tracker = new MultiBoxTracker(this);

    int cropSize = TF_OD_API_INPUT_SIZE;

    try {
      detector = TFLiteObjectDetectionAPIModel.create(getAssets(),
                                                      TF_OD_API_MODEL_FILE,
                                                      TF_OD_API_LABELS_FILE,
                                                      TF_OD_API_INPUT_SIZE,
                                                      TF_OD_API_IS_QUANTIZED);

    } catch (final IOException e) {
      e.printStackTrace();
      Log.e(String.valueOf(Log.ERROR), "Exception initializing Detector!");
      Toast toast = Toast.makeText(getApplicationContext(), "Detector could not be initialized", Toast.LENGTH_SHORT);
      toast.show();
      finish();
    }

    previewWidth = size.getWidth();
    previewHeight = size.getHeight();

    int sensorOrientation = rotation - getScreenOrientation();
    Log.i(String.valueOf(Log.INFO), String.format("Camera orientation relative to screen canvas: %d", sensorOrientation));

    Log.i(String.valueOf(Log.INFO), String.format("Initializing at size %dx%d", previewWidth, previewHeight));
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
    croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

    frameToCropTransform = ImageUtils.getTransformationMatrix(
            previewWidth, previewHeight,
            cropSize, cropSize,
            sensorOrientation,
            MAINTAIN_ASPECT);

    Matrix cropToFrameTransform = new Matrix();
    frameToCropTransform.invert(cropToFrameTransform);

    trackingOverlay = findViewById(R.id.tracking_overlay);
    trackingOverlay.addCallback(canvas -> tracker.draw(canvas, TF_OD_API_INPUT_SIZE));
    trackingOverlay.setAlpha(.75f);
    tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
  }

  @Override
  protected void processImage() {
    ++timestamp;
    final long currTimestamp = timestamp;
    trackingOverlay.postInvalidate();

    // No mutex needed as this method is not reentrant.
    if (computingDetection) {
      readyForNextImage();
      return;
    }
    computingDetection = true;
    Log.d(String.valueOf(Log.DEBUG), "Preparing image " + currTimestamp + " for segmentation in bg thread.");

    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

    readyForNextImage();

    final Canvas canvas = new Canvas(croppedBitmap);
    canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
    // For examining the actual TF input.
    if (SAVE_PREVIEW_BITMAP) {

      Date currentTime = Calendar.getInstance().getTime();
      SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.ITALY);

      String filename = formatter.format(currentTime) + ".png";

      // save image to gallery
      MediaStore.Images.Media.insertImage(getContentResolver(), croppedBitmap, filename, "");

      //ImageUtils.saveBitmap(croppedBitmap);
    }

    runInBackground(
            () -> {
              Log.d(String.valueOf(Log.DEBUG), "Running segmentation on image " + currTimestamp);
              final long startTime = SystemClock.uptimeMillis();
              final float[][][][] results = detector.recognizeImage(croppedBitmap);
              lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

              cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
              //final Canvas canvas1 = new Canvas(cropCopyBitmap);
              final Paint paint = new Paint();
              paint.setColor(Color.RED);
              paint.setStyle(Style.STROKE);
              paint.setStrokeWidth(2.0f);
              tracker.trackResults(results, currTimestamp);
              trackingOverlay.postInvalidate();
              computingDetection = false;

              runOnUiThread(
                      () -> {
                        showFrameInfo(previewWidth + "x" + previewHeight);
                        showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                        showInference(lastProcessingTimeMs + "ms");
                      });
            });
  }

  @Override
  protected int getLayoutId() {
    return R.layout.tfe_od_camera_connection_fragment_tracking;
  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  /*@Override
  protected void setUseNNAPI(final boolean isChecked) {
    runInBackground(
        () -> {
          try {
            detector.setUseNNAPI(isChecked);
          } catch (UnsupportedOperationException e) {
            Log.e(String.valueOf(Log.ERROR), "Failed to set \"Use NNAPI\".");
            runOnUiThread(
                    () -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
          }
        });
  }*/

  @Override
  protected void setNumThreads(final int numThreads) {
    runInBackground(() -> detector.setNumThreads(numThreads));
  }

  // Override of GLOBAL back-button press
  @Override
  public void onBackPressed() {
    finish();
    overridePendingTransition(R.animator.slide_in_left, R.animator.slide_out_right);
  }
}
