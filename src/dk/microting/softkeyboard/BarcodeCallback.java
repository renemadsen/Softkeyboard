package dk.microting.softkeyboard;

public interface BarcodeCallback {

	void barcodeCallBack(String barcode);
	
	void barcodeScannerConnect();
	
	void barcodeScannerDisconnect();
}
