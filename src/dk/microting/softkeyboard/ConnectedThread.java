
package dk.microting.softkeyboard;

import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

public class ConnectedThread extends Thread {
	
	private String TAG = "ConnectedThread";
	
	private final BluetoothSocket mmSocket;
	private final InputStream mmInStream;
	private final BarcodeCallback bcb;
	
	
	private String gotten = "";
	private Timer timerToSent;
	private boolean timerIsRunning = false;
	
	private boolean run = true;

	public ConnectedThread(BluetoothSocket socket, BarcodeCallback bcb) {
		Log.d(TAG, "create ConnectedThread");
		mmSocket = socket;
		
		InputStream tmpIn = null;
		this.bcb = bcb;
		this.timerToSent = new Timer();

		// Get the BluetoothSocket input and output streams
		try {
			tmpIn = socket.getInputStream();
		} catch (IOException e) {
			Log.e(TAG, "temp sockets not created", e);
		}

		mmInStream = tmpIn;
	}

	public void run() {
		Log.i(TAG, "BEGIN mConnectedThread");
		byte[] buffer = new byte[1024];
		int bytes;
		
		try {
			mmSocket.connect();
		} catch (IOException e1) {
			Log.d(TAG, e1.getMessage());
			e1.printStackTrace();
			bcb.barcodeScannerDisconnect();
			run = false;
		}
		
		if(run)
			bcb.barcodeScannerConnect();
		
		// Keep listening to the InputStream while connected
		while (run) {
			try {
				// Read from the InputStream
				bytes = mmInStream.read(buffer);
				
				for(int i = 0; i < bytes; i++)
					gotten += (char)buffer[i];
				
				Log.d(TAG, "Received: " + gotten);
				
				if(bcb != null)
				{
					if(!timerIsRunning)
					{
						timerIsRunning = true;
						timerToSent.schedule(new TimerTask() {
							
							@Override
							public void run() {
								callback();
							}
						}, 200);
					}
				}
				
			} catch (IOException e) {
				Log.e(TAG, "disconnected", e);
				bcb.barcodeScannerDisconnect();
				break;
			}
		}
	}

	public void cancel() {
		try {
			run = false;
			mmSocket.close();
		} catch (IOException e) {
			Log.e(TAG, "close() of connect socket failed", e);
		}
	}
	
	private void callback()
	{
		
		bcb.barcodeCallBack(gotten);
		gotten = "";
		if(timerIsRunning)
			timerIsRunning = false;
	}
}
