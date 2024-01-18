
package com.example.firebase_connection.tracking;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.util.TypedValue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/** A tracker that handles non-max suppression and matches existing objects to new detections. */
public class MultiBoxTracker {
  private static final float TEXT_SIZE_DIP = 18;
  private static final Integer[] COLORS = makeColorGradient(.2f, .2f, .2f, 0, 2, 4, 21);
  private final Paint boxPaint = new Paint();
  private int frameWidth;
  private int frameHeight;
  private int sensorOrientation;
  private float[][][][] results;
  private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/deeplab_label_map.txt";

  public MultiBoxTracker(final Context context) {
    Queue<Integer> availableColors = new LinkedList<>(Arrays.asList(COLORS));

    boxPaint.setColor(Color.RED);
    boxPaint.setStyle(Style.FILL);
    boxPaint.setStrokeWidth(10.0f);
    boxPaint.setStrokeCap(Cap.ROUND);
    boxPaint.setStrokeJoin(Join.ROUND);
    boxPaint.setStrokeMiter(100);
    boxPaint.setAntiAlias(true);
    boxPaint.setTextSize(32 * context.getResources().getDisplayMetrics().density);

    float textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
  }

  public synchronized void setFrameConfiguration(
          final int width, final int height, final int sensorOrientation) {
    frameWidth = width;
    frameHeight = height;
    this.sensorOrientation = sensorOrientation;
  }

  public synchronized void trackResults(final float[][][][] results, final long timestamp) {
    this.results = results;
  }
  //private BufferedReader reader =  Files.newBufferedReader(Paths.get(TF_OD_API_LABELS_FILE))
  /*public static void readfile(String[] args) throws FileNotFoundException {

    File getCSVFiles = new File(TF_OD_API_LABELS_FILE);
    Scanner sc = new Scanner(getCSVFiles);
    sc.useDelimiter(",");
    while (sc.hasNext())                  creare lista di string, e poi convertirla in array
    {
      System.out.print(sc.next() + " | ");
    }
    sc.close();
  }*/
  private static final String[] labels = //reader.split(COMMA_DELIMITER)

          new String[]{
          //"background",
          "wound"};
  public synchronized void draw(final Canvas canvas, final int input_size) {
    final float input_size_float = input_size;
    final boolean rotated = sensorOrientation % 180 == 90;
    final float multiplier =
            Math.min(
                    canvas.getHeight() / (float) (rotated ? frameWidth : frameHeight),
                    canvas.getWidth() / (float) (rotated ? frameHeight : frameWidth));
    int w = (int) (multiplier * (rotated ? frameHeight : frameWidth));
    int h = (int) (multiplier * (rotated ? frameWidth : frameHeight));
    if (results != null) {
      //int pos;
      float xw = w / input_size_float;
      float xh = h / input_size_float;
      RectF r = new RectF();
     // HashSet<Integer> used = new HashSet<>();
      for (int y = 0; y < input_size; y++) { //input size must be int
        for (int x = 0; x < input_size; x++) { //input size must int
          //Log.i(String.valueOf(Log.INFO), String.format("------------POS VALUE= %f", results[0][y][x][0]));
          if (results[0][y][x][0] > 0.95f) { //changed from pos>0
            //used.add(pos);
            boxPaint.setColor(Color.BLUE);
            r.left = (x / input_size_float * w) - xw;
            r.top = (y / input_size_float * h) - xh;
            r.right = (x / input_size_float * w) + xw;
            r.bottom = (y / input_size_float * h) + xh;
            canvas.drawRect(r, boxPaint);
          }
        }
      }
      /*float x = 5;
      float large = Math.max(canvas.getWidth(), canvas.getHeight());
      float small = Math.min(canvas.getWidth(), canvas.getHeight());
      float skip = boxPaint.getTextSize();
      float y = (large - small) / 4 + skip/2f;
      TextPaint textPaint = new TextPaint();
      textPaint.setAntiAlias(true);
      textPaint.setTextSize(boxPaint.getTextSize());
      for (int i : used) {
        boxPaint.setColor(COLORS[i]);
        textPaint.setColor(COLORS[i]);
        canvas.save();
        // canvas.drawText(labels[i],x,y,boxPaint);
        int width = (int) boxPaint.measureText(labels[i]);
        canvas.translate(x, y);
        StaticLayout staticLayout = new StaticLayout(labels[i], textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0, false);
        staticLayout.draw(canvas);
        y += skip;
        canvas.restore();
      }*/

    }
  }

  public int getIndexOfMax(float[] array) {
    if (array.length == 0) {
      return -1; // array contains no elements
    }
    float max = array[0];
    int pos = 0;

    for (int i = 1; i < array.length; i++) {
      if (max < array[i]) {
        pos = i;
        max = array[i];
      }
    }
    return pos;
  }

  public static Integer[] makeColorGradient(float frequency1, float frequency2, float frequency3, float phase1, float phase2, float phase3, int len) {
    Integer[] c = new Integer[len];
    int center = 128;
    int width = 127;
    for (int i = 0; i < len; ++i) {
      int red = (int) (Math.sin(frequency1 * i + phase1) * width + center);
      int grn = (int) (Math.sin(frequency2 * i + phase2) * width + center);
      int blu = (int) (Math.sin(frequency3 * i + phase3) * width + center);
      c[i] = Color.rgb(red, grn, blu);
    }
    List<Integer> list = Arrays.asList(c);
    Collections.shuffle(list);

    return list.toArray(new Integer[]{});
  }
}
