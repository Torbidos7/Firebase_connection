package com.example.firebase_connection;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;

public class RegisterActivity extends AppCompatActivity {

    TextInputEditText etRegEmail, etRegName, etRegPassword;
    TextView tvLoginHere;
    Button btnRegister;
    Spinner spRole;

    FirebaseAuth mAuth;
    private FirebaseDatabase db = FirebaseDatabase.getInstance("https://fir-connection-6e1bf-default-rtdb.europe-west1.firebasedatabase.app");
    private DatabaseReference root = db.getReference().child("Users");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        etRegName = findViewById(R.id.etRegName);
        etRegEmail = findViewById(R.id.etRegEmail);
        etRegPassword = findViewById(R.id.etRegPass);
        tvLoginHere = findViewById(R.id.tvLoginHere);
        btnRegister = findViewById(R.id.btnRegister);
        spRole = findViewById(R.id.spinner);

        mAuth = FirebaseAuth.getInstance();

        btnRegister.setOnClickListener(view -> {
            createUser();

        });

        tvLoginHere.setOnClickListener(view -> {
            startActivity(new Intent(RegisterActivity.this, MainActivity.class));
        });
    }

    private void createUser() {
        String email = etRegEmail.getText().toString();
        String password = etRegPassword.getText().toString();
        String name = etRegName.getText().toString();

        if (TextUtils.isEmpty(email)) {
            etRegEmail.setError("Email cannot be empty");
            etRegEmail.requestFocus();
        } else if (TextUtils.isEmpty(password)) {
            etRegPassword.setError("Password cannot be empty");
            etRegPassword.requestFocus();
        } else if(TextUtils.isEmpty(name)) {
            etRegName.setError("Password cannot be empty");
            etRegName.requestFocus();
        }else {
            mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    if (task.isSuccessful()) {
                        Toast.makeText(RegisterActivity.this, "User registered successfully", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                        registerNewUserDatabase();
                    } else {
                        Toast.makeText(RegisterActivity.this, "Registration Error: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private void registerNewUserDatabase() {
        // Take the value of two edit texts in Strings
        String Name = etRegName.getText().toString();
        String Password = etRegPassword.getText().toString();
        String Mail = etRegEmail.getText().toString();
        String Role = spRole.getSelectedItem().toString();
        HashMap<String, String> userMap = new HashMap<>();
        userMap.put("Name", Name);
        userMap.put("Password", Password);
        userMap.put("Email", Mail);
        userMap.put("Role", Role);

        root.push().setValue(userMap);




        }
    }
