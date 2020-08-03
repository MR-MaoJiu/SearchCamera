package club.yuanwanji.searchcamera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import com.otaliastudios.cameraview.CameraView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import org.json.JSONException;
import org.json.JSONObject;
import java.net.MalformedURLException;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface.OnClickListener;

public class MainActivity extends AppCompatActivity implements SensorEventListener{
    CirCleProgressBar cirCleProgressBar;
    MediaPlayer mediaPlayer = new MediaPlayer();//这个我定义了一个成员函数
    Socket socket = null;
    private CameraView cameraView;
    // 支付宝包名
    private static final String alipay_package_name = "com.eg.android.alipaygphone";
    //匹配C类地址的IP
    public static final String regexCIp = "^192\\.168\\.(\\d{1}|[1-9]\\d|1\\d{2}|2[0-4]\\d|25\\d)\\.(\\d{1}|[1-9]\\d|1\\d{2}|2[0-4]\\d|25\\d)$";
    //匹配A类地址
    public static final String regexAIp = "^10\\.(\\d{1}|[1-9]\\d|1\\d{2}|2[0-4]\\d|25\\d)\\.(\\d{1}|[1-9]\\d|1\\d{2}|2[0-4]\\d|25\\d)\\.(\\d{1}|[1-9]\\d|1\\d{2}|2[0-4]\\d|25\\d)$";
    //匹配B类地址
    public static final String regexBIp = "^172\\.(1[6-9]|2\\d|3[0-1])\\.(\\d{1}|[1-9]\\d|1\\d{2}|2[0-4]\\d|25\\d)\\.(\\d{1}|[1-9]\\d|1\\d{2}|2[0-4]\\d|25\\d)$";
    private static final String TAG = MainActivity.class.getSimpleName();
    /** 核心池大小 **/
    private static final int CORE_POOL_SIZE = 1;
    /** 线程池最大线程数 **/
    private static final int MAX_IMUM_POOL_SIZE = 255;
    private String mDevAddress;// 本机IP地址-完整
    private String mLocAddress;// 局域网IP地址头,如：192.168.1.
    private Runtime mRun = Runtime.getRuntime();// 获取当前运行环境，来执行ping，相当于windows的cmd
    private Process mProcess = null;// 进程
    private String mPing = "ping -c 1 -w 5 ";// 其中 -c 1为发送的次数，-w 表示发送后等待响应的时间
    private List<String> mIpList = new ArrayList<String>();// ping成功的IP地址
    private ThreadPoolExecutor mExecutor;// 线程池对象
    String ipstr;
    int port=81;
    String serveraddress="app.yuanwanji.club";
    String body="软件更新";
    String appurl="";
    // 上下文对象
    private Context mContext;
    // 下载进度条
    private ProgressBar progressBar ;
    // 是否终止下载
    private boolean isInterceptDownload = false;
    //进度条显示数值
    AlertDialog alertDialog3;
    private int progress = 0;
    String code;
    private static final String savePath = Environment.getExternalStorageDirectory().getPath();
    private static final String saveFileName = savePath + "/摄像头探测器.apk";
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    /**
     * 在对sd卡进行读写操作之前调用这个方法
     * Checks if the app has permission to write to device storage
     * If the app does not has permission then the user will be prompted to grant permissions
     */
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
        }
    }


    /**
     * TODO<扫描局域网内ip，找到对应服务器>
     *
     * @return void
     */
    public List<String> scan() {
        mDevAddress = getHostIp();// 获取本机IP地址
        mLocAddress = getLocAddrIndex(mDevAddress);// 获取本地ip前缀
        Log.e(TAG, "开始扫描设备,本机Ip为：" + mDevAddress);

        if (TextUtils.isEmpty(mLocAddress)) {
            Log.e(TAG, "扫描失败，请检查wifi网络");
            return null;
        }

        /**
         * 1.核心池大小 2.线程池最大线程数 3.表示线程没有任务执行时最多保持多久时间会终止
         * 4.参数keepAliveTime的时间单位，有7种取值,当前为毫秒
         * 5.一个阻塞队列，用来存储等待执行的任务，这个参数的选择也很重要，会对线程池的运行过程产生重大影响
         * ，一般来说，这里的阻塞队列有以下几种选择：
         */
        mExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_IMUM_POOL_SIZE,
                2000, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(
                CORE_POOL_SIZE));

        // 新建线程池
        for (int i = 1; i < 255; i++) {// 创建256个线程分别去ping
            final int lastAddress = i;// 存放ip最后一位地址 1-255

            Runnable run = new Runnable() {

                @Override
                public void run() {
                    // TODO Auto-generated method stub
                    String ping = MainActivity.this.mPing + mLocAddress
                            + lastAddress;
                    String currnetIp = mLocAddress + lastAddress;
                    if (mDevAddress.equals(currnetIp)) // 如果与本机IP地址相同,跳过
                        return;

                    try {
                        mProcess = mRun.exec(ping);

                        int result = mProcess.waitFor();
                        //Log.e(TAG, "正在扫描的IP地址为：" + currnetIp + "返回值为：" + result);
                        if (result == 0) {
                            Log.e(TAG, "扫描成功,Ip地址为：" + currnetIp);

                            socket = new Socket(currnetIp, port);
                            boolean isConnected = socket.isConnected() && !socket.isClosed();
                            if (isConnected) {
                                mIpList.add(currnetIp);
                            }
                            //关闭Socket
                            socket.close();

                        } else {
                            // 扫描失败
                            //Log.e(TAG, "扫描失败");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "扫描异常" + e.toString());
                    } finally {
                        if (mProcess != null)
                            mProcess.destroy();
                    }
                }
            };

            mExecutor.execute(run);
        }
        mExecutor.shutdown();
                    return mIpList;
    }
    /**
     * 弹出对话框
     */
    protected void showUpdataDialog() {
        AlertDialog.Builder builer = new AlertDialog.Builder(this) ;
        builer.setTitle("版本升级");
        builer.setMessage(body);

        //当点确定按钮时从服务器上下载 新的apk 然后安装
        builer.setPositiveButton("确定", (dialog, which) -> downloadApk());
        //当点取消按钮时不做任何举动
        builer.setNegativeButton("取消", (dialogInterface, i) -> {});
        AlertDialog dialog = builer.create();
        dialog.show();
    }
    /**
     * 下载apk
     */
    @SuppressLint("WrongConstant")
    private void downloadApk(){
        progressBar=new ProgressBar(this,null, android.R.attr.progressBarStyleHorizontal);

         alertDialog3 = new AlertDialog.Builder(MainActivity.this)
                .setTitle("更新中：")//标题
                .setView(progressBar)
                .setIcon(R.mipmap.logo)//图标
                .create();

        alertDialog3.show();
        //开启另一线程下载
        Thread downLoadThread = new Thread(downApkRunnable);
        downLoadThread.start();
    }
    /**
     * 从服务器下载新版apk的线程
     */
    private Runnable downApkRunnable = new Runnable(){
        @Override
        public void run() {
            String path = android.os.Environment.getExternalStorageState();
            System.out.println(path);
            if (!android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
                //如果没有SD卡
                Builder builder = new Builder(mContext);
                builder.setTitle("提示");
                builder.setMessage("当前设备无SD卡，数据无法下载");
                builder.setPositiveButton("确定", new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                builder.show();
                return;
            }else if(appurl != null){
                try {
                    //服务器上新版apk地址
                    URL url = new URL(appurl);
                    HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                    conn.connect();
                    int length = conn.getContentLength();
                    InputStream is = conn.getInputStream();

                    File file = new File(savePath);
                    if (!file.exists()) {
                        file.mkdirs();
                    }
                    String apkFile = saveFileName;
                    File ApkFile = new File(apkFile);
                    FileOutputStream fos = new FileOutputStream(ApkFile);
                    int count = 0;
                    byte buf[] = new byte[1024];

                    do{
                        int numRead = is.read(buf);
                        count += numRead;
                        //更新进度条
                        progress = (int) (((float) count / length) * 100);
                        handler.sendEmptyMessage(1);
                        if(numRead <= 0){
                            //下载完成通知安装
                            handler.sendEmptyMessage(0);
                            isInterceptDownload = true;
                            alertDialog3.dismiss();
                            break;
                        }
                        fos.write(buf,0,numRead);
                        //当点击取消时，则停止下载
                    }while(!isInterceptDownload);
                    fos.close();
                    is.close();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }else{
                Builder builder = new Builder(mContext);
                builder.setTitle("提示");
                builder.setMessage("获取服务器版本信息错误，数据无法下载");
                builder.setPositiveButton("确定", new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                builder.show();
            }
        }
    };
    /**
     * 声明一个handler来跟进进度条
     */
    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    // 更新进度情况
                    progressBar.setProgress(progress);
                    break;
                case 0:
                    progressBar.setVisibility(View.INVISIBLE);
                    // 安装apk文件
                    installApk();
                    break;
                default:
                    break;
            }
        };
    };
    /**
     * 安装apk
     */
    private void installApk() {
        Log.i(TAG, "开始执行安装: " + saveFileName);
        File apkFile = new File(saveFileName);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Log.w(TAG, "版本大于 N ，开始使用 fileProvider 进行安装");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri contentUri = FileProvider.getUriForFile(
                    mContext
                    , "club.yuanwanji.searchcamera"
                    , apkFile);
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
        } else {
            Log.w(TAG, "正常进行安装");
            intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
        }
        startActivity(intent);
    }
    public String getVersionName() throws Exception
    {
        // 获取packagemanager的实例
        PackageManager packageManager = getPackageManager();
        // getPackageName()是你当前类的包名，0代表是获取版本信息
        PackageInfo packInfo = packageManager.getPackageInfo(getPackageName(),0);
        String version = packInfo.versionName;
        return version;
    }

    /**
     * 获取最新版本信息
     * @return
     * @throws IOException
     * @throws JSONException
     */
    public void Update(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //你的URL
                    String url_s = "http://"+serveraddress+"/version.txt";
                    URL url = new URL(url_s);
                    HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                    //设置连接属性。不喜欢的话直接默认也阔以
                    conn.setConnectTimeout(5000);//设置超时
                    conn.setUseCaches(false);//数据不多不用缓存了

                    //这里连接了
                    conn.connect();
                    //这里才真正获取到了数据
                    InputStream inputStream = conn.getInputStream();
                    InputStreamReader input = new InputStreamReader(inputStream);
                    BufferedReader buffer = new BufferedReader(input);
                    if(conn.getResponseCode() == 200){//200意味着返回的是"OK"
                        String inputLine;
                        StringBuffer resultData  = new StringBuffer();//StringBuffer字符串拼接很快
                        while((inputLine = buffer.readLine())!= null){
                            resultData.append(inputLine);
                        }
                        String json = resultData.toString();
                        JSONObject jsonObject = new JSONObject(json);
                        code = jsonObject.getString("code");
                        appurl=jsonObject.getString("url");
                        body=jsonObject.getString("update");
//                        if(!code.trim().equals(getVersionName().trim()))
//                        {
//                            Log.e("VVV",code+"   "+getVersionName());
//                            showUpdataDialog();
//                        }
                    }
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * TODO<销毁正在执行的线程池>
     *
     * @return void
     */
    public void destory() {
        if (mExecutor != null) {
            mExecutor.shutdownNow();
        }
    }

    /**
     * TODO<获取本地ip地址>
     *
     * @return String
     */
    public static String getHostIp() {
        String hostIp;
        Pattern ip = Pattern.compile("(" + regexAIp + ")|" + "(" + regexBIp + ")|" + "(" + regexCIp + ")");
        Enumeration<NetworkInterface> networkInterfaces = null;
        try {
            networkInterfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        InetAddress address;
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfaces.nextElement();
            Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                address = inetAddresses.nextElement();
                String hostAddress = address.getHostAddress();
                Matcher matcher = ip.matcher(hostAddress);
                if (matcher.matches()) {
                    hostIp = hostAddress;
                    return hostIp;
                }

            }
        }
        return null;
    }

    /**
     * TODO<获取本机IP前缀>
     *
     * @param devAddress
     *            // 本机IP地址
     * @return String
     */
    private String getLocAddrIndex(String devAddress) {
        if (!devAddress.equals("")) {
            return devAddress.substring(0, devAddress.lastIndexOf(".") + 1);
        }
        return null;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        cirCleProgressBar=findViewById(R.id.ccb);
       Button btn=findViewById(R.id.btn);
        cameraView = findViewById(R.id.camera);
        Update();
        verifyStoragePermissions(MainActivity.this);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIpList.clear();
                ipstr="";
                scan();
                for(int i=0;i< mIpList.size();i++)
                {
                    ipstr+=mIpList.get(i)+"\n";
                    //Toast.makeText(MainActivity.this,mIpList.get(i),Toast.LENGTH_SHORT).show();
                }
                if(mIpList.size()>0)
                {
                    AlertDialog alertDialog1 = new AlertDialog.Builder(MainActivity.this)
                            .setTitle("探测到疑似摄像头IP列表：")//标题
                            .setMessage(ipstr)//内容
                            .setIcon(R.mipmap.logo)//图标
                            .setNegativeButton("确定", new DialogInterface.OnClickListener() {//添加取消
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    Toast.makeText(MainActivity.this, "注意保护隐私哦！！！", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .create();
                    alertDialog1.show();
                }
                else {
                    Toast.makeText(MainActivity.this, "未找到可疑摄像头，注意保护隐私哦！！！", Toast.LENGTH_SHORT).show();
                }

            }
        });
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        // 获得SensorManager对象
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        // 注册磁场传感器
        sensorManager.registerListener((SensorEventListener) this,
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_FASTEST);
        AssetFileDescriptor file =this.getResources().openRawResourceFd(

                R.raw.beep);

        try{

            mediaPlayer.setDataSource(file.getFileDescriptor(),

                    file.getStartOffset(), file.getLength());

            file.close();


            mediaPlayer.prepare();

        }catch (IOException ioe) {

            mediaPlayer = null;

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item){
         final EditText et = new EditText(this);//端口输入框
        switch (item.getItemId()) {
            case R.id.action_cart://监听菜单按钮
                AlertDialog alertDialog1 = new AlertDialog.Builder(this)
                        .setTitle("温馨提示：")//标题
                        .setMessage("本应用为您提供三种探测方式：\n" +
                                "1.红外探测，我们都知道部分针孔摄像头为了增加夜视功能" +
                                "会增加红外，我们通过摄像头扫描（关灯最好）一些有可能藏匿摄像头的地方" +
                                "（对着床，淋浴间，马桶等位置），如果发现红点则很有可能是摄像头\n" +
                                "2.使用磁场传感器，将手机贴近容易藏匿摄像头的地方如果震动则说明有磁场干扰" +
                                "（当然金属或者电子设备都会干扰），如果报警且震动则说明干扰增强，需要我们注意！\n" +
                                "3.扫描网络设备，连接酒店Wi-Fi点击按钮进行扫描（手机网络桥接或者热点需要关闭），本应用将会扫描每个连接Wi-Fi的设备" +
                                "如果发现有设备开放了81等摄像头常用端口的设备，将会显示在扫描列表里。" +
                                "此时我们需要注意查找，当然本应用查找结果仅供参考。本应用承诺开源且无广告如果对您有用请打赏一下吧！！！打赏的钱将用来维护应用和服务器支出！！！\n" +
                                "4.所有打赏的人将有希望获得最新版应用哦！")//内容
                        .setIcon(R.mipmap.logo)//图标
                        .setNegativeButton("去打赏", new DialogInterface.OnClickListener() {//添加取消
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if(AlipayUtil.hasInstalledAlipayClient(MainActivity.this)){
                                   boolean flg= AlipayUtil.startAlipayClient(MainActivity.this, "FKX03798GCD9KN1C5ND28F");  //第二个参数代表要给被支付的二维码code  可以在用草料二维码在线生成
                                    if(flg)
                                    {
                                        Toast.makeText(MainActivity.this, "感谢您的支持我将会加倍努力！！！", Toast.LENGTH_SHORT).show();
                                    }
                                }else{
                                    Toast.makeText(MainActivity.this, "没有检测到支付宝客户端", Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setPositiveButton("去分享", new DialogInterface.OnClickListener() {//添加取消
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Intent share_intent = new Intent();

                                share_intent.setAction(Intent.ACTION_SEND);

                                share_intent.setType("text/plain");

                                share_intent.putExtra(Intent.EXTRA_SUBJECT, "f分享");

                                share_intent.putExtra(Intent.EXTRA_TEXT, "Hi~最近发现一款很实用的APP：摄像头探测器\n这是一款免费的应用而且无广告哦！快去下载吧：http://app.yuanwanji.club" );

                                share_intent = Intent.createChooser(share_intent, "分享");

                                startActivity(share_intent);
                            }
                        })
                        .create();
                alertDialog1.show();
                break;
            case R.id.action_setting:
                AlertDialog alertDialog2 = new AlertDialog.Builder(this)
                        .setTitle("设置：")//标题
                        .setMessage("这里可以设置您要扫描的端口一般为81端口，推荐设置81、80、888：\n"
                               )//内容
                        .setIcon(R.mipmap.logo)//图标
                       .setView(et)
                        .setPositiveButton("设置", new DialogInterface.OnClickListener() {//添加取消
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                try {
                                   port= Integer.parseInt(et.getText().toString());
                                    Toast.makeText(MainActivity.this,"设置成功当前扫描端口为："+port,Toast.LENGTH_SHORT).show();
                                } catch (NumberFormatException e) {
                                    Toast.makeText(MainActivity.this,"请输入数字",Toast.LENGTH_SHORT).show();
                                }

                            }
                        })
                        .create();
                alertDialog2.show();
                break;
            case R.id.action_update:
                try {
                    if(!code.trim().equals(getVersionName().trim()))
                    {
                        Log.e("VVV",code+"   "+getVersionName());
                        showUpdataDialog();
                    }else {
                        Toast.makeText(MainActivity.this,"当前已是最新版本",Toast.LENGTH_SHORT).show();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

                break;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    protected void onResume() {
        super.onResume();
        cameraView.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraView.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraView.destroy();
    }
    private int count = 1;
    @Override
    public void onSensorChanged(SensorEvent event) {
        if( count++ == 20){ //磁场传感器很敏感，每20个变化，显示一次数值
         double value = Math.sqrt(event.values[0]*event.values[0]
             + event.values[1]*event.values[1]+event.values[2]*event.values[2]);
             //String str = String.format("X:%8.4f , Y:%8.4f , Z:%8.4f \n总值为：%8.4f", event.values[0],event.values[1],event.values[2],value);
            count = 1;
            cirCleProgressBar.setCurrentProgress((float)value);
            cirCleProgressBar.setText(true, (int)value+"ut");
            if((int)value>70&&(int)value<300)
            {
                cirCleProgressBar.setCircleColor(Color.rgb(255,193,7));
                cirCleProgressBar.setTextColor(Color.rgb(255,193,7));
                Vibrator vibrator = (Vibrator)this.getSystemService(this.VIBRATOR_SERVICE);
                vibrator.vibrate(300);
            }else if((int)value>300)
            {
                cirCleProgressBar.setCircleColor(Color.rgb(216,27,96));
                cirCleProgressBar.setTextColor(Color.rgb(216,27,96));
                Vibrator vibrator = (Vibrator)this.getSystemService(this.VIBRATOR_SERVICE);
                vibrator.vibrate(300);
                mediaPlayer.start();
            }else
            {
                cirCleProgressBar.setCircleColor(Color.rgb(3,169,244));
                cirCleProgressBar.setTextColor(Color.rgb(3,169,244));
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    //声明一个long类型变量：用于存放上一点击“返回键”的时刻
    private long mExitTime;
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //判断用户是否点击了“返回键”
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            //与上次点击返回键时刻作差
            if ((System.currentTimeMillis() - mExitTime) > 2000) {
                //大于2000ms则认为是误操作，使用Toast进行提示
                Toast.makeText(this, "再按一次退出程序", Toast.LENGTH_SHORT).show();
                //并记录下本次点击“返回键”的时刻，以便下次进行判断
                mExitTime = System.currentTimeMillis();
            } else {
                //小于2000ms则认为是用户确实希望退出程序-调用System.exit()方法进行退出
                System.exit(0);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
