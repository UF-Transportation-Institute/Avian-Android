package com.aseem.abc;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.peertopark.java.geocalc.Coordinate;
import com.peertopark.java.geocalc.DegreeCoordinate;
import com.peertopark.java.geocalc.EarthCalc;
import com.peertopark.java.geocalc.Point;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.DecimalFormat;
import java.util.logging.Level;

public class MainActivity extends AppCompatActivity {


    EditText listPortLabel;
    EditText suitIPLabel;
    EditText suitPortLabel;

    TextView ipLabel;
    TextView timer;
    TextView speed;
    TextView logLabel;

    boolean toListen;

    Button startListeningButton;
    Button requestTrajButton;

    InetAddress addressRecieved;
    int portRecieved;

    private DecimalFormat df = new DecimalFormat("##.00");

    LinearLayout dashboard;
    LinearLayout settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        WifiManager wifiMan = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInf = wifiMan.getConnectionInfo();
        int ipAddress = wifiInf.getIpAddress();
        String ip = String.format("%d.%d.%d.%d", (ipAddress & 0xff),(ipAddress >> 8 & 0xff),(ipAddress >> 16 & 0xff),(ipAddress >> 24 & 0xff));

        ipLabel = findViewById(R.id.ipLabel);

        if(ip.equals("0.0.0.0"))
            ipLabel.setText("Check Wifi connection. No internet service and IP");
        else
            ipLabel.setText("Device IP : "+ip);


        logLabel = findViewById(R.id.logLabel);
        timer = findViewById(R.id.timer);
        speed = findViewById(R.id.speed);

        listPortLabel = findViewById(R.id.listPortLabel);
        toListen = false;
        startListeningButton = findViewById(R.id.startListeningButton);
        requestTrajButton = findViewById(R.id.requestTrajButton);
        suitIPLabel = findViewById(R.id.suitIPLabel);
        suitPortLabel = findViewById(R.id.suitPortLabel);

