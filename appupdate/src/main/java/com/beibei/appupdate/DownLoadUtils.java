package com.beibei.appupdate;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;


import java.io.File;

import static com.beibei.appupdate.AppDownloadManager.APP_NAME;


/**
 * 封装 DownLoadManager 下载
 * Created by Song on 2016/11/2.
 */
public class DownLoadUtils {

    private Context mContext;
    private DownloadManager mDownloadManager;
    private static volatile DownLoadUtils instance;

    private DownLoadUtils(Context context) {
        this.mContext = context.getApplicationContext();
        mDownloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    /**
     * 获取单例对象
     *
     * @param context
     * @return
     */
    public static DownLoadUtils getInstance(Context context) {
        if (instance == null) {
            synchronized (DownLoadUtils.class) {
                if (instance == null) {
                    instance = new DownLoadUtils(context);
                    return instance;
                }
            }
        }
        return instance;
    }

    /**
     * 获取文件下载路径
     *
     * @param downloadId
     * @return
     */
    public String getDownloadPath(long downloadId) {

        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        Cursor c = mDownloadManager.query(query);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    return c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
                }
            } finally {
                c.close();
            }
        }

        return null;
    }

    /**
     * 获取文件保存的地址
     *
     * @param downloadId
     * @return
     */
    public Uri getDownloadUri(long downloadId) {
        Uri uri;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { // 6.0以下
            uri = mDownloadManager.getUriForDownloadedFile(downloadId);
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) { // 6.0 - 7.0
//            File apkFile = queryDownloadedApk(mContext, downloadId);
//            uri = Uri.fromFile(apkFile);
            uri = Uri.fromFile(new File(mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APP_NAME));
        } else { // Android 7.0 以上
            uri = FileProvider.getUriForFile(mContext,
                    "com.dashen.init.fileProvider",
                    new File(mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APP_NAME));
        }
        return uri;
    }


    //通过downLoadId查询下载的apk，解决6.0以后安装的问题
    private static File queryDownloadedApk(Context context, long downloadId) {
        File targetApkFile = null;
        DownloadManager downloader = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

        if (downloadId != -1) {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);
            query.setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL);
            Cursor cur = downloader.query(query);
            if (cur != null) {
                if (cur.moveToFirst()) {
                    String uriString = cur.getString(cur.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                    if (!TextUtils.isEmpty(uriString)) {
                        targetApkFile = new File(Uri.parse(uriString).getPath());
                    }
                }
                cur.close();
            }
        }
        return targetApkFile;
    }


    public DownloadManager getDownloadManager() {
        return mDownloadManager;
    }

    /**
     * 获取下载状态
     *
     * @param downloadId
     * @return
     */
    public int getDownloadStatus(long downloadId) {

        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        Cursor c = mDownloadManager.query(query);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    return c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                }
            } finally {
                c.close();
            }
        }
        return -1;
    }

    /**
     * 判断下载管理程序是否可用
     *
     * @return
     */
    public boolean canDownload() {

        try {
            int state = mContext.getPackageManager().getApplicationEnabledSetting("com.android.providers.downloads");
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 进入 启用/禁用 下载管理程序界面
     */
    public void skipToDownloadManager() {

        String packageName = "com.android.providers.downloads";
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + packageName));
        mContext.startActivity(intent);
    }


    /**
     * 获取APK程序信息
     *
     * @param context
     * @return
     */
    public static PackageInfo getApkInfo(Context context) {
        PackageManager pm = context.getPackageManager();
        PackageInfo pi = pm.getPackageArchiveInfo(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) + File.separator + APP_NAME, PackageManager.GET_ACTIVITIES);
        if (null != pi) {
            return pi;
        }
        return null;
    }

    /**
     * 比较两个APK的包名和版本号信息
     *
     * @param apkInfo 下载路径中apk的包信息
     * @param context
     * @return true 表示本地路径中的apk是新版本
     */
    public static boolean compareApkInfo(PackageInfo apkInfo, Context context) {
        if (null == apkInfo) {
            return false;
        }
        String localPackageName = context.getPackageName();
        if (localPackageName.equals(apkInfo.packageName)) {
//        if ("com.joyou.smartcity".equals(apkInfo.packageName)) {
            try {
                PackageInfo packageInfo = context.getPackageManager().getPackageInfo(localPackageName, 0);
                //比较当前APK和下载的APK版本号
                if (apkInfo.versionCode > packageInfo.versionCode) {
                    //如果下载的APK版本号大于当前安装的APK版本号，返回true
                    return true;
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        return false;
    }


    /**
     * @param context
     * @param intent
     */
    public void installApk(Context context, Intent intent) {
        long completeDownLoadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

//        LogUtils.e("收到广播");

        Intent intentInstall = new Intent();
        intentInstall.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intentInstall.setAction(Intent.ACTION_VIEW);

        DownLoadUtils downLoadUtils = DownLoadUtils.getInstance(context);
        Uri uri = downLoadUtils.getDownloadUri(completeDownLoadId);

//        if (completeDownLoadId == mReqId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // Android 7.0 以上
            intentInstall.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }

        // 安装应用
//        LogUtils.e("下载完成了");

        intentInstall.setDataAndType(uri, "application/vnd.android.package-archive");
        context.startActivity(intentInstall);
//        }
    }


    /**
     * 在app启动后判断是否需要删除存在的apk文件
     * 还有清理缓存中
     *
     * @return
     */
    public boolean deleteAPk() {
        File file = new File(mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APP_NAME);
        if (file != null) {
            return file.delete();
        }
        return false;
    }
}
