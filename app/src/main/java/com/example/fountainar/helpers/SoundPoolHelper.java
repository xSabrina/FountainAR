package com.example.fountainar.helpers;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.SoundPool;

import com.example.fountainar.R;

public class SoundPoolHelper {

    private final Activity activity;
    private SoundPool soundPool;
    private int soundId;
    private boolean soundPoolPlaying = false;

    public SoundPoolHelper(Activity activity) {
        this.activity = activity;
        setupSoundPool();
    }

    private void setupSoundPool() {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        soundPool = new SoundPool.Builder()
                .setAudioAttributes(attributes)
                .build();

        soundId = soundPool.load(activity, R.raw.fountain_animation_sound, 1);
    }

    public void play(){
        if (!soundPoolPlaying) {
            soundPoolPlaying = true;
            soundPool.play(soundId, 0.8f, 0.8f, 1, -1, 1.0f);
        }
    }

    public void pause(){
        soundPool.pause(soundId);
    }

    public void release(){
        soundPool.stop(soundId);
        soundPool.release();
        soundPool = null;
    }
}
