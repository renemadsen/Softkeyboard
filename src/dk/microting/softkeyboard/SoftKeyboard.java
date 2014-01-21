
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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.Keyboard.Key;
import android.inputmethodservice.KeyboardView;
import android.os.Handler;
import android.text.method.MetaKeyKeyListener;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dk.microting.softkeyboard.R;
import dk.microting.softkeyboard.autoupdateapk.AutoUpdateApk;

/**
 * Example of writing an input method for a soft keyboard.  This code is
 * focused on simplicity over completeness, so it should in no way be considered
 * to be a complete soft keyboard implementation.  Its purpose is to provide
 * a basic example for how you would get started writing an input method, to
 * be fleshed out as appropriate.
 */
public class SoftKeyboard extends InputMethodService implements KeyboardView.OnKeyboardActionListener, BarcodeCallback {
    /**
     * This boolean indicates the optional example code for performing
     * processing of hard keys in addition to regular text generation
     * from on-screen interaction.  It would be used for input methods that
     * perform language translations (such as converting text entered on 
     * a QWERTY keyboard to Chinese), but may not be used for input methods
     * that are primarily intended to be used for on-screen text entry.
     */
    static final boolean PROCESS_HARD_KEYS = false;
    
    private KeyboardView mInputView;
    private CandidateView mCandidateView;
    
    private StringBuilder mComposing = new StringBuilder();
    private boolean mPredictionOn;
    private boolean mCompletionOn;
    private int mLastDisplayWidth;
    private boolean mCapsLock;
    private long mLastShiftTime;
    private long mMetaState;
    
    private LatinKeyboard mSymbolsKeyboard;
    private LatinKeyboard mSymbolsShiftedKeyboard;
    private LatinKeyboard mQwertyKeyboard;
    
    private LatinKeyboard mCurKeyboard;
    
    private String mWordSeparators;
    
	private BluetoothAdapter btAdapter;
	private Set<BluetoothDevice> pairedDevices;
	
	private boolean scannerConnected = false;
	private BluetoothSocket scanner;
	private ConnectedThread scannerThread;
	private Key scannerKey;
	private Handler handler;
	
	private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //UUID for generic SPP connections
	
	private AutoUpdateApk aua;
    
    private String TAG = "SoftKeyboard";
    
    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    @Override
    public void onCreate() {
    	Log.d(TAG, "onCreate");
        super.onCreate();
        handler = new Handler();
        mWordSeparators = getResources().getString(R.string.word_separators);
        
        this.btAdapter = BluetoothAdapter.getDefaultAdapter();
        if(!btAdapter.isEnabled())
        	btAdapter.enable();
        
        pairedDevices = btAdapter.getBondedDevices();
        
        try {
			connectToScanner();
		} catch (Exception e) {
			Log.d(TAG, e.getMessage());
			e.printStackTrace();
		}
		
		this.aua = new AutoUpdateApk(this);
		this.aua.checkUpdatesManually();
    }

	/**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    @Override
    public void onInitializeInterface() {
    	Log.d(TAG, "onInitializeInterface");
        if (mQwertyKeyboard != null) {
            // Configuration changes can happen after the keyboard gets recreated,
            // so we need to be able to re-build the keyboards if the available
            // space has changed.
            int displayWidth = getMaxWidth();
            if (displayWidth == mLastDisplayWidth)
            	return;
            mLastDisplayWidth = displayWidth;
        }
        mQwertyKeyboard = new LatinKeyboard(this, R.xml.qwerty);
        mSymbolsKeyboard = new LatinKeyboard(this, R.xml.symbols);
        mSymbolsShiftedKeyboard = new LatinKeyboard(this, R.xml.symbols_shift);
    }
    
    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    @Override
    public View onCreateInputView() {
    	Log.d(TAG, "onCreateInputView");
        mInputView = (KeyboardView) getLayoutInflater().inflate(R.layout.input, null);
        mInputView.setOnKeyboardActionListener(this);
        mInputView.setKeyboard(mQwertyKeyboard);
        return mInputView;
    }

    /**
     * Called by the framework when your view for showing candidates needs to
     * be generated, like {@link #onCreateInputView}.
     */
    @Override
    public View onCreateCandidatesView() {
    	Log.d(TAG, "onCreateCandidatesView");
        mCandidateView = new CandidateView(this);
        mCandidateView.setService(this);
        return mCandidateView;
    }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
    	Log.d(TAG, "onStartInput");
    	attribute.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI; //Disable the extracted UI, example, do not use fullscreen mode in survey pin code
        super.onStartInput(attribute, restarting);
        
        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
        mComposing.setLength(0);
        updateCandidates();
        
