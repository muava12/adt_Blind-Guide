package com.ta.blindguideanyar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class BlindGuide extends Activity {

	private TextView myLabel;
	private EditText myTextbox;
	
	SoundPool soundPool;
	int beepSound = -1, welcome = -1, beepAtas = -1;
	public float volumeKiri = 0.1f,volumeKanan = 0.1f;
	
	BluetoothAdapter mBluetoothAdapter;
	BluetoothSocket mmSocket;
	BluetoothDevice mmDevice;
	OutputStream mmOutputStream;
	InputStream mmInputStream;
	Thread workerThread;
	byte[] readBuffer;
	int readBufferPosition;
	int counter;
	volatile boolean stopWorker;
	private StringBuilder sb = new StringBuilder();
	public int jarak, kanan, kiri, kananDepan, kiriDepan, atas, ngomong;
	boolean suara = true;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_blind_guide);
		
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		soundPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
		beepSound = soundPool.load(this, R.raw.beeb1, 1);
		welcome   = soundPool.load(this, R.raw.welcome,1);
		beepAtas  = soundPool.load(this, R.raw.beeb2,1);
		
	    myLabel = (TextView)findViewById(R.id.tampil);
	    
	   // Register for broadcasts on BluetoothAdapter state change
	    IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
	    this.registerReceiver(mReceiver, filter);
	    
	    // Initialize Receiver
        IntentFilter filter2 = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter2.addAction(Intent.ACTION_SCREEN_OFF);
        BroadcastReceiver mReceiver2 = new ScreenReceiver();
        registerReceiver(mReceiver2, filter2);
	    
	    // try to find and connect BT when this app start
	    try {
	    	findBT();
	    	suara = true;
            //openBT();
		} catch (Exception e) {
			myLabel.append("\r\nKoneksi Gagal!");
			myLabel.setTextColor(Color.RED);
		} 	
	    
	    //Gesture
	    RelativeLayout rl = (RelativeLayout) findViewById(R.id.relativeLayout);
	    rl.setOnTouchListener(new OnSwipeTouchListener() {
	        public void onSwipeTop() {
	                    // swipe ke atas
	        	suara = true;
	        	Toast.makeText(BlindGuide.this, "Top", Toast.LENGTH_SHORT).show();
	        }
	        public void onSwipeRight() {
	                    // swipe layar ke kanan
	        	soundPool.play(welcome, 100, 100, 0, 0, 1);
	        	try {
	        		suara = true;
	        		findBT();
					openBT();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	        	Toast.makeText(BlindGuide.this, "Kanan", Toast.LENGTH_SHORT).show();
	        }
	        public void onSwipeLeft() {
	                    // swipe layar ke kiri
	        	Toast.makeText(BlindGuide.this, "Kiri", Toast.LENGTH_SHORT).show();
	        	soundPool.play(welcome, 100, 100, 0, 0, 1);
	        	try {
	        		suara = false;
	    			closeBT();
	    			unregisterReceiver(mReceiver);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	        	
	        }
	        public void onSwipeBottom() {
	                    // swipe layar ke bawah
	        	finish();
	        	Toast.makeText(BlindGuide.this, "Exit", Toast.LENGTH_SHORT).show();
	        }
	    });
	    
	}

	void findBT()
	{
	    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	    if(mBluetoothAdapter == null)
	    {
	        myLabel.setText("No bluetooth adapter available");
	    }

	    if(!mBluetoothAdapter.isEnabled())
	    {
	        Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
	        startActivityForResult(enableBluetooth, 0);
	    }

	    Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
	    if(pairedDevices.size() > 0)
	    {
	        for(BluetoothDevice device : pairedDevices)
	        {
	            if(device.getName().equals("Bismillah_110")) 
	            {
	                mmDevice = device;
	                break;
	            }
	        }
	    }
	    myLabel.setText("Bluetooth Device Found");
	    
	    try {
			openBT();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	void openBT() throws IOException
	{
	    UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
	    mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);        
	    mmSocket.connect();
	    mmOutputStream = mmSocket.getOutputStream();
	    mmInputStream = mmSocket.getInputStream();

	    beginListenForData();

	    myLabel.setText("Bluetooth Opened");
	}

	void beginListenForData()
	{
	    final Handler handler = new Handler(); 
	    final byte delimiter = 10; //This is the ASCII code for a newline character

	    stopWorker = false;
	    readBufferPosition = 0;
	    readBuffer = new byte[1024];
	    workerThread = new Thread(new Runnable()
	    {
	        public void run()
	        {                
	           while(!Thread.currentThread().isInterrupted() && !stopWorker)
	           {
	                try 
	                {
	                    int bytesAvailable = mmInputStream.available();                        
	                    if(bytesAvailable > 0)
	                    {
	                        byte[] packetBytes = new byte[bytesAvailable];
	                        mmInputStream.read(packetBytes);
	                        for(int i=0;i<bytesAvailable;i++)
	                        {
	                            byte b = packetBytes[i];
	                            if(b == delimiter)
	                            {
	     byte[] encodedBytes = new byte[readBufferPosition];
	     System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
	     final String data = new String(encodedBytes, "US-ASCII");
	     readBufferPosition = 0;

	                                handler.post(new Runnable()
	                                {
	                                    public void run()
	                                    {
	                                        //myLabel.append(data); // menampilkan per karakter
	                                    	sb.append(data);		// tiap karakter yang diterima-
	                                    							// di tambahkan di "sb"
	                                    	
	                                        parsingData();		// proses parsing data sensor
	                                       
	                                        sb.delete(0, sb.length());    // reset nilai jumlah karakter
	                                        
	                                    }
	                                });
	                            }
	                            else
	                            {
	                                readBuffer[readBufferPosition++] = b;
	                            }
	                        }
	                    }
	                } 
	                catch (IOException ex) 
	                {
	                    stopWorker = true;
	                }
	           }
	        }
	    });
	    
	    
	    workerThread.start();
	    thread.start();
	    
	    
	}
	
	
	private void parsingData() {
		// TODO Auto-generated method stub
		
        int batasS1   = sb.indexOf("S1");
        int batasS2	  = sb.indexOf("S2");
        int batasS3	  = sb.indexOf("S3");
        int batasS4	  = sb.indexOf("S4");
        int batasS5	  = sb.indexOf("S5");
        int batasS6	  = sb.indexOf("S6");
        int batasAkhir  = sb.indexOf("#");		// "\r\n"				// determine the end-of-line
    	if (batasAkhir > 0) { 											// if end-of-line,
            
            String sbprint1 = sb.substring(batasS1+3, batasS2);				// extract string
//            myLabel.setText(sbprint1);
            kananDepan = Integer.parseInt(sbprint1.replaceAll("[\\D]", ""));
            
            String sbprint2 = sb.substring(batasS2+3, batasS3);				// extract string
//            myLabel.setText(sbprint2);
            kanan = Integer.parseInt(sbprint2.replaceAll("[\\D]", ""));
//            
            String sbprint3 = sb.substring(batasS3+3, batasS4);				// extract string
            myLabel.setText(sbprint3);
            jarak = Integer.parseInt(sbprint3.replaceAll("[\\D]", ""));
//            
            String sbprint4 = sb.substring(batasS4+3, batasS5);				// extract string
//            myLabel.setText(sbprint4);
            kiri = Integer.parseInt(sbprint4.replaceAll("[\\D]", ""));
//            
            String sbprint5 = sb.substring(batasS5+3, batasS6);				// extract string
//            myLabel.setText(sbprint5);
            kiriDepan = Integer.parseInt(sbprint5.replaceAll("[\\D]", ""));
         
            String sbprint6 = sb.substring(batasS6+3, batasAkhir);				// extract string
//            myLabel.setText(sbprint5);
            atas = Integer.parseInt(sbprint6.replaceAll("[\\D]", ""));
            
            //myLabel.append("\r\n");
//            myLabel.append("\r\n");   
    	}
	}
	
	
	Thread thread = new Thread() {
		@Override
		public void run() {
			
			while(suara) {
				
				if (ngomong == 1){
					try {
						sleep(2000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
//					play.start();
				}
				
				if (kanan <= 80 && kiri <= 80) {
					volumeKiri = 1.0f; 
					volumeKanan= 1.0f;
				}
				else if (kanan <=80) {
					volumeKanan  = 0.0f;
					volumeKiri   = 1.0f;
				}
				else if (kiri <= 80) {
					volumeKiri   = 0.0f;
					volumeKanan  = 1.0f;
				}
//				else if (kananDepan <=80) {
//					volumeKanan  = 0.3f;
//					volumeKiri   = 1.0f;
//				}
//				else if (kiriDepan <=80) {
//					volumeKiri  = 0.3f;
//					volumeKanan   = 1.0f;
//				}
				else {
					volumeKanan = 0.3f;
					volumeKiri  = 0.3f;
				}
					
				
				
				if (jarak < 80 && atas < 80) {
					try {
						sleep(200);
					} catch (InterruptedException e) {
					
						e.printStackTrace();
					}
					//handler.post(r);
					soundPool.play(beepAtas, volumeKanan, volumeKiri, 0, 0, 1);
				}
				else if (jarak < 80 ) {
					try {
						sleep(200);
					} catch (InterruptedException e) {
					
						e.printStackTrace();
					}
					//handler.post(r);
					soundPool.play(beepSound, volumeKanan, volumeKiri, 0, 0, 1);
				}
				else if ((jarak >= 80 && jarak < 130) || kanan <= 130 || kiri <= 130) {// || kiriDepan <=130 || kananDepan <=130) {
					try {
						sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					soundPool.play(beepSound, volumeKanan, volumeKiri, 0, 0, 1);
				}
				else if (jarak >= 130 && atas >=130){
//					try {
//						sleep(500);
//					} catch (InterruptedException e) {
//		
//						e.printStackTrace();
//					}
					//soundPool.play(beepSound, volumeKanan, volumeKiri, 0, 0, 1);
				}
			}
//			beep.stop();
			
		}
	};
	
	// fungsi untuk mengirim data ke Alat
	void sendData() throws IOException
	{
	    String msg = myTextbox.getText().toString();
	    msg += "\n";
	    mmOutputStream.write(msg.getBytes());
	    myLabel.setTextColor(Color.BLUE);  // ganti ke warna biru
	    myLabel.setText("Data Sent");
	    myLabel.setTextColor(Color.BLACK); // ganti ke warna hitam
	    myTextbox.setText("");
	}

	void closeBT() throws IOException
	{
	    stopWorker = true;
	    mmOutputStream.close();
	    mmInputStream.close();
	    mmSocket.close();
	    myLabel.setText("Bluetooth Closed");
	}
	
	
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	        final String action = intent.getAction();

	        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
	            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
	                                                 BluetoothAdapter.ERROR);
	            switch (state) {
	            case BluetoothAdapter.STATE_OFF:
	            	
	                break;
	            case BluetoothAdapter.STATE_TURNING_OFF:
	                
	                break;
	            case BluetoothAdapter.STATE_ON:
	                try {
	                	Toast.makeText(getApplicationContext(), "BT on", Toast.LENGTH_SHORT).show();
						openBT();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	                break;
	            case BluetoothAdapter.STATE_TURNING_ON:
	            	
	                break;
	            case BluetoothAdapter.STATE_CONNECTED:
//	            	ngomong = 1;
//	            	final Handler handler = new Handler();
//	                handler.postDelayed(new Runnable() {
//	                    @Override
//	                    public void run() {
//	                        // Do something after 10s = 10000ms
//	                    	suara = true;
//	                    }
//	                }, 6000);
	            	Toast.makeText(getApplicationContext(), "BT Connected", Toast.LENGTH_SHORT).show();
	            	break;
	            }
	            
	            	
	        }
	    }
	};
	
	
	@Override
	protected void onPause() {
		
		// TODO apa aja deh terserah
		
		if (ScreenReceiver.wasScreenOn) {
			// THIS IS THE CASE WHEN ONPAUSE() IS CALLED BY THE SYSTEM DUE TO A
			// SCREEN STATE CHANGE
			//System.out.println("SCREEN TURNED OFF");
		} else {
			// THIS IS WHEN ONPAUSE() IS CALLED WHEN THE SCREEN STATE HAS NOT
			// CHANGED
			suara = false;
		}
		super.onPause();
	}
	
	@Override
	protected void onResume() {
		
		// TODO apa aja deh terserah
		
		// ONLY WHEN SCREEN TURNS ON
        if (!ScreenReceiver.wasScreenOn) {
            // THIS IS WHEN ONRESUME() IS CALLED DUE TO A SCREEN STATE CHANGE
            //System.out.println("SCREEN TURNED ON");
        } else {
            // THIS IS WHEN ONRESUME() IS CALLED WHEN THE SCREEN STATE HAS NOT CHANGED
        	suara = true;
        }
        super.onResume();
		
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		try {
			suara = false;
			closeBT();
			
			// Unregister broadcast listeners
		    this.unregisterReceiver(mReceiver);
		    
		} catch (Exception e) {
			
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.blind_guide, menu);
		return true;
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.about:
            // Launch the DeviceListActivity to see devices and do scan
        	AlertDialog ad = new AlertDialog.Builder(this).create();  
        	ad.setCancelable(false); // This blocks the 'BACK' button  
        	ad.setMessage("Blind Guide Jacket\r\ndi buat oleh muafa rosyad sebagai proyek akhir");  
        	ad.setButton("OK", new DialogInterface.OnClickListener() {  
        	    @Override  
        	    public void onClick(DialogInterface dialog, int which) {  
        	        dialog.dismiss();                      
        	    }  
        	}
        	);  
        	ad.show();    
        return true;
//        case R.id.discoverable:
//            
//            return true;
        }
        return false;
    }

}
