package com.beibei.appupdate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

/**
 * project_name:   init
 * package_name:   com.dashen.init.common.network.appUpdate
 * author:   beibei
 * create_time:    2018/10/17 10:52
 * class_desc:
 * remarks:
 * <p>
 * 下载完成后提示跳转更新安装页面
 */

class DownloadReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        boolean haveInstallPermission;
        final DownLoadUtils downLoadUtils = DownLoadUtils.getInstance(context);
        // 兼容Android 8.0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            //先获取是否有安装未知来源应用的权限
            haveInstallPermission = context.getPackageManager().canRequestPackageInstalls();
            if (!haveInstallPermission) {//没有权限
                // 弹窗，并去设置页面授权
                final AndroidOInstallPermissionListener listener = new AndroidOInstallPermissionListener() {
                    @Override
                    public void permissionSuccess() {
                        downLoadUtils.installApk(context, intent);
                    }

                    @Override
                    public void permissionFail() {
                        Toast.makeText(context, "授权失败，无法安装应用", Toast.LENGTH_SHORT);
                    }
                };

                AndroidOPermissionActivity.sListener = listener;
                Intent intent1 = new Intent(context, AndroidOPermissionActivity.class);
                context.startActivity(intent1);


            } else {
                downLoadUtils.installApk(context, intent);
            }
        } else {
            downLoadUtils.installApk(context, intent);
        }
    }

    public interface AndroidOInstallPermissionListener {
        void permissionSuccess();

        void permissionFail();
    }
}

