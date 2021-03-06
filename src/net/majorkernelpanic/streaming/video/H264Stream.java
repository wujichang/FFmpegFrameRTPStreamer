package net.majorkernelpanic.streaming.video;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.Semaphore;

import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.hw.EncoderDebugger;
import net.majorkernelpanic.streaming.mp4.MP4Config;
import net.majorkernelpanic.streaming.rtp.H264Packetizer;
import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.hardware.Camera.CameraInfo;
import android.media.MediaRecorder;
import android.service.textservice.SpellCheckerService.Session;
import android.util.Base64;

/**
 * A class for streaming H.264 from the camera of an android device using RTP.
 * You should use a {@link Session} instantiated with {@link SessionBuilder}
 * instead of using this class directly. Call
 * {@link #setDestinationAddress(InetAddress)},
 * {@link #setDestinationPorts(int)} and {@link #setVideoQuality(VideoQuality)}
 * to configure the stream. You can then call {@link #start()} to start the RTP
 * stream. Call {@link #stop()} to stop the stream.
 */
public class H264Stream extends VideoStream {

	public final static String TAG = "H264Stream";

	private Semaphore mLock = new Semaphore(0);
	private MP4Config mConfig;

	/**
	 * Constructs the H.264 stream. Uses CAMERA_FACING_BACK by default.
	 */
	public H264Stream() {
		this(CameraInfo.CAMERA_FACING_BACK);
	}

	/**
	 * Constructs the H.264 stream.
	 * 
	 * @param cameraId
	 *            Can be either CameraInfo.CAMERA_FACING_BACK or
	 *            CameraInfo.CAMERA_FACING_FRONT
	 * @throws IOException
	 */
	public H264Stream(int cameraId) {
		super(cameraId);
		mMimeType = "video/avc";
		mCameraImageFormat = ImageFormat.NV21;
		mVideoEncoder = MediaRecorder.VideoEncoder.H264;
		mPacketizer = new H264Packetizer();
	}

	/**
	 * Returns a description of the stream using SDP. It can then be included in
	 * an SDP file.
	 */
	public synchronized String getSessionDescription()
			throws IllegalStateException {
		if (mConfig == null)
			throw new IllegalStateException(
					"You need to call configure() first !");
		return "m=video " + String.valueOf(getDestinationPorts()[0])
				+ " RTP/AVP 96\r\n" + "a=rtpmap:96 H264/90000\r\n"
				+ "a=fmtp:96 packetization-mode=1;profile-level-id="
				+ mConfig.getProfileLevel() + ";sprop-parameter-sets="
				+ mConfig.getB64SPS() + "," + mConfig.getB64PPS() + ";\r\n";
	}

	/**
	 * Starts the stream. This will also open the camera and display the preview
	 * if {@link #startPreview()} has not aready been called.
	 */
	public synchronized void start() throws IllegalStateException, IOException {
		if (!mStreaming) {
			configure();
			byte[] pps = Base64.decode(mConfig.getB64PPS(), Base64.NO_WRAP);
			byte[] sps = Base64.decode(mConfig.getB64SPS(), Base64.NO_WRAP);
			((H264Packetizer) mPacketizer).setStreamParameters(pps, sps);
			super.start();
		}
	}

	/**
	 * Configures the stream. You need to call this before calling
	 * {@link #getSessionDescription()} to apply your configuration of the
	 * stream.
	 */
	public synchronized void configure() throws IllegalStateException,
			IOException {
		super.configure();
		mMode = mRequestedMode;
		mQuality = mRequestedQuality.clone();
		mConfig = testH264();
	}

	/**
	 * Tests if streaming with the given configuration (bit rate, frame rate,
	 * resolution) is possible and determines the pps and sps. Should not be
	 * called by the UI thread.
	 **/
	private MP4Config testH264() throws IllegalStateException, IOException {
		return testMediaCodecAPI();
	}

	@SuppressLint("NewApi")
	private MP4Config testMediaCodecAPI() throws RuntimeException, IOException {

		createCamera();
		updateCamera();

		mMode = MODE_FFMPEG_API;
		EncoderDebugger debugger = EncoderDebugger.debug(mSettings,
				mQuality.resX, mQuality.resY);
		return new MP4Config(debugger.getB64SPS(), debugger.getB64PPS());

	}
}
