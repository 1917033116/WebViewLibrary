package com.webviewlib;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.DownloadListener;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.webviewlibrary.R;
import com.liulishuo.okdownload.DownloadTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static android.os.Build.VERSION.SDK_INT;

public class EasonWebActivity extends Activity implements EasonWebChromeClient.OpenFileChooserCallBack {
    private File mSourceFile;
    private ValueCallback<Uri> mUploadMsg;
    private ValueCallback<Uri[]> mUploadMsgForAndroid5;
    private String mHomeUrl = "";
    private boolean mNeedClearHistory = false;
    private WebView webView;
    private ProgressBar progressBar;
    private ImageView ivHome;
    private ImageView ivBack;
    private ConstraintLayout mainRoot;
    private ImageView ivForward;
    private ImageView ivRefresh;
    private ImageView ivShare;
    private LinearLayout llBottomContainer;
    public static final String EXTRA_DATA = "extra_url";
    private final int REQUEST_CODE_PICK_IMAGE = 0;
    private final int REQUEST_CODE_IMAGE_CAPTURE = 1;
    private final int P_CODE_PERMISSIONS = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_eason_web);
        initUI();
        webView.setWebViewClient(
                new WebViewClient() {
                    @Override
                    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                        super.onReceivedSslError(view, handler, error);
                        handler.proceed();
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        try {
                            String tempUrl = url;
                            if (tempUrl.startsWith("weixin") || tempUrl.startsWith("ali") ||
                                    tempUrl.startsWith("pay://") || tempUrl.startsWith("mqqapi://") || tempUrl.startsWith("tel://")) {
                                if (tempUrl.startsWith("pay://")) {
                                    tempUrl = tempUrl.replace("pay://", "https://");
                                }
                                //其他自定义的scheme.
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(tempUrl));
                                startActivity(intent);
                                return true;
                            }//其他自定义的scheme
                        } catch (Exception e) {
                            //防止crash (如果手机上没有安装处理某个scheme开头的url的APP, 会导致crash)
                            return false;
                        }

                        //处理跳转appStore
                        if (Uri.parse(url).getHost().contains("itunes.apple")) {
                            Toast.makeText(EasonWebActivity.this, "请选择安卓下载", Toast.LENGTH_SHORT).show();
                            return true;
                        }

                        //处理http和https开头的url
                        if (url.startsWith("http:") || url.startsWith("https:")) {
                            view.loadUrl(url);
                        }
                        return true;

                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        if (SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                            CookieSyncManager.getInstance().sync();
                        } else {
                            CookieManager.getInstance().flush();
                        }
                    }

                    @Override
                    public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                        super.doUpdateVisitedHistory(view, url, isReload);
                        if (mNeedClearHistory) {
                            mNeedClearHistory = false;
                            view.clearHistory();//清除历史记录
                        }
                        resetBoomBtnState();

                    }
                }
        );
        webView.setWebChromeClient(new EasonWebChromeClient(this) {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                }
                progressBar.setProgress(newProgress);
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                if (message == null) return super.onJsAlert(view, url, message, result);
                if (message.startsWith("share:")) {
                    //友盟分享
                    //showShareDialog(message.substring(6))
                    //原生分享
                    doShare(message.substring(6));
                    result.confirm();//这里必须调用，否则页面会阻塞造成假死
                    return true;
                }
                return super.onJsAlert(view, url, message, result);
            }
        });
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                //权限判断
                if (PermissionUtil.isOverMarshmallow()) {
                    if (!PermissionUtil.isPermissionValid(EasonWebActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        requestPermissionsAndroidM();
                        return;
                    }
                }
                startToDown(url);//创建下载任务,downloadUrl就是下载链接
            }
        });
        fixDirPath();
        mHomeUrl = getIntent().getStringExtra(EXTRA_DATA);
        webView.loadUrl(mHomeUrl);
    }

    private void startToDown(String url) {
        NumProgressDialog dialog = new NumProgressDialog(this);
        DownloadTask task = new DownloadTask.Builder(url, getCacheDir())
                .setFilename(System.currentTimeMillis() + getUrlName(url))
                .setMinIntervalMillisCallbackProcess(30)
                .setPassIfAlreadyCompleted(false)
                .build();
        task.enqueue(new ApkDownLoadListener(this, dialog));
    }

    /**
     * 读取baseurl
     */
    private String getUrlName(String url) {
        String murl = url;
        int index = murl.lastIndexOf("/");
        if (index != -1) {
            murl = murl.substring(index + 1, murl.length());
        }
        return murl;
    }

    /**
     * 初始化
     */
    private void initUI() {
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        ivHome = findViewById(R.id.ivHome);
        ivBack = findViewById(R.id.ivBack);
        mainRoot = findViewById(R.id.mainRoot);
        ivForward = findViewById(R.id.ivForward);
        ivRefresh = findViewById(R.id.ivRefresh);
        ivShare = findViewById(R.id.ivShare);
        llBottomContainer = findViewById(R.id.llBottomContainer);
        addLayoutListener(mainRoot);
        ivHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mNeedClearHistory = true;
                webView.loadUrl(mHomeUrl);
            }
        });
        ivBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webView.goBack();
            }
        });
        ivForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webView.goForward();
            }
        });
        ivRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webView.reload();
            }
        });
        ivShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getShareData();
            }
        });

        resetBoomBtnState();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setDomStorageEnabled(true);
        settings.setBuiltInZoomControls(false);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) {
            if (mUploadMsg != null) {
                mUploadMsg.onReceiveValue(null);
            }

            if (mUploadMsgForAndroid5 != null) {         // for android 5.0+
                mUploadMsgForAndroid5.onReceiveValue(null);
            }
            return;
        }
        switch (requestCode) {
            case REQUEST_CODE_IMAGE_CAPTURE:
            case REQUEST_CODE_PICK_IMAGE:
                try {
                    if (SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        if (mUploadMsg == null) return;

                        String sourcePath = retrievePath(this, mSourceFile, data);
                        if (TextUtils.isEmpty(sourcePath) || !new File(sourcePath).exists()) {
                            return;
                        }
                        Uri uri = Uri.fromFile(new File(sourcePath));
                        mUploadMsg.onReceiveValue(uri);
                    } else if (SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        if (mUploadMsgForAndroid5 == null) {        // for android 5.0+
                            return;
                        }
                        String sourcePath = retrievePath(this, mSourceFile, data);

                        if (TextUtils.isEmpty(sourcePath) || !new File(sourcePath).exists()) {
                            return;
                        }
                        Uri uri = Uri.fromFile(new File(sourcePath));
                        Uri uris[] = {uri};
                        mUploadMsgForAndroid5.onReceiveValue(uris);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;

        }
    }


    @Override
    public void openFileChooserCallBack(ValueCallback<Uri> uploadMsg, String acceptType) {
        mUploadMsg = uploadMsg;
        showOptions();
    }

    @Override
    public boolean openFileChooserCallBackAndroid5(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
        mUploadMsgForAndroid5 = filePathCallback;
        showOptions();
        return true;
    }

    private void showOptions() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setOnCancelListener(new DialogOnCancelListener());

        alertDialog.setTitle("请选择操作");
        String options[] = {"相册", "拍照"};

        alertDialog.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    if (PermissionUtil.isOverMarshmallow()) {
                        if (!PermissionUtil.isPermissionValid(EasonWebActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                            restoreUploadMsg();
                            requestPermissionsAndroidM();
                            return;
                        }
                    }

                    try {
                        Intent mSourceIntent = choosePicture();
                        startActivityForResult(mSourceIntent, REQUEST_CODE_PICK_IMAGE);
                    } catch (Exception e) {
                        e.printStackTrace();
                        restoreUploadMsg();
                    }
                } else {
                    if (PermissionUtil.isOverMarshmallow()) {
                        if (!PermissionUtil.isPermissionValid(EasonWebActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                            restoreUploadMsg();
                            requestPermissionsAndroidM();
                            return;
                        }

                        if (!PermissionUtil.isPermissionValid(EasonWebActivity.this, Manifest.permission.CAMERA)) {
                            restoreUploadMsg();
                            requestPermissionsAndroidM();
                            return;
                        }
                    }

                    try {

                        mSourceFile = new File(getNewPhotoPath());
                        Intent sourceIntent = takeBigPicture(EasonWebActivity.this, mSourceFile);
                        startActivityForResult(sourceIntent, REQUEST_CODE_IMAGE_CAPTURE);

                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(EasonWebActivity.this, "请去\"设置\"中开启本应用的存储访问权限", Toast.LENGTH_SHORT).show();
                        restoreUploadMsg();
                    }
                }
            }
        });

        alertDialog.show();
    }

    private void fixDirPath() {
        String path = getDirPath();
        File file = new File(path);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    class DialogOnCancelListener implements DialogInterface.OnCancelListener {

        @Override
        public void onCancel(DialogInterface dialog) {
            restoreUploadMsg();
        }
    }


    private void restoreUploadMsg() {
        if (mUploadMsg != null) {
            mUploadMsg.onReceiveValue(null);
            mUploadMsg = null;

        } else if (mUploadMsgForAndroid5 != null) {
            mUploadMsgForAndroid5.onReceiveValue(null);
            mUploadMsgForAndroid5 = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case P_CODE_PERMISSIONS:
                requestResult(permissions, grantResults);
                restoreUploadMsg();
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                break;
        }
    }

    private void requestPermissionsAndroidM() {
        if (!PermissionUtil.isOverMarshmallow()) return;
        List<String> needPermissionList = new ArrayList<>();
        needPermissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        needPermissionList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        needPermissionList.add(Manifest.permission.CAMERA);
        PermissionUtil.requestPermissions(this, P_CODE_PERMISSIONS, needPermissionList);
    }

    private void requestResult(String permissions[], int grantResults[]) {
        if (!PermissionUtil.isOverMarshmallow()) return;
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < grantResults.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                switch (permissions[i]) {
                    case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                        stringBuilder.append("写文件");
                        break;
                    case Manifest.permission.READ_EXTERNAL_STORAGE:
                        stringBuilder.append("读文件");
                        break;
                    case Manifest.permission.CAMERA:
                        stringBuilder.append("摄像头");
                        break;
                    default:
                        stringBuilder.append("");
                        break;
                }
            }
        }
        if (!TextUtils.isEmpty(stringBuilder)) {
            Toast.makeText(this, "请允许使用'"+stringBuilder+"'权限, 以正常使用APP的所有功能.", Toast.LENGTH_SHORT).show();
        }
    }

    public static void start(Context content, String it) {
        if (it == null || it.length() == 0) {
            return;
        }
        Intent intent = new Intent(content, EasonWebActivity.class);
        intent.putExtra(EXTRA_DATA, it);
        content.startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            new DialogCancelConfirm.Builder(this)
                    .setMessage("您确定退出吗？")
                    .setDoubleBtnClickListener(new OnDoubleBtnClickListener() {
                        @Override
                        public void onClick(DialogCancelConfirm dialog, boolean isLeft) {
                            dialog.dismiss();
                            if (!isLeft) finish();
                        }
                    }).build().show();
        }
    }

    /**
     * 重置底部返回和前进的状态
     */
    private void resetBoomBtnState() {
        ivBack.setEnabled(webView.canGoBack());
        ivForward.setEnabled(webView.canGoForward());
    }

    /**
     * addLayoutListener方法如下
     *
     * @param main 根布局
     */
    private void addLayoutListener(final View main) {
        main.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                Rect rect = new Rect();
                //1、获取main在窗体的可视区域
                main.getWindowVisibleDisplayFrame(rect);
                //2、获取main在窗体的不可视区域高度，在键盘没有弹起时，main.getRootView().getHeight()调节度应该和rect.bottom高度一样
                int mainInvisibleHeight = main.getRootView().getHeight() - rect.bottom;
                int screenHeight = main.getRootView().getHeight();//屏幕高度
                //3、不可见区域大于屏幕本身高度的1/4：说明键盘弹起了
                if (mainInvisibleHeight > screenHeight / 4) {
                    llBottomContainer.setVisibility(View.GONE);
                } else {
                    //3、不可见区域小于屏幕高度1/4时,说明键盘隐藏了，把界面下移，移回到原有高度
                    //main.scrollTo(0, 0)
                    llBottomContainer.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void getShareData() {
        /*HttpHelper.create(ToggleApi::class.java).getShareData()
            .compose(RxSchedulers.io_main())
            .subscribe({
                if (it.msg != null && StringUtils.isUrlLink(it.msg.links)) {
                    doShare(it.msg.links)
                } else {
                    "分享的链接错误".toast()
                }
            }, {
                loge(it.toString())
                "分享的链接错误".toast()
            })*/
    }

    /**
     * go for camera.
     */
    private Intent takeBigPicture(Context context, File cameraPhoto) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        /*获取当前系统的android版本号*/
        int currentVersion = SDK_INT;
        if (currentVersion < 24) {
            intent.putExtra(MediaStore.EXTRA_OUTPUT, newPictureUri(cameraPhoto));
        } else {
            ContentValues contentValues = new ContentValues(1);
            contentValues.put(MediaStore.Images.Media.DATA, cameraPhoto.getPath());
            Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);

            Uri photoUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", cameraPhoto);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        }
        return intent;
    }


    private Uri newPictureUri(File file) {
        return Uri.fromFile(file);
    }

    private String getDirPath() {
        return Environment.getExternalStorageDirectory().getPath() + "/UploadImage";
    }

    private String getNewPhotoPath() {
        return getDirPath() + "/" + System.currentTimeMillis() + ".jpg";
    }

    /**
     * go for Album.
     */
    private Intent choosePicture() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        return Intent.createChooser(intent, null);
    }

    private String retrievePath(Context context, File sourceFile, Intent dataIntent) {
        String picPath = null;
        try {
            Uri uri;
            if (dataIntent != null) {
                uri = dataIntent.getData();
                if (uri != null) {
                    picPath = getPath(context, uri);
                }
                return picPath;
            }

            if (sourceFile != null) {

                if (sourceFile.exists())
                    return sourceFile.getPath();


                /* if (uri != null) {
                    String scheme = uri.getScheme();
                    if (scheme != null && scheme.startsWith("file")) {
                        picPath = uri.getPath();
                    }
                }
                if (!TextUtils.isEmpty(picPath)) {
                    File file = new File(picPath);
                    if (!file.exists() || !file.isFile()) {
                        Log.w(TAG, String.format("retrievePath file not found from sourceIntent path:%s", picPath));
                    }
                }*/
            }
            return picPath;
        } catch (Exception e) {
            e.printStackTrace();
            return picPath;
        } finally {
        }
    }

    @SuppressLint("NewApi")
    private String getPath(Context context, Uri uri) {

        boolean isKitKat = SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                String docId = DocumentsContract.getDocumentId(uri);
                String split[] = docId.split(":");
                List<String> spList = new ArrayList<>(Arrays.asList(split));
                Iterator<String> it = spList.iterator();
                while (it.hasNext()) {
                    String s = it.next();
                    if (TextUtils.isEmpty(s)) {
                        it.remove();
                    }
                }
                String type = spList.get(0);

                if ("primary".equals(type)) {
                    return String.format("%s/%s", Environment.getExternalStorageDirectory().getPath(), split[1]);
                }
            } else if (isDownloadsDocument(uri)) {
                String id = DocumentsContract.getDocumentId(uri);
                Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                return getDataColumn(context, contentUri, null, null);
            } else if (isMediaDocument(uri)) {
                String docId = DocumentsContract.getDocumentId(uri);
                String split[] = docId.split(":");
                List<String> spList = new ArrayList<>(Arrays.asList(split));
                Iterator<String> it = spList.iterator();
                while (it.hasNext()) {
                    String s = it.next();
                    if (TextUtils.isEmpty(s)) {
                        it.remove();
                    }
                }
                String type = spList.get(0);

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                String selection = "_id=?";
                String selectionArgs[] = {spList.get(1)};
                return getDataColumn(context, contentUri, selection, selectionArgs);
            }// MediaProvider
            // DownloadsProvider
        } else if ("content".equals(uri.getScheme())) {

            // Return the remote address
            if (isGooglePhotosUri(uri)) {
                return uri.getLastPathSegment();
            } else return getDataColumn(context, uri, null, null);

        } else if ("file".equals(uri.getScheme())) {
            return uri.getPath();
        }// File
        // MediaStore (and general)

        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    private Boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    private Boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    private Boolean isDownloadsDocument(Uri uri)

    {
        return "com.android.providers.downloads.documents" == uri.getAuthority();
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    private Boolean isExternalStorageDocument(Uri uri)

    {
        return "com.android.externalstorage.documents" == uri.getAuthority();
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically ic_a file path.
     */
    private String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, new String[]{MediaStore.Images.Media.DATA}, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    // 系统自带的分享
    private void doShare(String message) {
        Intent intentShare =new  Intent();
        intentShare.setAction(Intent.ACTION_SEND);//设置分享行为
        intentShare.setType("text/plain");//设置分享内容的类型
        intentShare.putExtra(Intent.EXTRA_TEXT, message);//添加分享内容
        startActivity(Intent.createChooser(intentShare, "分享到..."));
    }
}


//-------------------------友盟分享--------------------------------------
//显示分享的窗口
/*private fun showShareDialog(link: String) {
    val dialogShare = DialogShare(this)
    dialogShare.setOnShareClickListener {
        val umWeb = getUMWeb(link)
        when (it) {
            1 -> ShareAction(this)
                .setPlatform(SHARE_MEDIA.WEIXIN)
                .withMedia(umWeb)
                .setCallback(umShareListener)
                .share()
            2 -> ShareAction(this)
                .setPlatform(SHARE_MEDIA.WEIXIN_CIRCLE)
                .withMedia(umWeb)
                .setCallback(umShareListener)
                .share()
            3 -> ShareAction(this)
                .setPlatform(SHARE_MEDIA.QQ)
                .withMedia(umWeb)
                .setCallback(umShareListener)
                .share()
        }
        dialogShare.dismiss()
    }
    dialogShare.show()
}

private fun getUMWeb(link: String): UMWeb {
    val bean = ShareBean(mData)
    val image = if (bean.localPath.isNotEmpty() && File(bean.localPath).exists())
        UMImage(this, BitmapFactory.decodeFile(bean.localPath))
    else UMImage(this, R.mipmap.ic_launcher)
    return UMWeb(link, bean.title, bean.description, image)
}

//分享回调
private val umShareListener = object : UMShareListener {
    override fun onResult(platform: SHARE_MEDIA?) {
        LogUtils.d("platform:${platform.toString()}")
        toast("分享成功")
        when {
            platform.toString() == "QQ" -> UIUtils.showToast("手机QQ分享成功")
            platform.toString() == "QZONE" -> UIUtils.showToast("QQ空间分享成功")
            platform.toString() == "WEIXIN" -> UIUtils.showToast("微信好友分享成功")
            platform.toString() == "WEIXIN_CIRCLE" -> UIUtils.showToast("微信朋友圈分享成功")
        }
    }

    override fun onCancel(platform: SHARE_MEDIA?) {
        toast("分享取消")
    }

    override fun onError(platform: SHARE_MEDIA?, t: Throwable?) {
        toast("分享失败${t?.message}")
    }

    override fun onStart(platform: SHARE_MEDIA?) {
        LogUtils.d("platform:${platform.toString()}")
    }
}

public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
       UMShareAPI.get(this).onActivityResult(requestCode, resultCode, data)
}*/
