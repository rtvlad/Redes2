package red2.f1.msg;

import java.io.Serializable;

/**
 * Container for the requests that are sent from the client towards the server.
 * @author <a href="mailto:radutom.vlad@gmail.com">Radu Tom Vlad</a>
 */
public class Request implements Serializable {
	/**
	 * Generated serial ID.
	 */
	private static final long serialVersionUID = 7071818095636942596L;
	private MsgType type;
	private String message;
	private long fileSize = 0;
	private String checksum = null;
	
	public Request(MsgType type) {
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
