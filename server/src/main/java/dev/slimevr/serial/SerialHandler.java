package dev.slimevr.serial;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortMessageListener;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.*;


public class SerialHandler implements SerialPortMessageListener {

	private final List<SerialListener> listeners = new CopyOnWriteArrayList<>();
	private SerialPort trackerPort = null;

	public void addListener(SerialListener channel) {
		this.listeners.add(channel);
	}

	public void removeListener(SerialListener l) {
		listeners.removeIf(listener -> l == listener);
	}

	public boolean openSerial() {
		if (this.isConnected()) {
			return true;
		}

		SerialPort[] ports = SerialPort.getCommPorts();
		for (SerialPort port : ports) {
			if (
				port.getDescriptivePortName().toLowerCase().contains("ch340")
					|| port.getDescriptivePortName().toLowerCase().contains("cp21")
					|| port.getDescriptivePortName().toLowerCase().contains("ch910")
					|| (port.getDescriptivePortName().toLowerCase().contains("usb")
						&& port.getDescriptivePortName().toLowerCase().contains("seri"))
			) {
				trackerPort = port;
				break;
			}
		}
		if (trackerPort == null) {
			return false;
		}
		trackerPort.setBaudRate(115200);
		trackerPort.clearRTS();
		trackerPort.clearDTR();
		if (!trackerPort.openPort()) {
			return false;
		}

		trackerPort.addDataListener(this);
		this.listeners.forEach((listener) -> listener.onSerialConnected(trackerPort));
		return true;
	}

	public void rebootRequest() {
		this.writeSerial("REBOOT");
	}

	public void factoryResetRequest() {
		this.writeSerial("FRST");
	}

	public void infoRequest() {
		this.writeSerial("GET INFO");
	}

	public void closeSerial() {
		try {
			if (trackerPort != null)
				trackerPort.closePort();
			this.listeners.forEach(SerialListener::onSerialDisconnected);
			System.out.println("Port closed okay");
			trackerPort = null;
		} catch (Exception e) {
			System.out.println("Error closing port: " + e.getMessage());
		}
	}

	private void writeSerial(String serialText) {
		if (trackerPort == null)
			return;
		OutputStream os = trackerPort.getOutputStream();
		OutputStreamWriter writer = new OutputStreamWriter(os);
		try {
			writer.append(serialText + "\n");
			writer.flush();
			this.addLog("-> " + serialText + "\n");
		} catch (IOException e) {
			addLog(e + "\n");
			e.printStackTrace();
		}
	}

	public void setWifi(String ssid, String passwd) {
		if (trackerPort == null)
			return;
		OutputStream os = trackerPort.getOutputStream();
		OutputStreamWriter writer = new OutputStreamWriter(os);
		try {
			writer.append("SET WIFI \"").append(ssid).append("\" \"").append(passwd).append("\"\n");
			writer.flush();
			this.addLog("-> SET WIFI \"" + ssid + "\" \"" + passwd.replaceAll(".", "*") + "\"\n");
		} catch (IOException e) {
			addLog(e + "\n");
			e.printStackTrace();
		}
	}

	public void addLog(String str) {
		this.listeners.forEach(listener -> listener.onSerialLog(str));
	}

	@Override
	public int getListeningEvents() {
		return SerialPort.LISTENING_EVENT_PORT_DISCONNECTED
			| SerialPort.LISTENING_EVENT_DATA_RECEIVED;
	}

	@Override
	public void serialEvent(SerialPortEvent event) {
		if (event.getEventType() == SerialPort.LISTENING_EVENT_DATA_RECEIVED) {
			byte[] newData = event.getReceivedData();
			String s = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(newData)).toString();
			this.addLog(s);
		} else if (event.getEventType() == SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) {
			this.closeSerial();
		}
	}

	public boolean isConnected() {
		return this.trackerPort != null && this.trackerPort.isOpen();
	}

	@Override
	public byte[] getMessageDelimiter() {
		return new byte[] { (byte) 0x0A };
	}

	@Override
	public boolean delimiterIndicatesEndOfMessage() {
		return true;
	}
}
