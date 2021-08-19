package com.example.basictcp2;

import androidx.appcompat.app.AppCompatActivity;


import android.os.Bundle;
import android.util.Log;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import android.content.DialogInterface;

import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.nfc.tech.TagTechnology;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {


    private TextView mConnectionStatus;
    private EditText mInputEditText;
    private ArrayAdapter<String> mConversationArrayAdapter;

    TextView myUID;
    String con=null;
    String myClone="";
    boolean nowStatus=false;
    private final String[][] techList = new String[][]{
            new String[] {

                    NfcA.class.getName(),
                    NfcB.class.getName(),
                    NfcF.class.getName(),
                    NfcV.class.getName(),
                    NdefFormatable.class.getName(),
                    TagTechnology.class.getName(),
                    IsoDep.class.getName(),
                    MifareClassic.class.getName(),
                    MifareUltralight.class.getName(),
                    Ndef.class.getName()
            }
    };

    private static final String TAG = "TcpClient";
    private boolean isConnected = false;

    private String mServerIP = null;
    private Socket mSocket = null;
    private PrintWriter mOut;
    private BufferedReader mIn;
    private Thread mReceiverThread = null;


    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mConnectionStatus = (TextView)findViewById(R.id.connection_status_textview);
        mInputEditText = (EditText)findViewById(R.id.input_string_edittext);
        ListView mMessageListview = (ListView) findViewById(R.id.message_listview);
        Button sendButton = (Button)findViewById(R.id.send_button);
        sendButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){

                String sendMessage = mInputEditText.getText().toString();
                if ( sendMessage.length() > 0 ) {

                    if (!isConnected) System.out.println("error");
                    else {
                        new Thread(new SenderThread(sendMessage)).start();
                        mInputEditText.setText(" ");
                    }
                }
            }
        });


        mConversationArrayAdapter = new ArrayAdapter<>( this,
                android.R.layout.simple_list_item_1 );
        mMessageListview.setAdapter(mConversationArrayAdapter);

        new Thread(new ConnectThread("172.30.1.10", 7778)).start();
    }
    @Override
    protected void onResume(){
        super.onResume();

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),0);

        IntentFilter filter = new IntentFilter();

        filter.addAction(NfcAdapter.ACTION_TAG_DISCOVERED);
        filter.addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        filter.addAction(NfcAdapter.ACTION_TECH_DISCOVERED);

        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        try{
            nfcAdapter.enableForegroundDispatch(this,pendingIntent,new IntentFilter[]{filter},this.techList);
        }
        catch (NullPointerException e){

        }

    }
    @Override
    protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        if(intent.getAction().equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {
            if(ByteArrayToHexString(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID)).equals(myClone)){
                try {
                    con="false";
                    new Thread(new SenderThread(con)).start();
                    mSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            myUID = (TextView) findViewById(R.id.myUID);
            myUID.setText(ByteArrayToHexString(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID)));
            myClone=ByteArrayToHexString(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID));
            con="true";
            new Thread(new SenderThread(myClone)).start();
        }
    }

    private String ByteArrayToHexString(byte []array) {
        int i,j,in;
        String [] hex = {"0","1","2","3","4","5","6","7","8","9","A","B","C","D","E","F"};
        String out="";

        for(j = 0;j<array.length;++j)
        {
            in = (int) array[j] & 0xff;
            i = (in>>4) & 0x0f;
            out += hex[i];
            i = in& 0x0f;
            out += hex[i];
        }
        switch (out) {
            case "042AA0627B7280":
                out = "Letpulldown1";
                break;
            case "0426A0627B7280":
                out = "Letpulldown2";
                break;
            case "0422A0627B7280":
                out = "Letpulldown3";
                break;
            case "041EA0627B7280":
                out = "Letpulldown4";
                break;
            default:
                out = "This is not mine";
                break;
        }

        return out;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        isConnected = false;
    }


    private static long back_pressed;
    @Override
    public void onBackPressed(){

        if (back_pressed + 2000 > System.currentTimeMillis()){
            super.onBackPressed();

            Log.d(TAG, "onBackPressed:");
            isConnected = false;

            finish();
        }
        else{
            Toast.makeText(getBaseContext(), "한번 더 뒤로가기를 누르면 종료됩니다.", Toast.LENGTH_SHORT).show();
            back_pressed = System.currentTimeMillis();
        }

    }


    private class ConnectThread implements Runnable {

        private String serverIP;
        private int serverPort;

        ConnectThread(String ip, int port) {
            serverIP = ip;
            serverPort = port;

            mConnectionStatus.setText("connecting to " + serverIP + ".......");
        }

        @Override
        public void run() {

            try {

                mSocket = new Socket(serverIP, serverPort);


                mServerIP = mSocket.getRemoteSocketAddress().toString();

            } catch (UnknownHostException e) {
                Log.d(TAG, "ConnectThread: can't find host");
            } catch (SocketTimeoutException e) {
                Log.d(TAG, "ConnectThread: timeout");
            } catch (Exception e) {

                Log.e(TAG, ("ConnectThread:" + e.getMessage()));
            }


            if (mSocket != null) {

                try {

                    mOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(mSocket.getOutputStream(), "UTF-8")), true);
                    mIn = new BufferedReader(new InputStreamReader(mSocket.getInputStream(), "UTF-8"));

                    isConnected = true;
                } catch (IOException e) {

                    Log.e(TAG, ("ConnectThread:" + e.getMessage()));
                }
            }


            runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    if (isConnected) {

                        Log.d(TAG, "connected to " + serverIP);
                        mConnectionStatus.setText("connected to " + serverIP);


                        mReceiverThread = new Thread(new ReceiverThread());
                        mReceiverThread.start();
                    } else {

                        Log.d(TAG, "failed to connect to server " + serverIP);
                        mConnectionStatus.setText("failed to connect to server " + serverIP);
                    }

                }
            });
        }
    }


    private class SenderThread implements Runnable {

        private String msg;

        SenderThread(String msg) {
            this.msg = msg;
        }

        @Override
        public void run() {

            mOut.println(this.msg);
            mOut.flush();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "send message: " + msg);
                    mConversationArrayAdapter.insert("Me - " + msg, 0);
                }
            });
        }
    }


    private class ReceiverThread implements Runnable {

        @Override
        public void run() {

            try {

                while (isConnected) {

                    if ( mIn ==  null ) {

                        Log.d(TAG, "ReceiverThread: mIn is null");
                        break;
                    }

                    final String recvMessage =  mIn.readLine();

                    if (recvMessage != null) {

                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {

                                Log.d(TAG, "recv message: "+recvMessage);
                                mConversationArrayAdapter.insert(mServerIP + " - " + recvMessage, 0);
                            }
                        });
                    }
                }

                Log.d(TAG, "ReceiverThread: thread has exited");
                if (mOut != null) {
                    mOut.flush();
                    mOut.close();
                }

                mIn = null;
                mOut = null;

                if (mSocket != null) {
                    try {
                        mSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            catch (IOException e) {

                Log.e(TAG, "ReceiverThread: "+ e);
            }
        }

    }






}
