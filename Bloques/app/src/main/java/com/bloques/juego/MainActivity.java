package com.bloques.juego;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public final class MainActivity extends Activity {

    private GameView view;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        view = new GameView(this, getSharedPreferences("bloques", MODE_PRIVATE));
        setContentView(view);
    }

    @Override
    protected void onPause() {
        super.onPause();
        view.pauseGame();
    }

    @Override
    public void onBackPressed() {
        if (!view.handleBack()) super.onBackPressed();
    }
}
