/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package com.phonemetra.turbo.ui.Components;

import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Handler;
import android.view.TextureView;

import com.phonemetra.turbo.messenger.exoplayer2.Player;
import com.phonemetra.turbo.messenger.exoplayer2.source.LoopingMediaSource;
import com.phonemetra.turbo.messenger.secretmedia.ExtendedDefaultDataSourceFactory;
import com.phonemetra.turbo.messenger.ApplicationLoader;
import com.phonemetra.turbo.messenger.exoplayer2.DefaultLoadControl;
import com.phonemetra.turbo.messenger.exoplayer2.DefaultRenderersFactory;
import com.phonemetra.turbo.messenger.exoplayer2.ExoPlaybackException;
import com.phonemetra.turbo.messenger.exoplayer2.ExoPlayer;
import com.phonemetra.turbo.messenger.exoplayer2.ExoPlayerFactory;
import com.phonemetra.turbo.messenger.exoplayer2.PlaybackParameters;
import com.phonemetra.turbo.messenger.exoplayer2.SimpleExoPlayer;
import com.phonemetra.turbo.messenger.exoplayer2.Timeline;
import com.phonemetra.turbo.messenger.exoplayer2.extractor.DefaultExtractorsFactory;
import com.phonemetra.turbo.messenger.exoplayer2.source.ExtractorMediaSource;
import com.phonemetra.turbo.messenger.exoplayer2.source.MediaSource;
import com.phonemetra.turbo.messenger.exoplayer2.source.TrackGroupArray;
import com.phonemetra.turbo.messenger.exoplayer2.source.dash.DashMediaSource;
import com.phonemetra.turbo.messenger.exoplayer2.source.dash.DefaultDashChunkSource;
import com.phonemetra.turbo.messenger.exoplayer2.source.hls.HlsMediaSource;
import com.phonemetra.turbo.messenger.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.phonemetra.turbo.messenger.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.phonemetra.turbo.messenger.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.phonemetra.turbo.messenger.exoplayer2.trackselection.DefaultTrackSelector;
import com.phonemetra.turbo.messenger.exoplayer2.trackselection.MappingTrackSelector;
import com.phonemetra.turbo.messenger.exoplayer2.trackselection.TrackSelection;
import com.phonemetra.turbo.messenger.exoplayer2.trackselection.TrackSelectionArray;
import com.phonemetra.turbo.messenger.exoplayer2.upstream.DataSource;
import com.phonemetra.turbo.messenger.exoplayer2.upstream.DefaultBandwidthMeter;
import com.phonemetra.turbo.messenger.exoplayer2.upstream.DefaultHttpDataSourceFactory;

@SuppressLint("NewApi")
public class VideoPlayer implements ExoPlayer.EventListener, SimpleExoPlayer.VideoListener {

    public interface RendererBuilder {
        void buildRenderers(VideoPlayer player);
        void cancel();
    }

