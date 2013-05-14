package se.chalmers.pd.playlistmanager;

import android.os.Parcel;
import android.os.Parcelable;

public class Track implements Parcelable {
	private String name;
	private String artist;
	private String spotifyUri;

	public Track() { }

	public Track(String name, String artist, String spotifyUri) {
		this.name = name;
		this.artist = artist;
		this.spotifyUri = spotifyUri;
	}

	public Track(Parcel parcel) {
		this.name = parcel.readString();
		this.artist = parcel.readString();
		this.spotifyUri = parcel.readString();
	}

	/**
	 * @return the name of the track
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return the artist
	 */
	public String getArtist() {
		return artist;
	}

	/**
	 * @return the spotifyUri
	 */
	public String getUri() {
		return spotifyUri;
	}

	/**
	 * @param the
	 *            name of the track
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @param the
	 *            name of the artist
	 */
	public void setArtist(String artist) {
		this.artist = artist;
	}

	/**
	 * @param the
	 *            spotify URI
	 */
	public void setUri(String spotifyUri) {
		this.spotifyUri = spotifyUri;
	}

	@Override
	public boolean equals(Object object) {
		if (object instanceof Track) {
			Track track = (Track) object;
			if (track.name.equals(this.name) && track.artist.equals(this.artist)
					&& track.spotifyUri.equals(this.spotifyUri))
				return true;
		}
		return false;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	/**
     * Writes the data to the parcel
     */
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(name);
		dest.writeString(artist);
		dest.writeString(spotifyUri);
	}

	/**
	 * Parcelable construction
	 */
	public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {
		public Track createFromParcel(Parcel in) {
			return new Track(in);
		}

		public Track[] newArray(int size) {
			return new Track[size];
		}
	};

}