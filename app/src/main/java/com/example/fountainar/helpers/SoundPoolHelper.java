package com.example.fountainar.helpers;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.SoundPool;

import com.example.fountainar.R;

/**
 * Helper for managing sound effects using SoundPool.
 */
public class SoundPoolHelper {

    private final Activity ACTIVITY;

    private SoundPool soundPool;
    private int soundId;
    private boolean soundPoolPlaying = false;

    public SoundPoolHelper(Activity activity) {
        this.ACTIVITY = activity;
        setupSoundPool();
    }

    /**
     * Sets up the SoundPool and loads the sound file.
     */
    private void setupSoundPool() {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        soundPool = new SoundPool.Builder()
                .setAudioAttributes(attributes)
                .build();

        soundId = soundPool.load(ACTIVITY, R.raw.fountain_animation_sound, 1);
    }

    /**
     * Plays the sound effect.
     * If the sound is already playing, this method has no effect.
     */
    public void play() {
        if (!soundPoolPlaying) {
            soundPoolPlaying = true;
            soundPool.play(soundId, 0.8f, 0.8f, 1, -1, 1.0f);
        }
    }

    /**
     * Pauses the currently playing sound effect.
     */
    public void pause() {
        soundPool.pause(soundId);
    }

    /**
     * Releases the SoundPool and associated resources.
     */
    public void release() {
        soundPool.stop(soundId);
        soundPool.release();
        soundPool = null;
    }
}
