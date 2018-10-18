package com.beibei.appupdate;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;


import java.io.File;
import java.lang.ref.WeakReference;


public class AppDownloadManager {
//    public static final String TAG = "AppDownloadManager";
    public static final String APP_NAME = "_temp.apk";
    public static final String NEW_VERSION_APK_DOWNLOAD_ID = "NewVersionApkDownloadId";
    private WeakReference<Activity> weakReference;
    private DownloadManager mDownloadManager;
    private DownloadChangeObserver mDownLoadChangeObserver;
    private DownloadReceiver mDownloadReceiver;
    private long mReqId;//标识当前下载任务的id，即downloadId。
    private OnUpdateListener mUpdateListener;

    public AppDownloadManager(Activity activity) {
        weakReference = new WeakReference<Activity>(activity);
        mDownloadManager = (DownloadManager) weakReference.get().getSystemService(Context.DOWNLOAD_SERVICE);
        mDownLoadChangeObserver = new DownloadChangeObserver(new Handler());
        mDownloadReceiver = new DownloadReceiver();
        registerService();
    }

    /**
     * 是否下载过的最新的apk文件
     * <p>
     * 会做一些额外的操作 下载过 直接安装
     *
     * @return true
     */
    public boolean existNewVersionApk() {
        //如果sp中有记录下载的新版本的apk
        long downloadId = (long) SPUtils.get(weakReference.get(), NEW_VERSION_APK_DOWNLOAD_ID, -1L);
        if (downloadId != -1) {   //存在downloadId
            DownLoadUtils downLoadUtils = DownLoadUtils.getInstance(weakReference.get());
            int status = downLoadUtils.getDownloadStatus(downloadId); //获取当前状态
            if (DownloadManager.STATUS_SUCCESSFUL == status) {   //状态为下载成功
                //存在下载的APK，如果两个APK相同，启动更新界面。否之则删除，重新下载。
                if (downLoadUtils.compareApkInfo(downLoadUtils.getApkInfo(weakReference.get()), weakReference.get())) {
                    Intent intent = new Intent();
                    intent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId);
                    downLoadUtils.installApk(weakReference.get(), intent);
                    return true;
                } else {
                    //删除下载任务以及文件
//                    downLoadUtils.getDownloadManager().remove(downloadId);
                    boolean isDelete = downLoadUtils.deleteAPk();
                    SPUtils.put(weakReference.get(), NEW_VERSION_APK_DOWNLOAD_ID, -1);
                }
            }
        }
        return false;
    }

    /**
     * 下载正式开始
     */
    public void downloadStart(String apkUrl, String title, String desc) {
        // fix bug : 装不了新版本，在下载之前应该删除已有文件
        File apkFile = new File(weakReference.get().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APP_NAME);
        if (apkFile != null && apkFile.exists()) {
            apkFile.delete();
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
        request.setTitle(title);         //设置title
        request.setDescription(desc);    // 设置描述
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);  // 完成后显示通知栏
        // 如果我们希望下载的文件可以被系统的Downloads应用扫描到并管理，我们需要调用Request对象的setVisibleInDownloadsUi方法，传递参数true.
        request.setVisibleInDownloadsUi(true);
        request.setDestinationInExternalFilesDir(weakReference.get(), Environment.DIRECTORY_DOWNLOADS, APP_NAME); //文件保存位置 file:///storage/emulated/0/Android/data/your-package/files/Download/appName.apk
        //在手机SD卡上创建一个download文件夹
        // Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).mkdir() ;
        //指定下载到SD卡的/download/my/目录下
        // request.setDestinationInExternalPublicDir("/codoon/","codoon_health.apk");

        request.setMimeType("application/vnd.android.package-archive");
        mReqId = mDownloadManager.enqueue(request);
        SPUtils.put(weakReference.get(), NEW_VERSION_APK_DOWNLOAD_ID, mReqId);
    }

    /**
     * 取消下载
     */
    public void cancel() {
        mDownloadManager.remove(mReqId);
    }


    /**
     * 下载进度监听
     */
    class DownloadChangeObserver extends ContentObserver {
        /**
         * Creates a content observer.
         *
         * @param handler The handler to run {@link #onChange} on, or null if none.
         */
        public DownloadChangeObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            updateView();
        }
    }

    private void updateView() {
        int[] bytesAndStatus = new int[]{0, 0, 0};
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(mReqId);
        Cursor c = null;
        try {
            c = mDownloadManager.query(query);
            if (c != null && c.moveToFirst()) {
                //已经下载的字节数
                bytesAndStatus[0] = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                //总需下载的字节数
                bytesAndStatus[1] = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                //状态所在的列索引
                bytesAndStatus[2] = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }

        if (mUpdateListener != null) {
            mUpdateListener.update(bytesAndStatus[0], bytesAndStatus[1]);
        }

//        Log.i(TAG, "下载进度：" + bytesAndStatus[0] + "/" + bytesAndStatus[1] + "");
    }

    public interface OnUpdateListener {
        void update(int currentByte, int totalByte);
    }


    public void setUpdateListener(OnUpdateListener mUpdateListener) {
        this.mUpdateListener = mUpdateListener;
    }

    public void registerService() {
        //设置监听Uri.parse("content://downloads/my_downloads")
        weakReference.get().getContentResolver().registerContentObserver(
                Uri.parse("content://downloads/my_downloads"), true, mDownLoadChangeObserver);
        // 注册广播，监听APK是否下载完成
        weakReference.get().registerReceiver(mDownloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    /**
     * 对应{@link Activity#onPause()} ()}
     */
    public void onDestroy() {
        weakReference.get().getContentResolver().unregisterContentObserver(mDownLoadChangeObserver);
        weakReference.get().unregisterReceiver(mDownloadReceiver);
    }
}
