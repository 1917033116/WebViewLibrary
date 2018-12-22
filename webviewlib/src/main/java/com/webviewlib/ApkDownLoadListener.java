package com.webviewlib;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.view.View;
import android.widget.Toast;

import com.liulishuo.okdownload.DownloadTask;
import com.liulishuo.okdownload.SpeedCalculator;
import com.liulishuo.okdownload.core.breakpoint.BlockInfo;
import com.liulishuo.okdownload.core.breakpoint.BreakpointInfo;
import com.liulishuo.okdownload.core.cause.EndCause;
import com.liulishuo.okdownload.core.listener.DownloadListener4WithSpeed;
import com.liulishuo.okdownload.core.listener.assist.Listener4SpeedAssistExtend;

import java.io.File;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;

public class ApkDownLoadListener extends DownloadListener4WithSpeed {
    private Context mContext;
    private  NumProgressDialog mDialog;
    public ApkDownLoadListener(Context mContext,NumProgressDialog mDialog){
        this.mContext=mContext;
        this.mDialog=mDialog;
    }
    private long totalLength= 0;
    private void installApk(File file) {
        Observable.just(file)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<File>() {
                    @Override
                    public void accept(File file) throws Exception {
                        Intent action =new  Intent(Intent.ACTION_VIEW);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            action.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            Uri contentUri = FileProvider.getUriForFile(mContext,
                                    mContext.getPackageName() + ".provider", file);
                            action.setDataAndType(contentUri, "application/vnd.android.package-archive");
                        } else {
                            action.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
                            action.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        }
                        mContext.startActivity(action);
                    }
                });
    }

    @Override
    public void taskStart(@NonNull DownloadTask task) {
        mDialog.show();
    }

    @Override
    public void connectStart(@NonNull DownloadTask task, int blockIndex, @NonNull Map<String, List<String>> requestHeaderFields) {

    }

    @Override
    public void connectEnd(@NonNull DownloadTask task, int blockIndex, int responseCode, @NonNull Map<String, List<String>> responseHeaderFields) {

    }

    @Override
    public void infoReady(@NonNull DownloadTask task, @NonNull BreakpointInfo info, boolean fromBreakpoint, @NonNull Listener4SpeedAssistExtend.Listener4SpeedModel model) {
        totalLength = info.getTotalLength();
    }

    @Override
    public void progressBlock(@NonNull DownloadTask task, int blockIndex, long currentBlockOffset, @NonNull SpeedCalculator blockSpeed) {

    }

    @Override
    public void progress(@NonNull DownloadTask task, long currentOffset, @NonNull SpeedCalculator taskSpeed) {
        int percent = Integer.parseInt((Float.parseFloat(currentOffset+"")/ Float.parseFloat(""+totalLength) * 100)+"");
        mDialog.setProgress(percent);
    }

    @Override
    public void blockEnd(@NonNull DownloadTask task, int blockIndex, BlockInfo info, @NonNull SpeedCalculator blockSpeed) {

    }

    @Override
    public void taskEnd(@NonNull final DownloadTask task, @NonNull EndCause cause, @Nullable Exception realCause, @NonNull SpeedCalculator taskSpeed) {
        if (realCause != null) {
            return;
        }
        if (task.getFile() == null || !task.getFile().exists()) {
            Toast.makeText(mContext,"文件不存在",Toast.LENGTH_SHORT).show();
            return;
        }
        mDialog.setAction("点击安装", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                installApk(task.getFile());

            }
        });
        installApk(task.getFile());
    }
}