        if (!restarting) {
            // Clear shift states.
            mMetaState = 0;
        }
        
        mPredictionOn = false;
        mCompletionOn = false;
        
        // We are now going to initialize our state based on the type of
        // text being edited.
        switch (attribute.inputType & EditorInfo.TYPE_MASK_CLASS) {
            case EditorInfo.TYPE_CLASS_NUMBER:
            case EditorInfo.TYPE_CLASS_DATETIME:
                // Numbers and dates default to the symbols keyboard, with
                // no extra features.
                mCurKeyboard = mSymbolsKeyboard;
                break;
                
            case EditorInfo.TYPE_CLASS_PHONE:
                // Phones will also default to the symbols keyboard, though
                // often you will want to have a dedicated phone keyboard.
                mCurKeyboard = mSymbolsKeyboard;
                break;
                
            case EditorInfo.TYPE_CLASS_TEXT:
                mCurKeyboard = mQwertyKeyboard;
                updateShiftKeyState(attribute);
                break;
                
            default:
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
                mCurKeyboard = mQwertyKeyboard;
                updateShiftKeyState(attribute);
        }
        
        // Update the label on the enter key, depending on what the application
        // says it will do.
        mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
    }

    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    @Override
    public void onFinishInput() {
    	Log.d(TAG, "onFinishInput");
        super.onFinishInput();
        
        // Clear current composing text and candidates.
        mComposing.setLength(0);
        updateCandidates();
        
        // We only hide the candidates window when finishing input on
        // a particular editor, to avoid popping the underlying application
        // up and down if the user is entering text into the bottom of
        // its window.
        setCandidatesViewShown(false);
        
        mCurKeyboard = mQwertyKeyboard;
        if (mInputView != null) {
            mInputView.closing();
        }
    }
    
    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
    	Log.d(TAG, "onStartInputView");
        super.onStartInputView(attribute, restarting);
        // Apply the selected keyboard to the input view.
        mInputView.setKeyboard(mCurKeyboard);
        mInputView.closing();
        try {
			connectToScanner();
		} catch (Exception e) {
			Log.d(TAG, e.getMessage());
			e.printStackTrace();
			Log.d(TAG, "Failed to connect to scanner");
		}
    }

    /**
     * This translates incoming hard key events in to edit operations on an
     * InputConnection.  It is only needed when using the
     * PROCESS_HARD_KEYS option.
     */
    private boolean translateKeyDown(int keyCode, KeyEvent event) {
    	Log.d(TAG, "translateKeyDown");
        mMetaState = MetaKeyKeyListener.handleKeyDown(mMetaState,
                keyCode, event);
        int c = event.getUnicodeChar(MetaKeyKeyListener.getMetaState(mMetaState));
        mMetaState = MetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
        InputConnection ic = getCurrentInputConnection();
        if (c == 0 || ic == null) {
            return false;
        }
        
        if ((c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
            c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
        }
        
        if (mComposing.length() > 0) {
            char accent = mComposing.charAt(mComposing.length() -1 );
            int composed = KeyEvent.getDeadChar(accent, c);

            if (composed != 0) {
                c = composed;
                mComposing.setLength(mComposing.length()-1);
            }
        }
        
        onKey(c, null);
        
        return true;
    }
    
    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	Log.d(TAG, "onKeyDown");
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // The InputMethodService already takes care of the back
                // key for us, to dismiss the input method if it is shown.
                // However, our keyboard could be showing a pop-up window
                // that back should dismiss, so we first allow it to do that.
                if (event.getRepeatCount() == 0 && mInputView != null) {
                    if (mInputView.handleBack()) {
                        return true;
                    }
                }
                break;
                
            case KeyEvent.KEYCODE_DEL:
                // Special handling of the delete key: if we currently are
                // composing text for the user, we want to modify that instead
                // of let the application to the delete itself.
                if (mComposing.length() > 0) {
                    onKey(Keyboard.KEYCODE_DELETE, null);
                    return true;
                }
                break;
                
            case KeyEvent.KEYCODE_ENTER:
                // Let the underlying text editor always handle these.
                return false;
                
            default:
                // For all other keys, if we want to do transformations on
                // text being entered with a hard keyboard, we need to process
                // it and do the appropriate action.
                if (PROCESS_HARD_KEYS) {
                    if (keyCode == KeyEvent.KEYCODE_SPACE
                            && (event.getMetaState()&KeyEvent.META_ALT_ON) != 0) {
                        // A silly example: in our input method, Alt+Space
                        // is a shortcut for 'android' in lower case.
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null) {
                            // First, tell the editor that it is no longer in the
                            // shift state, since we are consuming this.
                            ic.clearMetaKeyStates(KeyEvent.META_ALT_ON);
                            keyDownUp(KeyEvent.KEYCODE_A);
                            keyDownUp(KeyEvent.KEYCODE_N);
                            keyDownUp(KeyEvent.KEYCODE_D);
                            keyDownUp(KeyEvent.KEYCODE_R);
                            keyDownUp(KeyEvent.KEYCODE_O);
                            keyDownUp(KeyEvent.KEYCODE_I);
                            keyDownUp(KeyEvent.KEYCODE_D);
                            // And we consume this event.
                            return true;
                        }
                    }
                    if (mPredictionOn && translateKeyDown(keyCode, event)) {
                        return true;
                    }
                }
        }
        
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
    	Log.d(TAG, "onKeyUp");
        // If we want to do transformations on text being entered with a hard
        // keyboard, we need to process the up events to update the meta key
        // state we are tracking.
        if (PROCESS_HARD_KEYS) {
            if (mPredictionOn) {
                mMetaState = MetaKeyKeyListener.handleKeyUp(mMetaState,
                        keyCode, event);
            }
        }
        
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Helper function to commit any text being composed in to the editor.
     */
    private void commitTyped(InputConnection inputConnection) {
    	Log.d(TAG, "commitTyped");
        if (mComposing.length() > 0) {
            inputConnection.commitText(mComposing, mComposing.length());
            mComposing.setLength(0);
            updateCandidates();
        }
    }

    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    private void updateShiftKeyState(EditorInfo attr) {
    	Log.d(TAG, "updateShiftKeyState");
        if (attr != null 
                && mInputView != null && mQwertyKeyboard == mInputView.getKeyboard()) {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
                caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
            }
            mInputView.setShifted(mCapsLock || caps != 0);
        }
    }
    
    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDownUp(int keyEventCode) {
    	Log.d(TAG, "keyDownUp");
    	
    	InputConnection ic = getCurrentInputConnection();
		if(ic == null)
			return;
    	
		ic.sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
		ic.sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }
    
    /**
     * Helper to send a character to the editor as raw key events.
     */
    private void sendKey(int keyCode) {
    	Log.d(TAG, "sendKey");
        switch (keyCode) {
            case '\n':
                keyDownUp(KeyEvent.KEYCODE_ENTER);
                Log.d(TAG, "Sending enter key");
                break;
            default:
                if (keyCode >= '0' && keyCode <= '9') {
                    keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
                } else {
                	InputConnection ic = getCurrentInputConnection();
            		if(ic == null)
            			return;
                	
                    ic.commitText(String.valueOf((char) keyCode), 1);
                }
                break;
        }
    }

    // Implementation of KeyboardViewListener
    public void onKey(int primaryCode, int[] keyCodes) {
    	Log.d(TAG, "onKey primaryCode " + primaryCode);
        if (isWordSeparator(primaryCode)) {
            // Handle separator
            if (mComposing.length() > 0) {
            	
            	InputConnection ic = getCurrentInputConnection();
        		if(ic == null)
        			return;
            	
                commitTyped(ic);
            }
            sendKey(primaryCode);
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace();
        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift();
        } else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
            handleClose();
            return;
        } else if (primaryCode == LatinKeyboardView.KEYCODE_OPTIONS) {
            // Show a menu or somethin'
        } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE
                && mInputView != null) {
            Keyboard current = mInputView.getKeyboard();
            if (current == mSymbolsKeyboard || current == mSymbolsShiftedKeyboard) {
                current = mQwertyKeyboard;
            } else {
                current = mSymbolsKeyboard;
            }
            mInputView.setKeyboard(current);
            if (current == mSymbolsKeyboard) {
                current.setShifted(false);
            }
        } else if(primaryCode == 0 && !scannerConnected) { 
        	Log.d(TAG, "Checking connection to the scanner!");
        	if(!checkConnection())
        	{
        		Log.d(TAG, "No connection the scanner!");
	        	try {
					connectToScanner();
				} catch (Exception e) {
					Log.d(TAG, e.getMessage());
					e.printStackTrace();
				}
        	}
        } else if(primaryCode == 0 && scannerConnected) {
        	Log.d(TAG, "Disconnecting the scanner!");
        	scannerThread.cancel();
        }else {
            handleCharacter(primaryCode, keyCodes);
        }
    }

    public void onText(CharSequence text) {
    	Log.d(TAG, "onText");
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        if (mComposing.length() > 0) {
            commitTyped(ic);
        }
        ic.commitText(text, 0);
        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    /**
     * Update the list of available candidates from the current composing
     * text.  This will need to be filled in by however you are determining
     * candidates.
     */
    private void updateCandidates() {
    	Log.d(TAG, "updateCandidates");
        if (!mCompletionOn) {
            if (mComposing.length() > 0) {
                ArrayList<String> list = new ArrayList<String>();
                list.add(mComposing.toString());
                setSuggestions(list, true, true);
            } else {
                setSuggestions(null, false, false);
            }
        }
    }
    
    public void setSuggestions(List<String> suggestions, boolean completions, boolean typedWordValid) {
    	Log.d(TAG, "setSuggestions");
    }
    
    private void handleBackspace() {
    	Log.d(TAG, "handleBackspace");
        final int length = mComposing.length();
        
        InputConnection ic = getCurrentInputConnection();
		if(ic == null)
			return;
        
        if (length > 1) {
            mComposing.delete(length - 1, length);
            ic.commitText(mComposing, 1);
            mComposing.setLength(0);
            updateCandidates();
        } else if (length > 0) {
            mComposing.setLength(0);
            ic.commitText("", 0);
            updateCandidates();
        } else {
            keyDownUp(KeyEvent.KEYCODE_DEL);
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    private void handleShift() {
    	Log.d(TAG, "handleShift");
        if (mInputView == null) {
            return;
        }
        
        Keyboard currentKeyboard = mInputView.getKeyboard();
        if (mQwertyKeyboard == currentKeyboard) {
            // Alphabet keyboard
            checkToggleCapsLock();
            mInputView.setShifted(mCapsLock || !mInputView.isShifted());
        } else if (currentKeyboard == mSymbolsKeyboard) {
            mSymbolsKeyboard.setShifted(true);
            mInputView.setKeyboard(mSymbolsShiftedKeyboard);
            mSymbolsShiftedKeyboard.setShifted(true);
        } else if (currentKeyboard == mSymbolsShiftedKeyboard) {
            mSymbolsShiftedKeyboard.setShifted(false);
            mInputView.setKeyboard(mSymbolsKeyboard);
            mSymbolsKeyboard.setShifted(false);
        }
    }
    
    private void handleCharacter(int primaryCode, int[] keyCodes) {
    	Log.d(TAG, "handleCharacter");
    	
    	InputConnection ic = getCurrentInputConnection();
		if(ic == null)
			return;
    	
        if (isInputViewShown()) {
            if (mInputView.isShifted()) {
                primaryCode = Character.toUpperCase(primaryCode);
            }
        }
            mComposing.append((char) primaryCode);
            ic.commitText(mComposing, 1);
            mComposing.setLength(0);
            updateShiftKeyState(getCurrentInputEditorInfo());
            updateCandidates();
    }

    private void handleClose() {
    	Log.d(TAG, "handleClose");
    	
    	InputConnection ic = getCurrentInputConnection();
		if(ic == null)
			return;
    	
        commitTyped(ic);
        requestHideSelf(0);
        mInputView.closing();
    }

    private void checkToggleCapsLock() {
    	Log.d(TAG, "checkToggleCapsLock");
        long now = System.currentTimeMillis();
        if (mLastShiftTime + 800 > now) {
            mCapsLock = !mCapsLock;
            mLastShiftTime = 0;
        } else {
            mLastShiftTime = now;
        }
    }
    
    private String getWordSeparators() {
    	Log.d(TAG, "getWordSeparators");
        return mWordSeparators;
    }
    
    public boolean isWordSeparator(int code) {
    	Log.d(TAG, "isWordSeparator");
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char)code));
    }

    public void pickDefaultCandidate() {
    	Log.d(TAG, "pickDefaultCandidate");
        pickSuggestionManually(0);
    }
    
    public void pickSuggestionManually(int index) {
    	Log.d(TAG, "pickSuggestionManually");
    }
    
    public void swipeRight() {
    	Log.d(TAG, "swipeRight");
        if (mCompletionOn) {
            pickDefaultCandidate();
        }
    }
    
    public void swipeLeft() {
    	Log.d(TAG, "swipeLeft");
        handleBackspace();
    }

    public void swipeDown() {
    	Log.d(TAG, "swipeDown");
        handleClose();
    }

    public void swipeUp() {
    	Log.d(TAG, "swipeUp");
    }
    
    public void onPress(int primaryCode) {
    	Log.d(TAG, "onPress");
    }
    
    public void onRelease(int primaryCode) {
    	Log.d(TAG, "onRelease");
    }
    
    @Override
    public void onDestroy() {
    	if(scannerThread != null)
    	{
	    	scannerThread.cancel();
	    	scannerThread.stop();
    	}
    	super.onDestroy();
    }

	@Override
	public void barcodeCallBack(final String barcode) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				try {
					InputConnection ic = getCurrentInputConnection();
					if(ic == null)
						return;
						
					String bar = barcode.replaceAll("\\r\\n|\\n|\\r", "");
					
					Log.d(TAG, "barcodeCallBack" + " (" + bar + ")");
					
					ic.commitText(bar, bar.length());
					ic = null;
				} catch (Exception e) {
					Log.d(TAG, e.getMessage());
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public void barcodeScannerDisconnect() {
		Log.d(TAG, "barcodeScannerDisconnect");
		scannerConnected = false;
		
		handler.post(new Runnable() {
			@Override
			public void run() {
				Log.d(TAG, "Running the handler");
				setScannerIcon(scannerConnected);
			}
		});
		
	}
	
	@Override
	public void barcodeScannerConnect() {
		Log.d(TAG, "scanner connected");
		
		scannerConnected = true;
		
		handler.post(new Runnable() {
			@Override
			public void run() {
				Log.d(TAG, "Running the handler");
				setScannerIcon(scannerConnected);
			}
		});
	}
	
	public void connectToScanner() throws Exception
	{
		Log.d(TAG, "Connecting to scanner!");
		if(!scannerConnected)
		{
			Log.d(TAG, "No scanner connected");
			if(this.btAdapter.isDiscovering())
				this.btAdapter.cancelDiscovery();
			
			pairedDevices = this.btAdapter.getBondedDevices();
			
	        for(BluetoothDevice bd : pairedDevices)
	        {
	        	Log.d(TAG, "Bounded dev (" + bd.getName() + ")");
	        	if(bd.getName().toLowerCase().startsWith("cs30"))
	        	{
	        		Log.d(TAG, "We found a scanner from the bounded devs");
        			Log.d(TAG, "Connecting!");
		        	scanner = bd.createRfcommSocketToServiceRecord(MY_UUID);
		        	scannerThread = new ConnectedThread(scanner, SoftKeyboard.this);
		        	scannerThread.start();
		        	break;
	        	}
	        }
		} else {
			Log.d(TAG, "We already have a scanner connected!");
			setScannerIcon(scannerConnected);
		}
	}
	
	public boolean checkConnection()
	{
		for(BluetoothDevice bd : btAdapter.getBondedDevices())
		{
			if(bd.getName().toLowerCase().startsWith("cs3070"))
			{
				Log.d(TAG, "We have a scanner!");
				if(scannerThread != null)
				{
					if(scannerThread.isAlive()) {
						Log.d(TAG, "We have an active connection!");
						scannerConnected = true;
					} else
					{
						Log.d(TAG, "The thread is dead!");
						scannerConnected = false;
					}
				} else {
					Log.d(TAG, "The thread is null");
					scannerConnected = false;
				}
			}
		}
		
		setScannerIcon(scannerConnected);
		
		return scannerConnected;
	}
	
	public void setScannerIcon(boolean online)
	{
		Log.d(TAG, "Set Icon to " + (online ? "online" : "offline"));
		
		if (mCurKeyboard != null) {
			//Log.d(TAG, "Do not have the key");
			for(Key k : mCurKeyboard.getKeys())
			{
				if(k != null)
				{
					if(k.codes != null)
					{
						if(k.codes[0] == 0)
						{
							scannerKey = k;
							scannerKey.icon = getResources().getDrawable((online ? R.drawable.scanner : R.drawable.scanner_disconnected));
						}
					}
				}
			}
		}
		
		mInputView.invalidateAllKeys();
		mInputView.invalidate();
	}
}
