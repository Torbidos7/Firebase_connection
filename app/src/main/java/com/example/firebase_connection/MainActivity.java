
package com.example.firebase_connection;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.pm.PackageManager;

import android.os.Bundle;
import android.widget.Button;

import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;

import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract;
import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult;
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Arrays;
import java.util.List;


public class MainActivity extends AppCompatActivity {


    private static int AUTH_REQUEST_CODE = 1234; // any number


    private FirebaseAuth firebaseAuth;
    private FirebaseAuth.AuthStateListener listener;
    private List<AuthUI.IdpConfig> providers;
    Button btnLogOut, btnCamera, btnData, btnLogin, btnSigin;



    @Override
    protected void onStart() {
        super.onStart();
        firebaseAuth = FirebaseAuth.getInstance();
        firebaseAuth.addAuthStateListener(listener);

    }

    @Override
    protected void onStop() {
        if (listener != null) firebaseAuth.removeAuthStateListener(listener);
        super.onStop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();
        setContentView(R.layout.activity_main);

        btnLogOut = findViewById(R.id.btnLogout);
        btnCamera = findViewById(R.id.camerabutton);
        btnData = findViewById(R.id.databutton);
        btnLogin = findViewById(R.id.btnLogin);
        btnSigin = findViewById(R.id.btnSignin);



        btnLogin.setOnClickListener(view -> {

        });

        btnLogOut.setOnClickListener(view -> {
            AuthUI.getInstance()
                    .signOut(this)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            Toast.makeText(getApplicationContext(), "Signed Out.",Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(MainActivity.this, SignUpActivity.class));

                        }

                    });
        });

        btnSigin.setOnClickListener(view -> {
            startActivity(new Intent(MainActivity.this, SignUpActivity.class));
        });
        btnCamera.setOnClickListener(view -> {
            // Check the Camera availability since the AI will require it
            if (checkCameraHardware()){
                // If it is provided -> LiveMode
                Toast.makeText(getApplicationContext(), "Live Mode",Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(this, SegmentationActivity.class);
                startActivity(intent);
                // NOTE: This button is on the right of the screen
                overridePendingTransition(R.animator.slide_in_right, R.animator.slide_out_left);
            } else {
                // Sorry... not camera available
                Toast.makeText(getApplicationContext(), "Camera Not Found",Toast.LENGTH_SHORT).show();
            }
        });


        btnData.setOnClickListener(view -> {
            Intent intent = new Intent(this, TakePhotoActivity.class);
            startActivity(intent);
        });

    }

    private void init() {
        providers = Arrays.asList(
                new AuthUI.IdpConfig.AppleBuilder().build(), //apple provider
                new AuthUI.IdpConfig.GoogleBuilder().build(), //google provider
                new AuthUI.IdpConfig.EmailBuilder().build(),//email provider

                //new AuthUI.IdpConfig.FacebookBuilder().build(), //facebook provider
                new AuthUI.IdpConfig.PhoneBuilder().build() //phone provider
        );
        ;
        listener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                // Get user
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    Toast.makeText(MainActivity.this, "You are already logged with id: " + user.getUid(), Toast.LENGTH_SHORT).show();
                } else {
                    //Login
                    startActivityForResult(AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setAvailableProviders(providers)
                            .build(), AUTH_REQUEST_CODE);
                }
            }
        };
    }

    /** Check if this device has a camera */

    private boolean checkCameraHardware() {
        PackageManager pm = this.getPackageManager();
        // this device has a camera
        // no camera on this device
        return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }
 /*
    //sign in intent
    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new FirebaseAuthUIActivityResultContract(),
            new ActivityResultCallback<FirebaseAuthUIAuthenticationResult>() {
                @Override
                public void onActivityResult(FirebaseAuthUIAuthenticationResult result) {
                    onSignInResult(result);
                }
            }
    );
    private void onSignInResult(FirebaseAuthUIAuthenticationResult result) {
        IdpResponse response = result.getIdpResponse();
        if (result.getResultCode() == RESULT_OK) {
            // Successfully signed in
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            // ...
        } else {
            // Sign in failed. If response is null the user canceled the
            // sign-in flow using the back button. Otherwise check
            // response.getError().getErrorCode() and handle the error.
            // ...
        }
    }

// ...

    private void startSignIn() {
        Intent signInIntent = AuthUI.getInstance()
                .createSignInIntentBuilder()
                // ... options ...
                .build();

        signInLauncher.launch(signInIntent);
    }*/


}

