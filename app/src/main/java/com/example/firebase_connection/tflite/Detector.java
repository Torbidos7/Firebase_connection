package com.example.firebase_connection.tflite;

import android.graphics.Bitmap;

/** Generic interface for interacting with different recognition engines. */
public interface Detector {
  float[][][][] recognizeImage(Bitmap bitmap);

  void enableStatLogging(final boolean debug);

  String getStatString();

  void close();

  void setNumThreads(int num_threads);

 // void setUseNNAPI(boolean isChecked);
}