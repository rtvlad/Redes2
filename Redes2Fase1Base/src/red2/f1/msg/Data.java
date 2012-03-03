package red2.f1.msg;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Stores the data packets that are sent between client and server.
 * @author <a href="mailto:radutom.vlad@gmail.com">Radu Tom Vlad</a>
 */
public class Data implements Serializable {

	/**
	 * Generated serial ID.
	 */
	private static final long serialVersionUID = -4713171438422895201L;
	private int len = 0;
	private byte[] data = null;
	
	public Data(int size) {
		len = size;
	}
	
	public void writeData(byte[] src) {
		if (null == src) {
			return;
		}
		this.data = Arrays.copyOf(src, this.len);
	}
	
	public byte[] getData() {
		return this.data;
	}

	public int getLen() {
		return this.len;
	}
}
