package se.chalmers.pd.headunit;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Toast;

/**
 * This class handles the control flow of the application. It implements several callback interfaces
 * from classes that it use. These callbacks are necessary since a lot of asynchronous work is being
 * done.
 *
 * This class contains methods to publish and subscribe to topics. It also contains methods
 * that handle incoming messages and parse them to actions.
 */
public class ApplicationController implements Decompresser.Callback, MqttWorker.Callback, DialogFactory.Callback {

    private static final String HTTP_LOCALHOST = "http://localhost:8080/";
    public static final String TAG = "ApplicationController";
    private final String DEFAULT_URL = "file:///android_asset/index.html";
    private final String BASEDIR = Environment.getExternalStorageDirectory() + "/www/";

    private WebView webView;
    private Context context;
    private MqttWorker mqttWorker;
    private boolean debug = true;
    private String privateTopic;

    /**
     * Sets up the initial state
     *
     * @param webView the webview which should be update
     * @param context the context that the application is operating in
     */
    public ApplicationController(WebView webView, Context context) {
        this.mqttWorker = new MqttWorker(this, context);
        this.webView = webView;
        this.context = context;
    }

    /**
     * Tries to throw away the existing connectection and create a new one.
     */
    public void reconnect() {
        mqttWorker.interrupt();
        mqttWorker = new MqttWorker(this, context);
        mqttWorker.start();
    }

