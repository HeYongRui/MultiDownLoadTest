package com.example.haiyuan1995.multidown;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private ProgressBar progressBar;
    private int length;
    private int total = 0;
    private TextView textView;
    private boolean downloading = false;
    private URL url;
    private Button btn_down;
    private File file;
    private List<Map<String, Integer>> threadlist;

    Handler h = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == 123) {
                progressBar.setProgress(msg.arg1);
//               百分比的显示
                float beifenbi = (float) total / (float) length;
                int haha = (int) (beifenbi * 100);
                textView.setText(haha + "%");
                if (haha == 100) {
                    Notification();
                    btn_down.setText("完成");
                    Toast.makeText(MainActivity.this, "下载完成！", Toast.LENGTH_SHORT).show();
                }
            }
            return false;
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final EditText edit_url = (EditText) findViewById(R.id.edit_url);
        btn_down = (Button) findViewById(R.id.btn_down);
        textView = (TextView) findViewById(R.id.textView);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        threadlist = new ArrayList<>();

        btn_down.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (downloading) {
                    downloading = false;
                    btn_down.setText("下载");
                    return;
                }
                downloading = true;
                btn_down.setText("暂停");
                //新建线程，进行下载操作耗时操作
                if (threadlist.size() == 0) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                url = new URL(edit_url.getText().toString());
                                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                                connection.setRequestMethod("GET");
                                connection.setConnectTimeout(5000);//设置连接超时
                                //获取文件长度
                                length = connection.getContentLength();
//                            connection.setRequestProperty("Range", "bytes=" + "-");
                                file = new File(Environment.getExternalStorageDirectory(), setName(edit_url.getText().toString()));
                                RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
                                randomAccessFile.setLength(length);

                                progressBar.setMax(length);
                                progressBar.setProgress(0);

                                int blocksize = length / 3;
//                           分成三段，定义每一段的起始位置，每一段新建线程，实现多线程下载
                                for (int i = 0; i < 3; i++) {
                                    int begin = i * blocksize;
                                    int end = (i + 1) * blocksize;
                                    if (i == 2) {
                                        end = length;
                                    }
                                    HashMap<String, Integer> map = new HashMap<String, Integer>();
                                    map.put("begin", begin);
                                    map.put("end", end);
                                    map.put("finished", 0);
                                    threadlist.add(map);

//                                新建线程实现下载（实现下面的内部类）
                                    Thread t = new Thread(new DownloadRunnable(begin, i, end, url, file));
                                    t.start();
                                }
                            } catch (MalformedURLException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                } else {
                    for (int i = 0; i < threadlist.size(); i++) {
                        HashMap<String, Integer> hashMap = (HashMap<String, Integer>) threadlist.get(i);
                        int begin = hashMap.get("begin");
                        int end = hashMap.get("end");
                        int finished = hashMap.get("finished");
                        Thread thread = new Thread(new DownloadRunnable(begin + finished, i, end, url, file));
                        thread.start();
                    }
                }
            }
        });
    }

    //  定义获取文件末尾名字的方法
    private String setName(String url) {
        int s = url.lastIndexOf("/");
        String name = url.substring(s);
        return name;
    }

    //    定义内部类
    class DownloadRunnable implements Runnable {

        //        虽然上面点击事件的线程中定义了begin.end,file,rand,RandomAccessFile等，但是此处
//        仍然定义，因为在多线程中，都去访问同样的参数可能会造成冲突，所以每个线程都要定义
        private int begin, end, id;
        private URL url;
        private File file;
        public DownloadRunnable(int begin, int id, int end, URL url, File file) {
            this.begin = begin;
            this.end = end;
            this.url = url;
            this.file = file;
            this.id = id;
        }

        @Override
        public void run() {
            try {
//                如果开始大于结尾，说明此段已经下载完成，不需要下载了，直接返回
                if (begin > end) {
                    return;
                }

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setRequestProperty("Range", "bytes=" + begin + "-" + end);
                InputStream is = connection.getInputStream();
                byte[] bytes = new byte[1024 * 1024];
                RandomAccessFile accessFile = new RandomAccessFile(file, "rw");
                accessFile.seek(begin);
                int len = 0;
                HashMap<String, Integer> map = (HashMap<String, Integer>) threadlist.get(id);
                while ((len = is.read(bytes)) != -1 && downloading) {
                    accessFile.write(bytes, 0, len);
                    update(len);
                    map.put("finished", map.get("finished") + len);
                    int idd = id + 1;
                    Log.d("下载线程" + idd, "" + total);
                }
                is.close();
                accessFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    //      下载进度更新，并且发送消息给handler以便更新
    synchronized private void update(int add) {
        total += add;
        h.obtainMessage(123, total, 0).sendToTarget();
    }

    private void Notification() {
//        创建下载完成通知提示，并且点击可以访问一个网站
        Notification.Builder builder = new Notification.Builder(MainActivity.this);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://gold.xitu.io/"));
        PendingIntent pendingIntent = PendingIntent.getActivity(MainActivity.this, 0, intent, 0);
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setContentText("恭喜您！下载完成。点击访问掘金网");
        builder.setContentTitle("下载提示！");
        builder.setAutoCancel(true);//自动取消，点击一次后消失
        builder.setContentIntent(pendingIntent);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(1, builder.build());
    }
}
