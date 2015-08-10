package com.virtualapplications.play;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.ContextThemeWrapper;

import java.io.File;

public class EmulatorLauncher extends Activity {

    public EmulatorLauncher() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NativeInterop.setFilesDirPath(Environment.getExternalStorageDirectory().getAbsolutePath());

        EmulatorActivity.RegisterPreferences();

        if(!NativeInterop.isVirtualMachineCreated())
        {
            NativeInterop.createVirtualMachine();
        }
        Intent intent = getIntent();
        if (intent.getAction() != null) {
            if (intent.getAction().equals(Intent.ACTION_VIEW)) {
                MainActivity.launchGame(new File(intent.getData().getPath()));
            }
        }
        finish();
    }
}
