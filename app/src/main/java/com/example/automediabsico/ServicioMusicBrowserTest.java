package com.example.automediabsico;

import android.app.Service;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.service.media.MediaBrowserService;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ServicioMusicBrowserTest extends MediaBrowserService {
    private MediaSession mSession;
    private List<MediaMetadata> mMusic;
    private MediaPlayer mPlayer;
    private MediaMetadata mCurrentTrack;
    private int currentIndex = 0;

    private final String TAG = ServicioMusicBrowserTest.this.getClass().getSimpleName();
    private final String URL = "http://storage.googleapis.com/automotive-media/music.json";
    private RequestQueue requestQueue;

    private Gson gson;
    private Musica musica;

    @Override
    public void onCreate() {
        super.onCreate();

        GsonBuilder gsonBuilder = new GsonBuilder();
        gson = gsonBuilder.create();

        requestQueue = Volley.newRequestQueue(this);

        getRepositorioMusical();

         mMusic = new ArrayList<>();
    }

    private PlaybackState buildState(int state) {

        return new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_PLAY_PAUSE).setState(state, mPlayer.getCurrentPosition(), 1, SystemClock.elapsedRealtime()).build();
    }

    private void handlePlay() {
        if(mPlayer.isPlaying()){
            mPlayer.seekTo(0);
            mSession.setPlaybackState(buildState(PlaybackState.STATE_PLAYING));
            mSession.setMetadata(mCurrentTrack);
            try {
                mPlayer.reset();
                mPlayer.setDataSource(ServicioMusicBrowserTest.this, Uri.parse(mCurrentTrack.getDescription().getMediaId()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mediaPlayer) {
                    mediaPlayer.start();
                }
            });
            mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    mediaPlayer.seekTo(0);
                    mSession.setPlaybackState(buildState(PlaybackState.STATE_PAUSED));
                }
            });
            mPlayer.prepareAsync();
        }
    }

    @Override
    public MediaBrowserService.BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        return new MediaBrowserService.BrowserRoot("ROOT", null);
    }

    @Override
    public void onLoadChildren(String s, Result<List<MediaBrowser.MediaItem>> result) {
        List<MediaBrowser.MediaItem> list = new ArrayList<MediaBrowser.MediaItem>();
        for (MediaMetadata m : mMusic) {
            list.add(new MediaBrowser.MediaItem(m.getDescription(), MediaBrowser.MediaItem.FLAG_PLAYABLE));
        }
        result.sendResult(list);
    }

    @Override
    public void onDestroy() {
        mSession.release();
    }


    private void getRepositorioMusical() {
        StringRequest request = new StringRequest(Request.Method.GET, URL, onPostsLoaded, onPostsError);
        requestQueue.add(request);
    }

    private final Response.Listener<String> onPostsLoaded = new Response.Listener<String>() {
        @Override
        public void onResponse(String response) {
            musica = gson.fromJson(response, Musica.class);
            Log.d(TAG, "Número de pistas de audio: " + musica.getMusica().size());

            int slashPos = URL.lastIndexOf('/');
            String path = URL.substring(0, slashPos + 1);

            for (int i = 0; i < musica.getMusica().size(); i++) {
                PistaAudio pista = musica.getMusica().get(i);
                if (!pista.getSource().startsWith("http")) {
                    pista.setSource(path + pista.getSource());
                }

                if (!pista.getImage().startsWith("http")) {
                    pista.setImage(path + pista.getImage());
                }

                mMusic.add(new MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, pista.getSite())
                        .putString(MediaMetadata.METADATA_KEY_TITLE, pista.getTitle())
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, pista.getArtist())
                        .putLong(MediaMetadata.METADATA_KEY_DURATION, pista.getDuration() * 1000)
                        .build());

                // musica.getMusica().set(i, pista);
            }

            mPlayer = new MediaPlayer();
            mSession = new MediaSession(ServicioMusicBrowserTest.this, "MiServicioMusical");
            mSession.setCallback(new MediaSession.Callback() {
                @Override
                public void onPlayFromMediaId(String mediaId, Bundle extras) {
                    for (MediaMetadata item : mMusic) {
                        if (item.getDescription().getMediaId().equals(mediaId)) {
                            mCurrentTrack = item;
                            break;
                        }
                    }
                    handlePlay();
                }

                @Override
                public void onPlay() {
                    if (mCurrentTrack == null) {
                        mCurrentTrack = mMusic.get(currentIndex);
                        handlePlay();
                    } else {
                        mPlayer.start();
                        mSession.setPlaybackState(buildState(PlaybackState.STATE_PLAYING));
                    }
                }

                @Override
                public void onPause() {
                    if(mPlayer.isPlaying()){
                        mPlayer.pause();
                        mSession.setPlaybackState(buildState(PlaybackState.STATE_PAUSED));
                    }
                }

                @Override
                public void onSkipToNext() {
                    if (currentIndex < mMusic.size() - 1) {
                        currentIndex++;
                        mCurrentTrack = mMusic.get(currentIndex);
                        mSession.setPlaybackState(buildState(PlaybackState.STATE_SKIPPING_TO_NEXT));
                        handlePlay();
                    }
                }

                @Override
                public void onSkipToPrevious() {
                    if (currentIndex > 0) {
                        currentIndex--;
                        mCurrentTrack = mMusic.get(currentIndex);
                        mSession.setPlaybackState(buildState(PlaybackState.STATE_SKIPPING_TO_PREVIOUS));
                        handlePlay();
                    }
                }
            });
            mSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            mSession.setActive(true);
            setSessionToken(mSession.getSessionToken());
        }
    };
    private final Response.ErrorListener onPostsError = new Response.ErrorListener() {
        @Override
        public void onErrorResponse(VolleyError error) {
            Log.e(TAG, error.toString());
        }
    };
}