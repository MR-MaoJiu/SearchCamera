package club.yuanwanji.searchcamera;

import android.content.DialogInterface;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.otaliastudios.cameraview.CameraView;
import java.io.IOException;
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
import android.widget.Toast;


public class MainActivity extends AppCompatActivity implements SensorEventListener{
    CirCleProgressBar cirCleProgressBar;
    MediaPlayer mediaPlayer = new MediaPlayer();//这个我定义了一个成员函数
    private CameraView cameraView;
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
                    System.out.println(ping);
                    String currnetIp = mLocAddress + lastAddress;
                    if (mDevAddress.equals(currnetIp)) // 如果与本机IP地址相同,跳过
                        return;

                    try {
                        mProcess = mRun.exec(ping);

                        int result = mProcess.waitFor();
                        //Log.e(TAG, "正在扫描的IP地址为：" + currnetIp + "返回值为：" + result);
                        if (result == 0) {
                            Log.e(TAG, "扫描成功,Ip地址为：" + currnetIp);
                            mIpList.add(currnetIp);
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

        while (true) {
            try {
                if (mExecutor.isTerminated()) {// 扫描结束,开始验证
                    Log.e(TAG, "扫描结束,总共成功扫描到" + mIpList.size() + "个设备.");
                   // Log.e("设备列表："+new Gson().toJson(mIpList));
                    return mIpList;
                }
            } catch (Exception e) {
                // TODO: handle exception
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
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
                                "此时我们需要注意查找，当然本应用查找结果仅供参考。")//内容
                        .setIcon(R.mipmap.logo)//图标
                        .setNegativeButton("我已阅读并同意", new DialogInterface.OnClickListener() {//添加取消
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //Toast.makeText(MainActivity.this, "这是取消按钮", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .create();
                alertDialog1.show();
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
}
