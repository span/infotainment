package se.chalmers.pd.playlistmanager;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.util.Log;

public class ApplicationController implements MqttWorker.Callback, DialogFactory.Callback {
	

	public interface Callback {
		public void onSearchResult(ArrayList<Track> tracks);
		public void resetPlaylist();
		public void onUpdatePlaylist(Track track);
		public void onMessageAction(Action action);
	}
	
	private static final String TOPIC_PLAYLIST = "/playlist";
	private static final String TAG = "ApplicationController";
	private static final String TRACK_URI = "uri";
	private static final String TRACK_NAME = "track";
	private static final String TRACK_ARTIST = "artist";
	private static final String TRACK_LENGTH = "tracklength";
	
	private MqttWorker mqttWorker;
	private Context context;
	private Callback callback;

	public ApplicationController(Context context, Callback callback) {
		mqttWorker = new MqttWorker(this);
		mqttWorker.start();
		this.context = context;
		this.callback = callback;
	}
	
	public void reconnect() {
		mqttWorker.interrupt();
		mqttWorker = new MqttWorker(this);
		mqttWorker.start();
	}
	
	@Override
	public void onConnected(boolean connected) {
		if(connected) {
			mqttWorker.subscribe(TOPIC_PLAYLIST);
			Log.d(TAG, "Now subscribing to " + TOPIC_PLAYLIST);
		} else { 
			((MainActivity) context).runOnUiThread(new Runnable() {
				@Override
				public void run() {
					DialogFactory.buildConnectDialog(context, ApplicationController.this).show();
				}
			});
		}
	}
	
	@Override
	public void onConnectDialogAnswer(boolean result) {
		if(result) {
			reconnect();
		}
	}

	@Override
	public void onMessage(String topic, String payload) {
		try {
			JSONObject json = new JSONObject(payload);
			String action = json.getString(Action.action.toString());
			Action newAction = Action.valueOf(action);
			if(Action.add == newAction) {
				Track track = jsonToTrack(json);
				callback.onUpdatePlaylist(track);
			} else if(Action.add_all == newAction) {
				JSONArray trackArray = json.getJSONArray("data");
				callback.resetPlaylist();
				for(int i = 0; i < trackArray.length(); i++) {
					JSONObject jsonTrack = trackArray.getJSONObject(i);
					Track track = jsonToTrack(jsonTrack);
					callback.onUpdatePlaylist(track);
				}
			} else {
				callback.onMessageAction(newAction);
			}
		} catch (JSONException e) {
			Log.e(TAG, "Could not create json object from payload " + payload + " with error: " + e.getMessage());
		}
	}
	
	private Track jsonToTrack(JSONObject jsonTrack) throws JSONException {
		return new Track(jsonTrack.getString(TRACK_NAME), jsonTrack.getString(TRACK_ARTIST), jsonTrack.optString(TRACK_URI), jsonTrack.optInt(TRACK_LENGTH));
	}

	public void addTrack(Track track) {
		JSONObject message = new JSONObject();
		try {
			message.put(Action.action.toString(), Action.add.toString());
			message.put(TRACK_ARTIST, track.getArtist());
			message.put(TRACK_NAME, track.getName());
			message.put(TRACK_URI, track.getUri());
			message.put(TRACK_LENGTH, track.getLength());
			mqttWorker.publish(TOPIC_PLAYLIST, message.toString());
		} catch (JSONException e) {
			Log.e(TAG, "Could not create and send json object from track " + track.toString() + " with error: " + e.getMessage());
		}
	}
	
	public void performAction(Action action) {
		switch (action) {
		case play:
		case pause:
		case prev:
		case next:
			mqttWorker.publish(TOPIC_PLAYLIST, getJsonActionMessage(action.toString()));
			break;
		default:
			break;
		}
	}

	private String getJsonActionMessage(String action) {
		JSONObject json = new JSONObject();
		try {
			json.put(Action.action.toString(), action);
		} catch (JSONException e) {
			Log.e(TAG, "Could not create and send json object from action " + action + " with error: " + e.getMessage());
		}
		return json.toString();
	}

}
