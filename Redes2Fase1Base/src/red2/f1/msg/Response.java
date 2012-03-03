package red2.f1.msg;

import java.io.Serializable;
import java.util.Map;

/**
 * A class that stores the responses from server.
 * @author <a href="mailto:radutom.vlad@gmail.com">Radu Tom Vlad</a>
 */
public class Response implements Serializable {
	/**
	 * Generated serial ID.
	 */
	private static final long serialVersionUID = 3245109145020831552L;

	public static enum Codes {
		OK, ERR
	}
	private Codes code = Codes.OK;
	private String message = null;
	private Map<String, String> files = null;
	private MsgType type = null;
	private long fileSize = 0;
	private String checksum = null;
	
	public Response(MsgType type) {
		this.type = type;
	}
	public MsgType getType() {
		return type;
	}
	public void setType(MsgType type) {
		this.type = type;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public Map<String, String> getFiles() {
		return files;
	}
	public void setFiles(Map<String, String> files) {
		this.files = files;
	}
	public Codes getCode() {
		return code;
	}
	public void setCode(Codes code) {
		this.code = code;
	}
	public long getFileSize() {
		return fileSize;
	}
	public void setFileSize(long fileSize) {
		this.fileSize = fileSize;
	}
	public String getChecksum() {
		return checksum;
	}
	public void setChecksum(String checksum) {
		this.checksum = checksum;
	}

}