        dashboard = findViewById(R.id.dashboard);
        settings = findViewById(R.id.settings);
        dashboard.setVisibility(View.GONE);
        settings.setVisibility(View.VISIBLE);
    }



    public void startListening(View view) {
        Log.d("aseem","starting listening server");

        toListen = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
//                    DatagramSocket socket = new DatagramSocket(5005);
                    DatagramSocket socket = new DatagramSocket(Integer.parseInt(listPortLabel.getText().toString()));

                    Log.d("aseem","UDP Server is listening");

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            logLabel.setText("UDP Server is listening\n\n\n\n\n\n\n\n");
                        }
                    });

                    while(toListen){
                        byte[] buf = new byte[1024];

                        // receive request
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        socket.receive(packet);     //this code block the program flow

                        // send the response to the client at "address" and "port"
                        final InetAddress address = packet.getAddress();
                        final int port = packet.getPort();

                        final String received
                                = new String(packet.getData(), 0, packet.getLength());

                        Log.d("aseem","received string: "+received);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                logLabel.setText("received string: "+received);
                            }
                        });

                        processString(received);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                addressRecieved = address;
                                portRecieved = port;
                                logLabel.setText(logLabel.getText()+"\n\n"+received);
                            }
                        });
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }).start();

        startListeningButton.setText("Listening");
        startListeningButton.setEnabled(false);

    }

    private void processString(String line){
        String[] list = line.split(",");
        for(String s : list){
            s.replace(" ", "");
        }

        int messageId;
        double firstLat;
        double firstLon;
        int firstMinMin;
        int firstMinMS;
        int offSetCount;

        try {
            messageId = Integer.parseInt(list[0]);
            firstLat = Double.parseDouble(list[1]) / 10000000;
            firstLon = Double.parseDouble(list[2]) / 10000000;
            firstMinMin = Integer.parseInt(list[3]);
            firstMinMS = Integer.parseInt(list[4]);
            offSetCount = Integer.parseInt(list[5]);
        } catch (NumberFormatException ex) {
            System.err.println("Error parsing message!");
            return;
        }

        double time = 0;

        Coordinate startLat = new DegreeCoordinate(firstLat);
        Coordinate startLon = new DegreeCoordinate(firstLon);
        Point start = new Point(startLat, startLon);

        double nextLat = firstLat;
        double nextLon = firstLon;

        int count = 0;
        double total_time = 0;
        double total_dist = 0;
        for(int i = 6; i < (3 * offSetCount) + 6; i+=3) {
            count += 1;
            nextLat += (Double.parseDouble(list[i]) / 10000000);
            nextLon += (Double.parseDouble(list[i + 1]) / 10000000);
            time = Double.parseDouble(list[i + 2]) / 1000;
            total_time += time;
            Coordinate endLat = new DegreeCoordinate(nextLat);
            Coordinate endLon = new DegreeCoordinate(nextLon);
            Point end = new Point(endLat, endLon);

            double meters = EarthCalc.getDistance(start, end);
            total_dist += meters;
            //double meters1 = EarthCalc.getBearing(start, end);
            //double meters2 = EarthCalc.getHarvesineDistance(start, end);

            //avg_mph += (meters / time) * 2.23694;
            startLat = endLat;
            startLon = endLon;
            start = new Point(startLat, startLon);
        }
        //avg_mph /= count;
        //int speed_advisory = (int) Math.round(avg_mph);
        final Integer timerStart = (int) Math.round(total_time);

        System.out.println("time: " + df.format(total_time) + " seconds");
        System.out.println("distance: " + df.format(total_dist) + " meters");
        logLabel.setText(logLabel.getText()+"\n\n"+ "Trajectory: TTC is " + df.format(total_time) + " sec, and distance is " + df.format(total_dist) + " meters.");
        speed.setText((int)(total_dist/total_time*2.23694));
        //update Seconds Timer Display

        requestTrajButton.setEnabled(false);
        startTimer(timerStart);

    }

    private void startTimer(Integer timerStart) {
        new CountDownTimer(timerStart*1000, 100) {
            @Override
            public void onTick(final long timeLeft) {
                Log.d("aseem","Timer: time left"+ timeLeft);
                logLabel.setText(logLabel.getText()+ "\n\nTimer: time left"+ timeLeft);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        long timeLeft2 = timeLeft/100;
                        timer.setText(""+ ((int)(timeLeft2/10)));
                    }
                });
            }

            @Override
            public void onFinish() {
                Log.d("aseem","Timer over");
                timer.setText("0");
                logLabel.setText(logLabel.getText() + "+\n\nTimer over");
                requestTrajButton.setEnabled(true);
                speed.setText("0");
            }
        }.start();
        speed.setText("20");
    }

    public void requestTrajectory(View view) {
        requestTrajButton.setEnabled(false);
        //send UDP message
        sendUDPMessage("Served,0");
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        startTimer(7);
    }

    private void sendUDPMessage(final String msg) {
        final String hostIP = suitIPLabel.getText().toString();
        final int hostPort = Integer.parseInt(suitPortLabel.getText().toString());

        if(!hostIP.equals(addressRecieved) || portRecieved!=hostPort){
            Log.d("aseem", "IP and port of suitcse dont match");
            logLabel.setText(logLabel.getText()+"\n\nIP AND PORT OF SUITCASE DON'T MATCH");
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try{

                    Log.d("aseem","Requesting Trajectory");

                    DatagramSocket datagramSocket = new DatagramSocket();

                    byte[] buffer = msg.getBytes();

                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(hostIP), hostPort);
                    datagramSocket.send(packet);

                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }).start();

    }

    public void resetServer(View view) {
        toListen = false;
        //stop threads
        startListeningButton.setEnabled(true);
    }

    public void showHideSettings(View view) {
        if(dashboard.getVisibility()==View.VISIBLE){
            dashboard.setVisibility(View.GONE);
            settings.setVisibility(View.VISIBLE);
        }else {
            settings.setVisibility(View.GONE);
            dashboard.setVisibility(View.VISIBLE);
        }

    }
}