    /**
     * Callback from the mqtt client when a connection status message has been received. If
     * the connection was successful, the parameter will be true. If the connection
     * was not successful, a connect dialog is shown to the user.
     *
     * @param connected true if connected
     */
    @Override
    public void onConnected(boolean connected) {
        if (connected) {
            loadStartScreen();
        } else {
            ((MainActivity) context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DialogFactory.buildConnectDialog(context, ApplicationController.this).show();
                }
            });
        }
    }

    /**
     * Callback from the connect dialog when the user has selected if they wish to connect again
     * or cancel.
     *
     * @param result is true if the user wants to connect again.
     */
    @Override
    public void onConnectDialogAnswer(boolean result) {
        if (result) {
            reconnect();
        }
    }

    /**
     * Checks if the application exists in the app folder.
     *
     * @param appName which is unique. Must be the same as the root folder of the application.
     * @return true if it does exist
     */
    public boolean applicationExists(String appName) {
        File directory = new File(BASEDIR + appName);
        if (directory.exists() && directory.isDirectory()) {
            log(TAG, "init " + appName + " exists");
            return true;
        }
        log(TAG, "init " + appName + " doesn't exist or is not a directory");
        return false;
    }

    /**
     * Unzips the data in the inputstream and places it in the app folder.
     *
     * @param inputStream  (from the zip file)
     * @param privateTopic
     * @return true if successful
     */
    public void install(InputStream inputStream, String privateTopic) {
        String unzipLocation = BASEDIR;
        Decompresser decompresser = new Decompresser(unzipLocation, this, privateTopic);
        decompresser.unzip(inputStream);
    }

    /**
     * Loads the app at the given location which is the same as the app name
     * into the web view.
     *
     * @param appName the app name (folder name of application)
     */
    public boolean start(String appName) {
        final String url = HTTP_LOCALHOST + appName + "/index.html";
        updateWebView(url);
        log(TAG, "start " + url);
        return true;
    }

    /**
     * Stops the currently running application in the webview by loading the
     * default url.
     */
    public void loadStartScreen() {
        updateWebView(DEFAULT_URL);
    }

    /**
     * Helper method that creates a runnable that can be run on the main UI
     * thread to be able to update the web view.
     *
     * @param url the url to load in the web view
     */
    private void updateWebView(final String url) {
        ((MainActivity) context).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                webView.loadUrl(url);
            }
        });
    }

    /**
     * Uninstalls the application with the given app name. Note that the app
     * name needs to be identical to the folder name of the install.
     *
     * @param appName the app name (folder name of application)
     */
    public void uninstall(String appName) {
        log(TAG, "uninstall " + appName);
        File directory = new File(BASEDIR + appName);
        if (directory.exists() && directory.isDirectory()) {
            deleteRecursive(directory);
        }
        log(TAG, "uninstall complete" + appName);
        // TODO Confirm uninstall complete
    }

    /**
     * Deletes all files and folders recursively from appDir.
     *
     * @param appDir *            the app name (folder name of application)
     */
    private void deleteRecursive(File appDir) {
        if (appDir.isDirectory()) {
            for (File child : appDir.listFiles()) {
                deleteRecursive(child);
            }
        }
        log(TAG, "deleteRecursive " + appDir.getAbsolutePath());
        appDir.delete();
    }

    /**
     * Called from the mqtt client when a message has been received.
     *
     * @param topic   the topic the message was received on
     * @param payload the payload of the message
     */
    @Override
    public void onMessage(String topic, String payload) {
        log(TAG, "onMessageReceived " + "topic: " + topic + " payload: " + payload);
        if (topic.equals(MqttWorker.TOPIC_SYSTEM)) {
            handleSystemMessage(payload);
        } else {
            handleMessage(topic, payload);
        }
    }

    /**
     * Handles the system messages like start, stop, install, uninstall and
     * exist.
     *
     * @param payload the payload from the system message
     */
    private void handleSystemMessage(String payload) {
        try {
            JSONObject responsePayload = new JSONObject();
            JSONObject json = new JSONObject(payload);
            Action action = Action.valueOf(json.getString(MqttWorker.ACTION));
            String data = json.getString(MqttWorker.ACTION_DATA);
            // TODO Add checking to make sure that action is one of a well
            // defined enum or array
            responsePayload.put(Action.action.toString(), action.toString());

            switch (action) {
                case exist:
                    // Create a custom private topic, this is hard coded in the primary device as well
                    // but must be dynamic in a real implementation.
                    privateTopic = "/" + data + "/1";
                    if (applicationExists(data)) {
                        responsePayload.put(MqttWorker.ACTION_DATA, MqttWorker.ACTION_SUCCESS);
                    } else {
                        responsePayload.put(MqttWorker.ACTION_DATA, MqttWorker.ACTION_ERROR);
                        responsePayload.put(MqttWorker.ACTION_ERROR,
                                context.getString(R.string.application_does_not_exist_payload_was) + payload);
                    }
                    break;
                case install:
                    responsePayload.put(MqttWorker.ACTION_DATA, MqttWorker.ACTION_PENDING);
                    data = mqttWorker.getApplicationRawData();
                    install(getInputStream(data), privateTopic);
                    break;
                case uninstall:
                    loadStartScreen();
                    uninstall(data);
                    responsePayload.put(MqttWorker.ACTION_DATA, MqttWorker.ACTION_SUCCESS);
                    break;
                case start:
                    if (start(data)) {
                        responsePayload.put(MqttWorker.ACTION_DATA, MqttWorker.ACTION_PENDING);
                        // Waiting for onLoadComplete before answering
                    } else {
                        responsePayload.put(MqttWorker.ACTION_DATA, MqttWorker.ACTION_ERROR);
                        responsePayload.put(MqttWorker.ACTION_ERROR,
                                context.getString(R.string.could_not_start_the_application_payload_was) + payload);
                    }
                    break;
                case stop:
                    loadStartScreen();
                    responsePayload.put(MqttWorker.ACTION_DATA, MqttWorker.ACTION_SUCCESS);
                    break;
                default:
                    break;
            }

            sendResponse(privateTopic, responsePayload);
        } catch (JSONException e) {
            Log.d(TAG, "Not a properly formatted system message, " + e.getMessage());
        }
    }

    /**
     * Handles any message that is not a system message
     *
     * @param topic   of the message
     * @param payload of the message
     */
    private void handleMessage(final String topic, final String payload) {
        log("ApplicationController:handleMessage", topic + " " + payload);
        updateWebView("javascript:onMessage(\"" + topic + "\"," + payload + ")");
    }

    /**
     * Helper method to send response after receiving a system message.
     *
     * @param topic
     * @param responsePayload
     * @throws JSONException
     */
    private void sendResponse(String topic, JSONObject responsePayload) throws JSONException {
        responsePayload.put(MqttWorker.ACTION_TYPE, MqttWorker.ACTION_RESPONSE);
        publish(topic, responsePayload.toString());
    }

    /**
     * Helper method to convert string data to input stream.
     *
     * @param data
     * @return the inputstream
     */
    private InputStream getInputStream(String data) {
        log("getInputStream", data);
        InputStream inputStream = (InputStream) new ByteArrayInputStream(Base64.decode(data, Base64.DEFAULT));
        return inputStream;
    }

    /**
     * Controller method to publish a message
     *
     * @param topic   of message
     * @param payload of message (should be stringified JSON)
     */
    public void publish(String topic, String payload) {
        log(TAG, "publish " + "topic: " + topic + " payload: " + payload);
        mqttWorker.publish(topic, payload);
    }

    /**
     * When decompressing is finished this method is executed. If the decompression
     * was successful a success message is sent. If it was not successful an error
     * message is sent.
     *
     * @param result       as a boolean, true if successful
     * @param privateTopic the topic which the result is to be published to
     */
    @Override
    public void decompressComplete(boolean result, String privateTopic) {
        JSONObject responsePayload = new JSONObject();
        try {
            responsePayload.put(MqttWorker.ACTION, MqttWorker.ACTION_INSTALL);
            if (result) {
                responsePayload.put(MqttWorker.ACTION_DATA, MqttWorker.ACTION_SUCCESS);
                log("decompressComplete", "installation complete");
            } else {
                responsePayload.put(MqttWorker.ACTION_DATA, MqttWorker.ACTION_ERROR);
                responsePayload.put(MqttWorker.ACTION_ERROR, R.string.could_not_install_the_application);
                log("decompressComplete", "failed to install");
            }
            sendResponse(privateTopic, responsePayload);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * When a page finishes loading in the web view this method creates and sends a response
     * message of success when the webview has finished loading the web application.
     *
     * @param url the url that was loaded
     */
    public void onLoadComplete(String url) {
        if (!url.equals(DEFAULT_URL)) {
            JSONObject json = new JSONObject();
            try {
                json.put(MqttWorker.ACTION, MqttWorker.ACTION_START);
                json.put(MqttWorker.ACTION_DATA, MqttWorker.ACTION_SUCCESS);
                sendResponse(privateTopic, json);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Helper method for logging
     *
     * @param tag     to log as
     * @param message to log
     */
    private void log(String tag, final String message) {
        if (debug) {
            Log.d(tag, message);
        }
    }

    /**
     * Forwards subscribe request to the mqttservice
     *
     * @param topic
     */
    public void subscribe(String topic) {
        mqttWorker.subscribe(topic);
    }

    /**
     * Forwards unsubscribe request to the mqttservice
     *
     * @param topic
     */
    public void unsubscribe(String topic) {
        mqttWorker.unsubscribe(topic);
    }

    /**
     * Shows a toast message
     *
     * @param message
     */
    public void showToast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}
