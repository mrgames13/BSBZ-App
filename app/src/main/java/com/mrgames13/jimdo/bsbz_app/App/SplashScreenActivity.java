package com.mrgames13.jimdo.bsbz_app.App;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.PersistableBundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.mrgames13.jimdo.bsbz_app.HelpClasses.MutedVideoView;
import com.mrgames13.jimdo.bsbz_app.R;
import com.mrgames13.jimdo.bsbz_app.Utils.SimpleAnimationListener;

public class SplashScreenActivity extends AppCompatActivity {
    //Konstanten

    //Variablen als Objekte
    private MutedVideoView video;
    private TextView app_name;
    private TextView powered;
    private ImageView app_logo;
    private RelativeLayout container;
    private Handler h;

    //Variablen
    private int position = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        //Handler initialisieren
        h = new Handler();

        container = findViewById(R.id.logo_container);
        container.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                video.stopPlayback();
                Intent i = new Intent(SplashScreenActivity.this, LogInActivity.class);
                startActivity(i);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
                return true;
            }
        });

        app_name = findViewById(R.id.logo_app_title);
        powered = findViewById(R.id.logo_powered);
        app_logo = findViewById(R.id.logo_image_view);

        final Uri video_uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.logo_animation);
        video = findViewById(R.id.logo_video_view);
        video.setVideoURI(video_uri);
        video.setDrawingCacheEnabled(true);
        video.setZOrderOnTop(true);
        video.requestFocus();
        video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                video.seekTo(0);
                video.start();
                mp.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                    @Override
                    public boolean onInfo(MediaPlayer mp, int what, int extra) {
                        if(what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) app_logo.setVisibility(View.VISIBLE);
                        return false;
                    }
                });
            }
        });
        video.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                video.setVisibility(View.GONE);
                //Schriftzug langsam einblenden
                Animation fade_in = AnimationUtils.loadAnimation(SplashScreenActivity.this, android.R.anim.fade_in);
                fade_in.setAnimationListener(new SimpleAnimationListener() {
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        h.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Intent i = new Intent(SplashScreenActivity.this, LogInActivity.class);
                                startActivity(i);
                                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                                finish();
                            }
                        }, 1000);
                    }
                });
                app_name.setAnimation(fade_in);
                app_name.setVisibility(View.VISIBLE);
                powered.setAnimation(fade_in);
                powered.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putInt("pos", position);
        video.pause();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        position = savedInstanceState.getInt("pos");
        video.seekTo(position);
    }
}