    public interface VideoPlayerDelegate {
        void onStateChanged(boolean playWhenReady, int playbackState);
        void onError(Exception e);
        void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio);
        void onRenderedFirstFrame();
        void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture);
        boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture);
    }

    private SimpleExoPlayer player;
    private SimpleExoPlayer audioPlayer;
    private MappingTrackSelector trackSelector;
    private Handler mainHandler;
    private DataSource.Factory mediaDataSourceFactory;
    private TextureView textureView;
    private boolean autoplay;
    private boolean mixedAudio;

    private boolean videoPlayerReady;
    private boolean audioPlayerReady;
    private boolean mixedPlayWhenReady;

    private VideoPlayerDelegate delegate;
    private int lastReportedPlaybackState;
    private boolean lastReportedPlayWhenReady;

    private static final int RENDERER_BUILDING_STATE_IDLE = 1;
    private static final int RENDERER_BUILDING_STATE_BUILDING = 2;
    private static final int RENDERER_BUILDING_STATE_BUILT = 3;

    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();

    public VideoPlayer() {
        mediaDataSourceFactory = new ExtendedDefaultDataSourceFactory(ApplicationLoader.applicationContext, BANDWIDTH_METER, new DefaultHttpDataSourceFactory("Mozilla/5.0 (X11; Linux x86_64; rv:10.0) Gecko/20150101 Firefox/47.0 (Chrome)", BANDWIDTH_METER));

        mainHandler = new Handler();

        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);
        trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

        lastReportedPlaybackState = ExoPlayer.STATE_IDLE;
    }

    private void ensurePleyaerCreated() {
        if (player == null) {
            player = ExoPlayerFactory.newSimpleInstance(ApplicationLoader.applicationContext, trackSelector, new DefaultLoadControl(), null, DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
            player.addListener(this);
            player.setVideoListener(this);
            player.setVideoTextureView(textureView);
            player.setPlayWhenReady(autoplay);
        }
        if (mixedAudio) {
            if (audioPlayer == null) {
                audioPlayer = ExoPlayerFactory.newSimpleInstance(ApplicationLoader.applicationContext, trackSelector, new DefaultLoadControl(), null, DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
                audioPlayer.addListener(new Player.EventListener() {
                    @Override
                    public void onTimelineChanged(Timeline timeline, Object manifest) {

                    }

                    @Override
                    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

                    }

                    @Override
                    public void onLoadingChanged(boolean isLoading) {

                    }

                    @Override
                    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                        if (!audioPlayerReady && playbackState == Player.STATE_READY) {
                            audioPlayerReady = true;
                            checkPlayersReady();
                        }
                    }

                    @Override
                    public void onRepeatModeChanged(int repeatMode) {

                    }

                    @Override
                    public void onPlayerError(ExoPlaybackException error) {

                    }

                    @Override
                    public void onPositionDiscontinuity() {

                    }

                    @Override
                    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

                    }
                });
                audioPlayer.setPlayWhenReady(autoplay);
            }
        }
    }

    public void preparePlayerLoop(Uri videoUri, String videoType, Uri audioUri, String audioType) {
        mixedAudio = true;
        audioPlayerReady = false;
        videoPlayerReady = false;
        ensurePleyaerCreated();
        MediaSource mediaSource1 = null, mediaSource2 = null;
        for (int a = 0; a < 2; a++) {
            MediaSource mediaSource;
            String type;
            Uri uri;
            if (a == 0) {
                type = videoType;
                uri = videoUri;
            } else {
                type = audioType;
                uri = audioUri;
            }
            switch (type) {
                case "dash":
                    mediaSource = new DashMediaSource(uri, mediaDataSourceFactory, new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);
                    break;
                case "hls":
                    mediaSource = new HlsMediaSource(uri, mediaDataSourceFactory, mainHandler, null);
                    break;
                case "ss":
                    mediaSource = new SsMediaSource(uri, mediaDataSourceFactory, new DefaultSsChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);
                    break;
                default:
                    mediaSource = new ExtractorMediaSource(uri, mediaDataSourceFactory, new DefaultExtractorsFactory(), mainHandler, null);
                    break;
            }
            mediaSource = new LoopingMediaSource(mediaSource);
            if (a == 0) {
                mediaSource1 = mediaSource;
            } else {
                mediaSource2 = mediaSource;
            }
        }
        player.prepare(mediaSource1, true, true);
        audioPlayer.prepare(mediaSource2, true, true);
    }

    public void preparePlayer(Uri uri, String type) {
        videoPlayerReady = false;
        mixedAudio = false;
        ensurePleyaerCreated();
        MediaSource mediaSource;
        switch (type) {
            case "dash":
                mediaSource = new DashMediaSource(uri, mediaDataSourceFactory, new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);
                break;
            case "hls":
                mediaSource = new HlsMediaSource(uri, mediaDataSourceFactory, mainHandler, null);
                break;
            case "ss":
                mediaSource = new SsMediaSource(uri, mediaDataSourceFactory, new DefaultSsChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);
                break;
            default:
                mediaSource = new ExtractorMediaSource(uri, mediaDataSourceFactory, new DefaultExtractorsFactory(), mainHandler, null);
                break;
        }
        player.prepare(mediaSource, true, true);
    }

    public boolean isPlayerPrepared() {
        return player != null;
    }

    public void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
        if (audioPlayer != null) {
            audioPlayer.release();
            audioPlayer = null;
        }
    }

    public void setTextureView(TextureView texture) {
        if (textureView == texture) {
            return;
        }
        textureView = texture;
        if (player == null) {
            return;
        }
        player.setVideoTextureView(textureView);
    }

    public void play() {
        mixedPlayWhenReady = true;
        if (mixedAudio) {
            if (!audioPlayerReady || !videoPlayerReady) {
                if (player != null) {
                    player.setPlayWhenReady(false);
                }
                if (audioPlayer != null) {
                    audioPlayer.setPlayWhenReady(false);
                }
                return;
            }
        }
        if (player != null) {
            player.setPlayWhenReady(true);
        }
        if (audioPlayer != null) {
            audioPlayer.setPlayWhenReady(true);
        }
    }

    public void pause() {
        mixedPlayWhenReady = false;
        if (player != null) {
            player.setPlayWhenReady(false);
        }
        if (audioPlayer != null) {
            audioPlayer.setPlayWhenReady(false);
        }
    }

    public void setPlayWhenReady(boolean playWhenReady) {
        mixedPlayWhenReady = playWhenReady;
        if (playWhenReady && mixedAudio) {
            if (!audioPlayerReady || !videoPlayerReady) {
                if (player != null) {
                    player.setPlayWhenReady(false);
                }
                if (audioPlayer != null) {
                    audioPlayer.setPlayWhenReady(false);
                }
                return;
            }
        }
        autoplay = playWhenReady;
        if (player != null) {
            player.setPlayWhenReady(playWhenReady);
        }
        if (audioPlayer != null) {
            audioPlayer.setPlayWhenReady(playWhenReady);
        }
    }

    public long getDuration() {
        return player != null ? player.getDuration() : 0;
    }

    public long getCurrentPosition() {
        return player != null ? player.getCurrentPosition() : 0;
    }

    public boolean isMuted() {
        return player.getVolume() == 0.0f;
    }

    public void setMute(boolean value) {
        if (player != null) {
            player.setVolume(value ? 0.0f : 1.0f);
        }
        if (audioPlayer != null) {
            audioPlayer.setVolume(value ? 0.0f : 1.0f);
        }
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {

    }

    public void setVolume(float volume) {
        if (player != null) {
            player.setVolume(volume);
        }
        if (audioPlayer != null) {
            audioPlayer.setVolume(volume);
        }
    }

    public void seekTo(long positionMs) {
        if (player != null) {
            player.seekTo(positionMs);
        }
    }

    public void setDelegate(VideoPlayerDelegate videoPlayerDelegate) {
        delegate = videoPlayerDelegate;
    }

    public int getBufferedPercentage() {
        return player != null ? player.getBufferedPercentage() : 0;
    }

    public long getBufferedPosition() {
        return player != null ? player.getBufferedPosition() : 0;
    }

    public boolean isPlaying() {
        return mixedAudio && mixedPlayWhenReady || player != null && player.getPlayWhenReady();
    }

    public boolean isBuffering() {
        return player != null && lastReportedPlaybackState == ExoPlayer.STATE_BUFFERING;
    }

    public void setStreamType(int type) {
        if (player != null) {
            player.setAudioStreamType(type);
        }
        if (audioPlayer != null) {
            audioPlayer.setAudioStreamType(type);
        }
    }

    private void checkPlayersReady() {
        if (audioPlayerReady && videoPlayerReady && mixedPlayWhenReady) {
            play();
        }
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        maybeReportPlayerState();
        if (!videoPlayerReady && playbackState == Player.STATE_READY) {
            videoPlayerReady = true;
            checkPlayersReady();
        }
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        delegate.onError(error);
    }

    @Override
    public void onPositionDiscontinuity() {

    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        delegate.onVideoSizeChanged(width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
    }

    @Override
    public void onRenderedFirstFrame() {
        delegate.onRenderedFirstFrame();
    }

    @Override
    public boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture) {
        return delegate.onSurfaceDestroyed(surfaceTexture);
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        delegate.onSurfaceTextureUpdated(surfaceTexture);
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

    }

    private void maybeReportPlayerState() {
        boolean playWhenReady = player.getPlayWhenReady();
        int playbackState = player.getPlaybackState();
        if (lastReportedPlayWhenReady != playWhenReady || lastReportedPlaybackState != playbackState) {
            delegate.onStateChanged(playWhenReady, playbackState);
            lastReportedPlayWhenReady = playWhenReady;
            lastReportedPlaybackState = playbackState;
        }
    }
}
