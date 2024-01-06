package com.artillery.musicmain.ui;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.ViewModelProviders;

import com.artillery.musicbase.base.BaseActivity;
import com.artillery.musicbase.binding.command.BindingAction;
import com.artillery.musicbase.binding.command.BindingCommand;
import com.artillery.musicbase.utils.KLog;
import com.artillery.musicmain.BR;
import com.artillery.musicmain.R;
import com.artillery.musicmain.app.AppViewModelFactory;
import com.artillery.musicmain.data.MusicContext;
import com.artillery.musicmain.data.source.contract.view.MusicPlayView;
import com.artillery.musicmain.databinding.ActivityMusicPlayBinding;
import com.artillery.musicmain.viewmodel.MusicPlayViewModel;
import com.artillery.musicmain.utils.TimeUtils;
import com.artillery.musicservice.data.PlayList;
import com.artillery.musicservice.data.Song;
import com.artillery.musicservice.service.MusicListener;
import com.artillery.musicservice.service.MusicMode;
import com.artillery.musicservice.service.MusicService;

import java.util.ArrayList;

/**
 * @author ArtilleryOrchid
 */
@RequiresApi(api = Build.VERSION_CODES.P)
public class MusicPlayActivity extends BaseActivity<ActivityMusicPlayBinding, MusicPlayViewModel> implements MusicPlayView, MusicListener.Callback, SeekBar.OnSeekBarChangeListener {
    private MusicListener mMusicListener;
    private PlayList mPlayList;
    private int mStartIndex = 0;
    private Song mSong;
    private final Handler mHandler = Handler.createAsync(Looper.getMainLooper());
    private final Runnable mProgressCallback = new Runnable() {
        @Override
        public void run() {
            if (mMusicListener.isPlaying()) {
                int progress = (int) (binding.musicSeekbar.getMax()
                        * ((float) mMusicListener.getProgress() / (float) getCurrentSongDuration()));
                updateProgressTextWithDuration(mMusicListener.getProgress());
                if (progress >= 0 && progress <= binding.musicSeekbar.getMax()) {
                    binding.musicSeekbar.setProgress(progress, true);
                }
                mHandler.postDelayed(mProgressCallback, DateUtils.SECOND_IN_MILLIS);
            }
        }
    };

    @Override
    public void initParam() {
        mPlayList = new PlayList();
        mSong = getIntent().getParcelableExtra(MusicContext.MUSIC_PLAY_SONG);
        ArrayList<Song> mSongList = getIntent().getParcelableArrayListExtra(MusicContext.MUSIC_PLAY_SONG_LIST);
        mPlayList.setSongs(mSongList);
        assert mSongList != null;
        mPlayList.setNumOfSongs(mSongList.size());
        mStartIndex = getIntent().getIntExtra(MusicContext.MUSIC_PLAY_SONG_START, 0);
    }

    @Override
    public int initContentView(Bundle savedInstanceState) {
        return R.layout.activity_music_play;
    }

    @Override
    public int initVariableId() {
        return BR.musicPlayModel;
    }

    @Override
    public MusicPlayViewModel initViewModel() {
        AppViewModelFactory factory = AppViewModelFactory.getInstance(getApplication());
        return ViewModelProviders.of(this, factory).get(MusicPlayViewModel.class);
    }

    @Override
    public void initData() {
        KLog.e(" initData ============>");
        updateMainUi(mSong);
    }

    @Override
    public void initViewObservable() {
        viewModel.mMusicRepository.bindMusicView(this);
        binding.musicSeekbar.setOnSeekBarChangeListener(this);
        viewModel.play = new BindingCommand(new BindingAction() {
            @Override
            public void call() {
                if (mMusicListener == null) {
                    return;
                }
                if (mMusicListener.isPlaying()) {
                    mMusicListener.pause();
                } else {
                    mMusicListener.play();
                }
                mHandler.post(mProgressCallback);
            }
        });
        viewModel.next = new BindingCommand(new BindingAction() {
            @Override
            public void call() {
                mMusicListener.playNext();
            }
        });
        viewModel.pre = new BindingCommand(new BindingAction() {
            @Override
            public void call() {
                mMusicListener.playLast();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mMusicListener != null && mMusicListener.isPlaying()) {
            mHandler.removeCallbacks(mProgressCallback);
            mHandler.post(mProgressCallback);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        mHandler.removeCallbacks(mProgressCallback);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            updateProgressTextWithProgress(progress);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        mHandler.removeCallbacks(mProgressCallback);
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        seekTo(getDuration(seekBar.getProgress()));
        if (mMusicListener.isPlaying()) {
            mHandler.removeCallbacks(mProgressCallback);
            mHandler.post(mProgressCallback);
        }
    }

    private int getDuration(int progress) {
        return (int) (getCurrentSongDuration() * ((float) progress / binding.musicSeekbar.getMax()));
    }

    private void updateProgressTextWithProgress(int progress) {
        binding.musicTimeStart.setText(TimeUtils.formatDuration(getDuration(progress)));
    }

    private void updateProgressTextWithDuration(int duration) {
        binding.musicTimeStart.setText(TimeUtils.formatDuration(duration));
    }

    private void seekTo(int duration) {
        mMusicListener.seekTo(duration);
    }

    private long getCurrentSongDuration() {
        Song currentSong = mMusicListener.getPlayingSong();
        int duration = 0;
        if (currentSong != null) {
            duration = currentSong.getDuration();
        }
        return duration;
    }

    @Override
    public void onSwitchLast(@Nullable Song last) {
        onSongUpdated(last);
    }

    @Override
    public void onSwitchNext(@Nullable Song next) {
        onSongUpdated(next);
    }

    @Override
    public void onComplete(@Nullable Song next) {
        onSongUpdated(next);
    }

    @Override
    public void onPlayStatusChanged(boolean isPlaying) {
        updatePlayToggle(isPlaying);
        if (isPlaying) {
            mHandler.removeCallbacks(mProgressCallback);
            mHandler.post(mProgressCallback);
        } else {
            mHandler.removeCallbacks(mProgressCallback);
        }
    }

    @Override
    public void handleError(Throwable error) {
        KLog.e("Error ================= > " + error);
    }

    @Override
    public void onPlaybackServiceBound(MusicService service) {
        KLog.e(" onPlaybackService ===========> " + service.toString());
        mMusicListener = service;
        mMusicListener.registerCallback(this);
        startPlay();
    }

    private void startPlay() {
        if (mMusicListener != null) {
            mMusicListener.play(mPlayList, mStartIndex);
            mHandler.post(mProgressCallback);
        }
    }

    @Override
    public void onPlaybackServiceUnbound() {
        mMusicListener.unregisterCallback(this);
        mMusicListener = null;
    }

    @Override
    public void onSongSetAsFavorite(@NonNull Song song) {

    }

    @Override
    public void onSongUpdated(@Nullable Song song) {
        if (song == null) {
            mHandler.removeCallbacks(mProgressCallback);
            return;
        }
        //Music Duration
        updateMainUi(song);
        mHandler.removeCallbacks(mProgressCallback);
        if (mMusicListener.isPlaying()) {
            mHandler.post(mProgressCallback);
        }
    }

    private void updateMainUi(Song song) {
        binding.musicNamePlay.setText(song.getTitle());
        binding.musicArtistPlay.setText(song.getArtist());
        binding.musicTimeEnd.setText(TimeUtils.formatDuration(song.getDuration()));
    }

    @Override
    public void updatePlayMode(MusicMode playMode) {

    }

    @Override
    public void updatePlayToggle(boolean play) {

    }

    @Override
    public void updateFavoriteToggle(boolean favorite) {

    }
}