package com.jabcode.test;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Launcher activity - immediately launches camera scanner.
 */
public class MainActivity extends Activity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Launch scanner activity
        Intent intent = new Intent(this, ScannerActivity.class);
        startActivity(intent);
        finish();
    }
}
