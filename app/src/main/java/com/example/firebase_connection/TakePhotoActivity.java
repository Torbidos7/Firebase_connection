package com.example.firebase_connection;


import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.firebase_connection.env.ImageUtils;
import com.example.firebase_connection.tflite.Detector;
import com.example.firebase_connection.tflite.TFLiteObjectDetectionAPIModel;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;


public class TakePhotoActivity extends AppCompatActivity {
    private static final int TF_OD_API_INPUT_SIZE = 224;
    private static final boolean TF_OD_API_IS_QUANTIZED = false;
    private static final String TF_OD_API_MODEL_FILE = "mobilenetv2_model.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/deeplab_label_map.txt";

    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(480, 640);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    private Bitmap croppedBitmap, maskBitmap = null;
    private Detector detector;
    Button btnCapture;
    ImageView imgCapture;
    int cropSize = TF_OD_API_INPUT_SIZE;

    FirebaseStorage storage = FirebaseStorage.getInstance();



    private Matrix frameToCropTransform;

    private static final int Image_Capture_Code = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo);


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
        //TODO GET WIDTH AND HEIGHT OF THE CAMERA
        btnCapture = findViewById(R.id.btnTakePicture);
        imgCapture = findViewById(R.id.capturedImage);
        //rgbFrameBitmap = Bitmap.createBitmap(DESIRED_PREVIEW_SIZE.getWidth(), DESIRED_PREVIEW_SIZE.getHeight(), Bitmap.Config.ARGB_8888);
        //ImageUtils.convertYUV420SPToARGB8888(bytes, DESIRED_PREVIEW_SIZE.getWidth(), DESIRED_PREVIEW_SIZE.getWidth(), rgbBytes);

        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);
        maskBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);

        frameToCropTransform = ImageUtils.getTransformationMatrix(
                DESIRED_PREVIEW_SIZE.getWidth(), DESIRED_PREVIEW_SIZE.getHeight(),
                cropSize, cropSize,
                0,
                MAINTAIN_ASPECT);

        Matrix cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        btnCapture.setOnClickListener(view -> {
            Intent cInt = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(cInt, Image_Capture_Code);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Image_Capture_Code) {
            if (resultCode == RESULT_OK) {
                Bitmap bp = (Bitmap) data.getExtras().get("data");

                //set pixels of rgbframebitmap to 0



                Canvas canvas = performSegmentation(bp);
                //imgResult.setImageBitmap(croppedBitmap);
                //result.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
                Bitmap resizedMaskBitmap = Bitmap.createScaledBitmap(maskBitmap, bp.getWidth(), bp.getHeight(), true);
                imgCapture.setImageBitmap(resizedMaskBitmap);



                // create reference to the woundshot-storage database (IMAGE)
                StorageReference storageRefProfile = storage.getReference("mask/mask");

                // Upload the picture Bitmap of the wound shot
                // convert the bitmap to a byte array of string
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                // compress the woundshot picture bitmap using PNG fmt
                resizedMaskBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                // create the buffer of the image data (aka array)
                byte[] immagine = baos.toByteArray();

                UploadTask uploadTask = storageRefProfile.putBytes(immagine);
                uploadTask.addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        // print failure
                        Log.i(String.valueOf(Log.INFO), "Upload failed");
                    }
                }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        // print success and file reference
                        Log.i(String.valueOf(Log.INFO), "Upload success");
                        // ...
                    }
                });


                //print resizedMaskBitmap dimensions
                Log.i(String.valueOf(Log.INFO), String.format("resizedMaskBitmap dimensions: %d x %d", resizedMaskBitmap.getWidth(), resizedMaskBitmap.getHeight()));



            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show();
            }
        }
    }

    private Canvas performSegmentation(Bitmap inputImage) {
        // Preprocess the input image
        Bitmap resizedImage = Bitmap.createScaledBitmap(inputImage, TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, true);

        // Run inference
        final float[][][][] results = detector.recognizeImage(resizedImage);
        Canvas canvas = new Canvas(croppedBitmap);
        Canvas canvas2 = new Canvas(maskBitmap);
        //draw_green(canvas, TF_OD_API_INPUT_SIZE, results);
        draw_pic(canvas, TF_OD_API_INPUT_SIZE, results, resizedImage);
        draw_mask(canvas2, TF_OD_API_INPUT_SIZE, results);



        //To do resolve bitmap and canvas
        //print the result array
        for (int i = 0; i < 224; i++) {
            for (int j = 0; j < 224; j++) {
                if (results[0][i][j][0] > 0.95){
                Log.i(String.valueOf(Log.INFO), String.format("------------POS VALUE= %f", results[0][i][j][0]));}
            }
        }
        // Create a Bitmap from the segmentation results
        //Bitmap segmentedBitmap = Bitmap.createBitmap(canvas);

        return canvas;
    }

    private void draw_mask(final Canvas canvas, final int input_size, final float[][][][] results) {

        final Paint boxPaint = new Paint();
        int sensorOrientation = 0;
        final float input_size_float = input_size;
        final boolean rotated = sensorOrientation % 180 == 90;
        final float multiplier =
                Math.min(
                        canvas.getHeight() / (float) (rotated ? 640 : 480),
                        canvas.getWidth() / (float) (rotated ? 480 : 640));
        int w = (int) (multiplier * (rotated ? 480 : 640));
        int h = (int) (multiplier * (rotated ? 640 : 480));
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
                        boxPaint.setColor(Color.WHITE);
                        r.left = (x / input_size_float * w) - xw;
                        r.top = (y / input_size_float * h) - xh;
                        r.right = (x / input_size_float * w) + xw;
                        r.bottom = (y / input_size_float * h) + xh;
                        canvas.drawRect(r, boxPaint);
                    } else { //changed from pos>0
                        //used.add(pos);
                        boxPaint.setColor(Color.BLACK);
                        r.left = (x / input_size_float * w) - xw;
                        r.top = (y / input_size_float * h) - xh;
                        r.right = (x / input_size_float * w) + xw;
                        r.bottom = (y / input_size_float * h) + xh;
                        canvas.drawRect(r, boxPaint);
                    }
                }
            }
        }

    }




    private void draw_pic(final Canvas canvas, final int input_size, final float[][][][] results, Bitmap bp) {

        final Paint boxPaint = new Paint();
        int sensorOrientation = 0;
        final float input_size_float = input_size;
        final boolean rotated = sensorOrientation % 180 == 90;
        final float multiplier =
                Math.min(
                        canvas.getHeight() / (float) (rotated ? 640 : 480),
                        canvas.getWidth() / (float) (rotated ? 480 : 640));
        int w = (int) (multiplier * (rotated ? 480 : 640));
        int h = (int) (multiplier * (rotated ? 640 : 480));
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
                        boxPaint.setColor(bp.getPixel(x,y));
                        r.left = (x / input_size_float * w) - xw;
                        r.top = (y / input_size_float * h) - xh;
                        r.right = (x / input_size_float * w) + xw;
                        r.bottom = (y / input_size_float * h) + xh;
                        canvas.drawRect(r, boxPaint);
                    } else{
                        boxPaint.setColor(adjustAlpha(bp.getPixel(x,y), 0.1f));
                        r.left = (x / input_size_float * w) - xw;
                        r.top = (y / input_size_float * h) - xh;
                        r.right = (x / input_size_float * w) + xw;
                        r.bottom = (y / input_size_float * h) + xh;
                        canvas.drawRect(r, boxPaint);
                    }
                }
            }
        }

    }
    @ColorInt
    public static int adjustAlpha(@ColorInt int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }



}


