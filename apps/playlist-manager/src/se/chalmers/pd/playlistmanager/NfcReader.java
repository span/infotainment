package se.chalmers.pd.playlistmanager;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Parcelable;
import android.util.Log;

/**
 * This class handles nfc reading and the incoming intents which it may match.
 */
public class NfcReader {

    /**
     * Interface for the activity. This must be implemented by the calling activity
     * to be able to get the result.
     */
    interface NfcCallback {
        void onNfcResult(String url);
    }

    private Context context;
    private static final String TAG = "NFCReadTag";
    private NfcAdapter nfcAdapter;
    private IntentFilter[] ndefFilter;
    private PendingIntent pendingIntent;
    private NfcCallback callback;

    /**
     * Constructor of the class, Note that you need your activity to implement
     * the NFCCAllback to be able to receive the message read in the tag.
     *
     * @param context
     */
    public NfcReader(Context context) {
        this.context = context;
        this.callback = (NfcCallback) context;
        init();
    }

    /**
     * Initialize the adapter and creates the filter required to discover NFC tags.
     */
    private void init() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(context);

        pendingIntent = PendingIntent.getActivity(
                context,
                0,
                new Intent(context, ((Activity) context).getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP), 0);

        IntentFilter tapwise = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        ndefFilter = new IntentFilter[]{tapwise};
    }

    /**
     * Call this method in the activitys onResume to enable the foreground dispatch
     */
    public void onResume() {
        if (nfcAdapter != null) {
            if (!nfcAdapter.isEnabled()) {
                Log.d("nfcAdapter", "disabled");
            } else {
                Log.d("nfcAdapter", "enabled");
                nfcAdapter.enableForegroundDispatch((Activity) context, pendingIntent,
                        ndefFilter, null);
            }
        } else {
            Log.d("onResume", "Sorry, No NFC Adapter found.");
        }
    }

    /**
     * Call this method in the Activity's onPause to disable the foreground dispatcher.
     */
    public void onPause() {
        if (nfcAdapter != null) nfcAdapter.disableForegroundDispatch((Activity) context);
    }

    /**
     * Called when a new intent has been received. Reads the messages from the intent
     * and calls back with the resulting string.
     *
     * @param intent the intent that was triggered.
     */
    public void onNewIntent(Intent intent) {
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            NdefMessage[] messages = null;
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            if (rawMsgs != null && rawMsgs.length > 0) {
                messages = new NdefMessage[rawMsgs.length];
                for (int i = 0; i < rawMsgs.length; i++) {
                    messages[i] = (NdefMessage) rawMsgs[i];
                }

                String result = "";
                byte[] payload = messages[0].getRecords()[0].getPayload();
                // this ignores the first characters "en"
                for (int b = 3; b < payload.length; b++) {
                    result += (char) payload[b];
                }
                // TODO make sure result is a url formatted string 'tcp://xxx.xxx.xxx.xxx:yyyy'
                callback.onNfcResult(result);
            }
        }
    }
}