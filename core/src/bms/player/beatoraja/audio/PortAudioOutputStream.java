package bms.player.beatoraja.audio;

interface PortAudioOutputStream {
	void start();

	boolean write(float[] buffer, int frames);

	void stop();

	void close();
}
