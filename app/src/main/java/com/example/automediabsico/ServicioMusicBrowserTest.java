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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ServicioMusicBrowserTest extends MediaBrowserService {
    private MediaSession mSession;
    private List<MediaMetadata> mMusic;
    private MediaPlayer mPlayer;
    private MediaMetadata mCurrentTrack;
    private int currentIndex = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        mMusic = new ArrayList<MediaMetadata>(); //Añadimos 3 canciones desde la librería de audio de youtube
        mMusic.add(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "https://www.youtube.com/audiolibrary_download?vid=f5cfb6bd8c048b98").putString(MediaMetadata.METADATA_KEY_TITLE, "Primera canción").putString(MediaMetadata.METADATA_KEY_ARTIST, "Artista 1").putLong(MediaMetadata.METADATA_KEY_DURATION, 109000).build());
        mMusic.add(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "https://www.youtube.com/audiolibrary_download?vid=ac7a38f4a568229c").putString(MediaMetadata.METADATA_KEY_TITLE, "Segunda canción").putString(MediaMetadata.METADATA_KEY_ARTIST, "Artista 2").putLong(MediaMetadata.METADATA_KEY_DURATION, 65000).build());
        mMusic.add(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "https://www.youtube.com/audiolibrary_download?vid=456229530454affd").putString(MediaMetadata.METADATA_KEY_TITLE, "Tercera canción").putString(MediaMetadata.METADATA_KEY_ARTIST, "Artista 3").putLong(MediaMetadata.METADATA_KEY_DURATION, 121000).build());
        mPlayer = new MediaPlayer();
        mSession = new MediaSession(this, "MiServicioMusical");
        mSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlayFromMediaId(String mediaId, Bundle extras) {
                for (MediaMetadata item : mMusic) {
                    if (item.getDescription().getMediaId().equals(mediaId)) {
                        mCurrentTrack = item;
                        break;
                    }
                }
                //
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
                mPlayer.pause();
                mSession.setPlaybackState(buildState(PlaybackState.STATE_PAUSED));
            }

            @Override
            public void onSkipToNext() {
                if (currentIndex < mMusic.size()-1) {
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

    private PlaybackState buildState(int state) {

        return new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_PLAY_PAUSE).setState(state, mPlayer.getCurrentPosition(), 1, SystemClock.elapsedRealtime()).build();
    }

    private void handlePlay() {
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
}