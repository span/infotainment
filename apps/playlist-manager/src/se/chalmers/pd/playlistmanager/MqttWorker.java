package se.chalmers.pd.playlistmanager;

import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttDefaultFilePersistence;
import org.eclipse.paho.client.mqttv3.MqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.MqttTopic;

import android.os.Environment;
import android.util.Log;

/**
 * This thread launches an MQTT client and subscribes to the systems basic
 * topics. When a message is received, it broadcasts the message using custom
 * intent filters.
 * 
 */
public class MqttWorker extends Thread {

	private static final String STORAGE_DIRECTORY = "/infotainment/";
	private static final String WORKER_NAME = "MqttWorker";
	private static final String BROKER = "tcp://192.168.43.147:1883";
	private static final String CLIENT_NAME = "playlistmanager";

	private MqttClient mqttClient;
	private Callback callback;

	public interface Callback {
		public void onMessage(String topic, String payload);

		public void onConnected(boolean connected);
	}

	public MqttWorker(Callback callback) {
		this.callback = callback;
	}

	@Override
	public void run() {
		try {
			// Sets up the client and subscribes to topics
			String tmpDir = Environment.getExternalStorageDirectory() + STORAGE_DIRECTORY;
			MqttDefaultFilePersistence dataStore = new MqttDefaultFilePersistence(tmpDir);
			mqttClient = new MqttClient(BROKER, CLIENT_NAME, dataStore);
			mqttClient.setCallback(new CustomMqttCallback());
			if(!connect()) {
				callback.onConnected(false);
				return;
			}
		} catch (MqttException e) {
			e.printStackTrace();
		}
	}

	private boolean connect() {
		boolean connected = false;
		if (mqttClient != null) {
			try {
				mqttClient.connect();
				notifyOnConnected();
				connected = true;
			} catch (MqttSecurityException e) {
				Log.e(WORKER_NAME, "Could not connect to the broker " + e.getMessage());
			} catch (MqttException e) {
				Log.e(WORKER_NAME, "Could not connect to the broker " + e.getMessage());
			}
		}
		return connected;
	}

	/**
	 * Publishes a message on the given topic.
	 * 
	 * @param topic
	 * @param message
	 *            should be stringified JSON
	 */
	public void publish(String topic, String message) {
		Log.d(WORKER_NAME, "publishing topic " + topic + " with message " + message);
		try {
			MqttMessage payload = new MqttMessage(message.getBytes());
			mqttClient.getTopic(topic).publish(payload);
		} catch (MqttPersistenceException e) {
			e.printStackTrace();
		} catch (MqttException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Subscribes to the given topic
	 * 
	 * @param topic
	 */
	public void subscribe(String topic) {
		try {
			mqttClient.subscribe(topic, 2);
		} catch (MqttSecurityException e) {
			e.printStackTrace();
		} catch (MqttException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Unsubscribes from the given topic
	 * 
	 * @param topic
	 */
	public void unsubscribe(String topic) {
		try {
			mqttClient.unsubscribe(topic);
		} catch (MqttException e) {
			e.printStackTrace();
		}
	}

	private void notifyOnConnected() {
		callback.onConnected(true);
	}

	/**
	 * Called when messages are received. Filters out data from installation
	 * messages since it is too much to pass around as Strings.
	 */
	class CustomMqttCallback implements MqttCallback {

		@Override
		public void messageArrived(MqttTopic topic, MqttMessage message) {
			String payload = message.toString();
			String stringTopic = topic.toString();
			callback.onMessage(stringTopic, payload);
			Log.d(WORKER_NAME, "messageArrived" + "topic:" + stringTopic + ", message:" + payload);
		}

		@Override
		public void deliveryComplete(MqttDeliveryToken token) {
			Log.d(WORKER_NAME, "deliveryComplete " + "token:" + token);
		}

		@Override
		public void connectionLost(Throwable cause) {
			Log.d(WORKER_NAME, "connectionLost " + "cause:" + cause.toString());
		}
	}
}
