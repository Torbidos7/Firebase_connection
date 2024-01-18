
package com.example.firebase_connection.tflite;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.Trace;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Vector;


/**
 * Wrapper for frozen detection models trained using the Tensorflow Object Detection API:
 * github.com/tensorflow/models/tree/master/research/object_detection
 */
public class TFLiteObjectDetectionAPIModel implements Detector {

  // Only return this many results.
  // private static final int NUM_DETECTIONS = 10;
  // Float model
  private static final float IMAGE_MEAN = 128.0f;
  private static final float IMAGE_STD = 128.0f;
  // Number of threads in the java app
  private static final int NUM_THREADS = 4;
  private boolean isModelQuantized;
  // Config values.
  private int inputSize;
  // Pre-allocated buffers.
  private final Vector<String> labels = new Vector<>();
  private int[] intValues;
  // outputLocations: array of shape [Batchsize, NUM_DETECTIONS,4]
  // contains the location of detected boxes
  // private float[][][] outputLocations;
  // outputClasses: array of shape [Batchsize, NUM_DETECTIONS]
  // contains the classes of detected boxes
  // private float[][] outputClasses;
  // outputScores: array of shape [Batchsize, NUM_DETECTIONS]
  // contains the scores of detected boxes
  // private float[][] outputScores;
  // numDetections: array of shape [Batchsize]
  // contains the number of detected boxes
  // private float[] numDetections;

  Interpreter.Options tfliteOptions;

  private ByteBuffer imgData;

  private Interpreter tfLite;

  private TFLiteObjectDetectionAPIModel() {
  }

  /**
   * Memory-map the model file in Assets.
   */
  private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
          throws IOException {
    AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
    FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
    FileChannel fileChannel = inputStream.getChannel();
    long startOffset = fileDescriptor.getStartOffset();
    long declaredLength = fileDescriptor.getDeclaredLength();
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
  }

  /**
   * Initializes a native TensorFlow session for classifying images.
   *
   * @param assetManager  The asset manager to be used to load assets.
   * @param modelFilename The filepath of the model GraphDef protocol buffer.
   * @param labelFilename The filepath of label file for classes.
   * @param inputSize     The size of image input
   * @param isQuantized   Boolean representing model is quantized or not
   */
  public static Detector create(
          final AssetManager assetManager,
          final String modelFilename,
          final String labelFilename,
          final int inputSize,
          final boolean isQuantized)
          throws IOException {
    final TFLiteObjectDetectionAPIModel d = new TFLiteObjectDetectionAPIModel();

    d.tfliteOptions = new Interpreter.Options();

    InputStream labelsInput;
    String actualFilename = labelFilename.split("file:///android_asset/")[1];
    labelsInput = assetManager.open(actualFilename);
    BufferedReader br;
    br = new BufferedReader(new InputStreamReader(labelsInput));
    String line;
    while ((line = br.readLine()) != null) {
      //Log.i(String.valueOf(Log.INFO), line);
      d.labels.add(line);
    }
    br.close();

    d.inputSize = inputSize;

    try {
      d.tfliteOptions.setUseNNAPI(true);
      d.tfLite = new Interpreter(loadModelFile(assetManager, modelFilename), d.tfliteOptions);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    d.isModelQuantized = isQuantized;
    // Pre-allocate buffers.
    int numBytesPerChannel;
    if (isQuantized) {
      numBytesPerChannel = 1; // Quantized
    } else {
      numBytesPerChannel = 4; // Floating point
    }
    d.imgData = ByteBuffer.allocateDirect(d.inputSize * d.inputSize * 3 * numBytesPerChannel);
    d.imgData.order(ByteOrder.nativeOrder());
    d.intValues = new int[d.inputSize * d.inputSize];

    d.tfliteOptions.setNumThreads(NUM_THREADS);
    return d;
  }

  @Override
  public float[][][][] recognizeImage(final Bitmap bitmap) {
    // Log this method so that it can be analyzed with systrace.
    Trace.beginSection("recognizeImage");

    Trace.beginSection("preprocessBitmap");
    // Preprocess the image data from 0-255 int to normalized float based
    // on the provided parameters.
    bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

    imgData.rewind();
    for (int i = 0; i < inputSize; ++i) {
      for (int j = 0; j < inputSize; ++j) {
        int pixelValue = intValues[i * inputSize + j];
        if (isModelQuantized) {
          // Quantized model
          imgData.put((byte) ((pixelValue >> 16) & 0xFF));
          imgData.put((byte) ((pixelValue >> 8) & 0xFF));
          imgData.put((byte) (pixelValue & 0xFF));
        } else {
          // Float model
          imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
          imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
          imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
        }
      }
    }
    Trace.endSection(); // preprocessBitmap

    // Copy the input data into TensorFlow.
    Trace.beginSection("feed");

    float[][][][] outputMap = new float[1][inputSize][inputSize][labels.size()];
    Trace.endSection();

    // Run the inference call.
    Trace.beginSection("run");
    tfLite.run(imgData, outputMap);
    Trace.endSection();

    return outputMap;
  }

  @Override
  public void enableStatLogging(final boolean logStats) {
  }

  @Override
  public String getStatString() {
    return "";
  }

  @Override
  public void close() {
  }

  public void setNumThreads(int num_threads) {
    if (tfLite != null) tfliteOptions.setNumThreads(num_threads);
  }

  /*@Override
  public void setUseNNAPI(boolean isChecked) {
    if (tfLite != null) tfliteOptions.setUseNNAPI(isChecked);
  }*/
}
