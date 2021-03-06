package se.chalmers.pd.headunit;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

/**
 * This factory can create an connect/reconnect dialog that is shown to the user. It has a callback
 * interface which must be implemented to receive the result of the user action.
 */
public class DialogFactory {

    public interface Callback {
        public void onConnectDialogAnswer(boolean result);
    }

    /**
     * Builds a connect dialog
     *
     * @param context  the context to show the dialog in
     * @param callback the callback class
     * @return a connect dialog
     */
    public static AlertDialog buildConnectDialog(Context context, final Callback callback) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
        alertDialogBuilder.setTitle(context.getString(R.string.connection_error));
        alertDialogBuilder.setMessage(context.getString(R.string.connection_problem)).setCancelable(false)
                .setPositiveButton(context.getString(R.string.reconnect), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        callback.onConnectDialogAnswer(true);
                    }
                }).setNegativeButton(context.getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                callback.onConnectDialogAnswer(false);
                dialog.cancel();
            }
        });
        return alertDialogBuilder.create();
    }
}
