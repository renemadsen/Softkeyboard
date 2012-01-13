
/**
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*/

/**
*
* @author Martin Jensby mj@microting.dk
* @author <a target="_blank" href="http://www.microting.com/">www.microting.com</a>
*
*/

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
	
	private String receivedChars = "";
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
		} catch (IOException e) {
			Log.d(TAG, e.getMessage());
			e.printStackTrace();
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
					receivedChars += (char)buffer[i];
				
				Log.d(TAG, "Received: " + receivedChars);
				
				if(bcb != null)
				{
					if(!timerIsRunning)
					{
						timerIsRunning = true;
						timerToSent.schedule(new TimerTask() {
							@Override
							public void run() {
								callback();
								timerIsRunning = false;
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
		Log.d(TAG, "Cancelling the thread!");
		try {
			run = false;
			mmSocket.close();
		} catch (IOException e) {
			Log.e(TAG, "close() of connect socket failed", e);
		}
	}
	
	private void callback()
	{
		bcb.barcodeCallBack(receivedChars);
		receivedChars = "";
	}
}
