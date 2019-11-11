package io.github.s5uishida.iot.device.bh1750fvi.driver;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;

/*
 * Refer to https://www.mouser.com/datasheet/2/348/bh1750fvi-e-186247.pdf
 *
 * @author s5uishida
 *
 */
public class BH1750FVIDriver {
	private static final Logger LOG = LoggerFactory.getLogger(BH1750FVIDriver.class);

	public static final byte I2C_ADDRESS_23 = 0x23;
	public static final byte I2C_ADDRESS_5C = 0x5c;

	private static final byte OPECODE_POWER_DOWN							= 0x00;
	private static final byte OPECODE_POWER_ON								= 0x01;
	private static final byte OPECODE_RESET									= 0x07;
	private static final byte OPECODE_CONTINUOUSLY_H_RESOLUTION_MODE		= 0x10;
	private static final byte OPECODE_CONTINUOUSLY_H_RESOLUTION_MODE2	= 0x11;
	private static final byte OPECODE_CONTINUOUSLY_L_RESOLUTION_MODE		= 0x13;
	private static final byte OPECODE_ONE_TIME_H_RESOLUTION_MODE			= 0x20;
	private static final byte OPECODE_ONE_TIME_H_RESOLUTION_MODE2		= 0x21;
	private static final byte OPECODE_ONE_TIME_L_RESOLUTION_MODE			= 0x23;

	private static final int SENSOR_DATA_LENGTH = 2;

	private final byte i2cAddress;
	private final I2CBus i2cBus;
	private final I2CDevice i2cDevice;
	private final String i2cName;
	private final String logPrefix;

	private final AtomicInteger useCount = new AtomicInteger(0);

	private static final ConcurrentHashMap<Integer, BH1750FVIDriver> map = new ConcurrentHashMap<Integer, BH1750FVIDriver>();

	synchronized public static BH1750FVIDriver getInstance(int i2cBusNumber, byte i2cAddress) {
		BH1750FVIDriver bh1750fvi = map.get(i2cBusNumber);
		if (bh1750fvi == null) {
			bh1750fvi = new BH1750FVIDriver(i2cBusNumber, i2cAddress);
			map.put(i2cBusNumber, bh1750fvi);
		}
		return bh1750fvi;
	}

	private BH1750FVIDriver(int i2cBusNumber, byte i2cAddress) {
		if (i2cBusNumber != I2CBus.BUS_0 && i2cBusNumber != I2CBus.BUS_1) {
			throw new IllegalArgumentException("The set " + i2cBusNumber + " is not " +
					I2CBus.BUS_0 + " or " + I2CBus.BUS_1 + ".");
		}
		if (i2cAddress == I2C_ADDRESS_23 || i2cAddress == I2C_ADDRESS_5C) {
			this.i2cAddress = i2cAddress;
		} else {
			throw new IllegalArgumentException("The set " + String.format("%x", i2cAddress) + " is not " +
					String.format("%x", I2C_ADDRESS_23) + " or " + String.format("%x", I2C_ADDRESS_5C) + ".");
		}

		i2cName = "I2C_" + i2cBusNumber + "_" + String.format("%x", i2cAddress);
		logPrefix = "[" + i2cName + "] ";

		try {
			this.i2cBus = I2CFactory.getInstance(i2cBusNumber);
			this.i2cDevice = i2cBus.getDevice(i2cAddress);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	synchronized public void open() throws IOException {
		try {
			LOG.debug(logPrefix + "before - useCount:{}", useCount.get());
			if (useCount.compareAndSet(0, 1)) {
				init();
				LOG.info(logPrefix + "opened");
			}
		} finally {
			LOG.debug(logPrefix + "after - useCount:{}", useCount.get());
		}
	}

	synchronized public void close() throws IOException {
		try {
			LOG.debug(logPrefix + "before - useCount:{}", useCount.get());
			if (useCount.compareAndSet(1, 0)) {
				i2cBus.close();
				LOG.info(logPrefix + "closed");
			}
		} finally {
			LOG.debug(logPrefix + "after - useCount:{}", useCount.get());
		}
	}

	public int getI2cBusNumber() {
		return i2cBus.getBusNumber();
	}

	public byte getI2cAddress() {
		return i2cAddress;
	}

	public String getName() {
		return i2cName;
	}

	public String getLogPrefix() {
		return logPrefix;
	}

	private void dump(byte data, String tag) {
		if (LOG.isTraceEnabled()) {
			StringBuffer sb = new StringBuffer();
			sb.append(String.format("%02x", data));
			LOG.trace(logPrefix + "{}{}", tag, sb.toString());
		}
	}

	private void dump(byte[] data, String tag) {
		if (LOG.isTraceEnabled()) {
			StringBuffer sb = new StringBuffer();
			for (byte data1 : data) {
				sb.append(String.format("%02x ", data1));
			}
			LOG.trace(logPrefix + "{}{}", tag, sb.toString().trim());
		}
	}

	private void write(byte out) throws IOException {
		try {
			dump(out, "BH1750FVI sensor command: write: ");
			i2cDevice.write(out);
		} catch (IOException e) {
			String message = logPrefix + "failed to write.";
			LOG.warn(message);
			throw new IOException(message, e);
		}
	}

	private byte[] read(int length) throws IOException {
		try {
			byte[] in = new byte[length];
			i2cDevice.read(in, 0, length);
			dump(in, "BH1750FVI sensor command: read:  ");
			return in;
		} catch (IOException e) {
			String message = logPrefix + "failed to read.";
			LOG.warn(message);
			throw new IOException(message, e);
		}
	}

	private void init() throws IOException {
		write(OPECODE_CONTINUOUSLY_H_RESOLUTION_MODE);
	}

	public float getOptical() throws IOException {
		byte[] data = read(SENSOR_DATA_LENGTH);

		return (float)((((int)(data[0] & 0xff) << 8) + (int)(data[1] & 0xff)) / 1.2);
	}

	/******************************************************************************************************************
	 * Sample main
	 ******************************************************************************************************************/
	public static void main(String[] args) throws IOException {
		BH1750FVIDriver bh1750fvi = null;
		try {
			bh1750fvi = BH1750FVIDriver.getInstance(I2CBus.BUS_1, BH1750FVIDriver.I2C_ADDRESS_23);
			bh1750fvi.open();

			while (true) {
				float value = bh1750fvi.getOptical();
				LOG.info("optical:" + value);

				Thread.sleep(10000);
			}
		} catch (InterruptedException e) {
			LOG.warn("caught - {}", e.toString());
		} catch (IOException e) {
			LOG.warn("caught - {}", e.toString());
		} finally {
			if (bh1750fvi != null) {
				bh1750fvi.close();
			}
		}
	}
}
