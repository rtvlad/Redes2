package red2.f1.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;

/**
 * Utilities class.
 * @author <a href="mailto:radutom.vlad@gmail.com">Radu Tom Vlad</a>
 */
public enum Util {
	/** Only instance of this class. */
	INSTANCE;
	
	/** Max length in bytes */
	public static final int MAX_DATA = 1024;
	public final static Charset SHARED_CHARSET = Charset.forName("UTF-8");
	
	
	public static String calculateFileSize(File file) {
		DecimalFormat df = new DecimalFormat();
		df.setRoundingMode(RoundingMode.HALF_DOWN);
		String scale = "B";
		long size = file.length();
		float rounded = size;
		if (size > 1024.0) {
			scale = "KB";
			rounded = (float) (size / 1024.0);
		}
		if (size > 1024 * 1024) {
			scale = "MB";
			rounded = (float) (size / (1024*1024.0));
		}
		if (size > 1024 * 1024 * 1024) {
			scale = "GB";
			rounded = (float) (size / (1024*1024*1024.0));
		}
		df.setMaximumFractionDigits(2);
		return df.format(rounded) + scale;
	}
	
	/**
	 * Modified {@link PrintStream#print(String)} from the {@link System#out} stream. 
	 * @param s
	 */
	public static synchronized void serBash(String s) {
		System.out.print("Ser# " + s);
	}
	
	/**
	 * Modified {@link PrintStream#println(String)} from the {@link System#out} stream. 
	 * @param s
	 */
	public static synchronized void serBashln(String s) {
		System.out.println("Ser# " + s);
	}
	
	/**
	 * Reads a file (using big chunks of data) and calculates its MD5 checksum.
	 * @param file a file with read access
	 * @return the checksum or null in case of error
	 */
	public static String calcMD5(File file, int chunkSize) {
		FileInputStream fis = null;
		byte[] buf = null;
		String result = null;
		try {
			fis = new FileInputStream(file);
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.reset();
			int readBytes;
			buf = new byte[chunkSize];
			do {
				readBytes = fis.read(buf);
				if (readBytes > 0) {
					md.update(buf, 0, readBytes);
				}
			} while (readBytes == chunkSize);
			fis.close();
			result = calcMD5(md.digest());
		} catch (FileNotFoundException e) {
			//TODO should be able to choose between consoles
			Util.serBashln("Error opening file '" + file.getName() + "' for reading");
			System.out.println("FileOutputStream: " + e.getMessage());
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			Util.serBashln("Error creating MessageDigest with MD5 algorithm");
			System.out.println("NoSuchAlgorithmException: " + e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			Util.serBashln("Error reading from file '" + file.getName() + "'");
			System.out.println("IOException: " + e.getMessage());
			e.printStackTrace();
		}
		return result;
	}
	/**
	 * Calculates a HEX string from a message digest, calculated using the MD5 algorithm.
	 * @param digestResult
	 * @return
	 */
	public static String calcMD5(byte[] digestResult) {
		BigInteger bigInt = new BigInteger(1,digestResult);
		return bigInt.toString(16);
	}
}
