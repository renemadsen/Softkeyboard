
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

public interface BarcodeCallback {

	void barcodeCallBack(String barcode);
	
	void barcodeScannerConnect();
	
	void barcodeScannerDisconnect();
}
