package com.x.evee.mic;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;

public class MainActivity extends Activity {
    private boolean isRunning = true;
    private final int sampleRate = 44100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 101);
        } else {
            startAudioProcess();
        }
    }

    private void startAudioProcess() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                zeroDelayResonance();
            }
        }).start();
    }

    private void zeroDelayResonance() {
        int bufSize = AudioRecord.getMinBufferSize(sampleRate, 
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize);

        AudioTrack tracker = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize, 
                AudioTrack.MODE_STREAM);

        short[] buffer = new short[256];
        
        // Filter States for ZDF (Zero Delay Feedback)
        float s1 = 0f; 
        float s2 = 0f;
        
        // Tuning: Lower 'g' means deeper, heavier resonance.
        // g = tan(pi * cutoff / sampleRate)
        float cutoffHz = 60.0f; 
        float g = (float) Math.tan(Math.PI * cutoffHz / sampleRate);
        float k = 1.8f; // Resonance/Feedback amount (1.0 to 2.0)

        recorder.startRecording();
        tracker.play();

        while (isRunning) {
            int read = recorder.read(buffer, 0, buffer.length);
            if (read > 0) {
                for (int i = 0; i < read; i++) {
                    float x = buffer[i] / 32768.0f;

                    // --- ZDF Chamberlin Topology ---
                    // This calculates the resonance 'instantly' for each sample
                    float y_high = (x - (k + g) * s1 - s2) / (1 + g * (k + g));
                    float v1 = g * y_high;
                    float y_band = v1 + s1;
                    s1 = v1 + y_band;
                    float v2 = g * y_band;
                    float y_low = v2 + s2;
                    s2 = v2 + y_low;

                    // Layering: Combine the Low-pass and Band-pass outputs
                    // This creates a 'throbbing' continuous texture
                    float output = y_low + (y_band * 0.2f);

                    // Soft saturation to glue the "echoes" together
                    output = (float) Math.tanh(output * 3.0f);

                    buffer[i] = (short) (output * 32767);
                }
                tracker.write(buffer, 0, read);
            }
        }
        recorder.release();
        tracker.release();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning =
          false;
    }
}
