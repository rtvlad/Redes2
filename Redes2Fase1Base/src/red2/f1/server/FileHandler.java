package red2.f1.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import red2.f1.msg.Data;
import red2.f1.msg.MsgType;
import red2.f1.msg.Request;
import red2.f1.msg.Response;
import red2.f1.msg.Response.Codes;
import red2.f1.util.Util;

/**
 * Handler for the file server.
 * @author <a href="mailto:radutom.vlad@gmail.com">Radu Tom Vlad</a>
 */
public class FileHandler implements Runnable {
	private static final String fatalError = "[Fatal error]: ";

	private Socket conn;
	private File localDir;
	private Request req = null;
	private Response resp = null;
	private ObjectInputStream ois = null;
	private ObjectOutputStream oos = null;
	private String remoteHost;

	private boolean canContinue = true; 

	private String errorMsg = null;
	private String okMsg = null;
	private boolean inErrorState = false;

	public FileHandler(Socket s, String absolutePath) {
		this.conn = s;
		this.localDir = new File(absolutePath);
		this.remoteHost = conn.getInetAddress().getHostName() + ":" + conn.getPort();
	}

	@Override
	public void run() {
		isLocalDirOK();
		openInputStream();
		readRequest();
		if (canContinue) {
			openOutputStream();
			if (sendResponse() && inErrorState) {
				Util.serBashln(errorMsg);
			}
		}
		try {
			if (null != oos) oos.close();
			if (null != ois) ois.close();
			if (null != conn) conn.close();
		} catch (IOException e) {
			Util.serBashln("Error closing streams or connection to " + remoteHost);
			System.out.println("IOException: " + e.getMessage());
			e.printStackTrace();
		}
		//System.out.println("Handler to " + remoteHost + " finished.");
	}

	/**
	 * Reads an object from the incoming connection's input stream,
	 * and if it's a {@link Request}, it processes it depending on
	 * the enclosed {@link MsgType}.
	 */
	private void readRequest() {
		Object obj = null;
		try {
			obj = ois.readObject();
		} catch (ClassNotFoundException e) {
			Util.serBashln("Error understanding client " + remoteHost);
			System.out.println("ClassNotFoundException: " + e.getMessage());
			e.printStackTrace();
			canContinue = false;
			return;
		} catch (IOException e) {
			Util.serBashln("Error reading Request from client " + remoteHost);
			System.out.println("IOException: " + e.getMessage());
			e.printStackTrace();
			canContinue = false;
			return;
		}
		if (inErrorState) 
			constructErrorResponseMsg();
		else {
			if (null != obj && obj instanceof Request) {
				req = (Request)obj;
				switch (req.getType()) {
				case LR:
					okMsg = "Received a request from "
							+ remoteHost + " to list the working dir ... ";
					resp = new Response(MsgType.LR);
					listFiles();
					break;
				case DR:
					okMsg = "Received a request from " + remoteHost + 
							" to delete '" + req.getMessage() + "' ... ";
					resp = new Response(MsgType.DR);
					deleteFile(req.getMessage());
					break;
				case R:
					okMsg = "Received a request from " + remoteHost + 
							" to send '" + req.getMessage() + "' ... ";
					resp = new Response(MsgType.R);
					sendFileToClient(req.getMessage());
					break;
				case S:
					okMsg = "Received a request from " + remoteHost + 
							" to receive '" + req.getMessage() + "' ... ";
					resp = new Response(MsgType.S);
					recvFileFromClient(req.getMessage());
					break;
				default:
					Util.serBashln("Received an unknown request from " + remoteHost);
					canContinue = false;
				}
				return;
			} //the message is not a request
			inErrorState = true; 
			errorMsg = "Unable to understand request";
			constructErrorResponseMsg();
		}
	}
	/**
	 * Writes a {@link Response} object on the incoming connection's
	 * output stream then flushes the stream;
	 */
	private boolean sendResponse() {
		try {
			oos.writeObject(resp);
			oos.flush();
			return true;
		} catch (IOException e) {
			Util.serBashln("Error writing Response to output stream towards " + remoteHost);
			System.out.println("IOException: " + e.getMessage());
			e.printStackTrace();
		}
		return false;
	}
	/**
	 * Writes data object to output stream //and flushes the stream.
	 * @param data
	 * @throws IOException 
	 */
	private void sendData(Data data) throws IOException {
		oos.writeObject(data);
		oos.flush();
		oos.reset();
	}
	/**
	 * Reads a Data object from the opened stream.
	 * It assumes the stream has been opened.
	 * @return
	 */
	private Data readData() {
		Object obj = null;
		try {
			obj = ois.readObject();
		} catch (IOException e) {
			Util.serBashln("Error reading data from " + remoteHost);
			System.out.println("IOException: " + e.getMessage());
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			Util.serBashln("Error understanding data sent from " + remoteHost);
			System.out.println("ClassNotFoundException: " + e.getMessage());
			e.printStackTrace();
		}
		if (null != obj)
			if(obj instanceof Data) {
				return (Data)obj;
			}
		return null;
	}
	//----PROCESSING COMMANDS--------------------------------//
	private void sendFileToClient(String target) {
		if (!isLocalDirOK()) {
			constructErrorResponseMsg();
			return;
		}
		File file = new File(localDir.getAbsolutePath() + File.separator + target);
		if (! file.exists() || ! file.canRead()) {
			inErrorState = true;
			errorMsg = "File '" + target + "' not found";
			resp.setMessage(errorMsg);
			resp.setCode(Codes.ERR);
			Util.serBashln(okMsg + "ERROR");
			return;
		}
		resp.setMessage("File '" + target + "' exists, ready for transfer");
		resp.setFileSize(file.length());
		resp.setChecksum(Util.calcMD5(file, Util.MAX_DATA * Util.MAX_DATA));
		if (null == resp.getChecksum() || resp.getChecksum().isEmpty()) {
			inErrorState = true;
			errorMsg = "Error creating checksum for '" + target + "'";
			resp.setCode(Codes.ERR);
			resp.setMessage(errorMsg);
			Util.serBashln(okMsg + "ERROR");
			return;
		}
		Util.serBashln("File checksum on this side: " + resp.getChecksum());
		canContinue = false;
		if (! openOutputStream() || ! sendResponse()) {
			Util.serBashln(okMsg + "ERROR");
			return;
		}
		//can start sending Data
		byte[] buf = null;
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			Util.serBashln(okMsg + "OK");
			Util.serBashln("Starting sending data to " + remoteHost);
			int readBytes;
			buf = new byte[Util.MAX_DATA];
			long start = System.currentTimeMillis();
			do { //TODO the read should be by itself in a try-catch
				readBytes = fis.read(buf);
				if (readBytes >= 0) {
					Data dpak = new Data(readBytes);
					dpak.writeData(buf);
					sendData(dpak);
					//Util.serBashln("Sent " + readBytes + " bytes");
				}
			} while (readBytes == Util.MAX_DATA);
			Util.serBashln("Transfer of '" + file.getName() + 
					"' towards " + remoteHost + " completed in " +
					(System.currentTimeMillis() - start) +
					" miliseconds.");
		} catch (FileNotFoundException e) { 
			//file might have been deleted after calculating the checksum
			inErrorState = true;
			errorMsg = "Error opening file '" + target + "' for transfer";
			System.out.println("FileNotFoundException: " + e.getMessage());
			e.printStackTrace();
			resp = new Response(MsgType.ERR);
			resp.setCode(Codes.ERR);
			resp.setMessage(errorMsg);
			canContinue = true; //try and send the reason to client
		} catch (IOException e) {
			inErrorState = true;
			errorMsg = "I/O error while transfering file '" + target + "'";
			System.out.println("IOException: " + e.getMessage());
			e.printStackTrace();
			resp = new Response(MsgType.ERR);
			resp.setCode(Codes.ERR);
			resp.setMessage(errorMsg);
			canContinue = true; //try and send the reason to client
		} finally {
			try {
				if (null != fis) fis.close();
			} catch (IOException e) {
				Util.serBashln("Unable to close input stream on file " + file.getName());
				System.out.println("IOException: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	private void recvFileFromClient(String target) {
		if (!isLocalDirOK()) {
			constructErrorResponseMsg();
			return;
		}
		File file = new File(localDir.getAbsolutePath() + File.separator + target);
		if (file.exists()) {
			inErrorState = true;
			errorMsg = "File '" + target + "' already exists";
			resp.setMessage(errorMsg);
			resp.setCode(Codes.ERR);
			Util.serBashln(okMsg + "ERROR");
			return;
		}
		//start receiving file chunks
		String finalChecksum = req.getChecksum();
		long fileLength = req.getFileSize();
		int recvBytes = 0;
		resp.setMessage("Can accept file '" + target + "'");
		if (! openOutputStream() || ! sendResponse()) {
			Util.serBashln(okMsg + "ERROR");
			return;
		}
		try {
			file.createNewFile();
		} catch (IOException e) {
			inErrorState = true;
			errorMsg = "Error creating new empty file '" + target + "'";
			System.out.println("IOException: " + e.getMessage());
			e.printStackTrace();
			return;
		}

		if (fileLength == 0) {
			Util.serBashln(okMsg + "OK");
			canContinue = false;
			return;
		}
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(file);
		} catch (FileNotFoundException e) {
			inErrorState = true;
			errorMsg = "Error opening out stream on '" + target + "'";
			System.out.println("FileNotFoundException: " + e.getMessage());
			e.printStackTrace();
			file.delete();
			return;
		}
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("MD5");
			md.reset();
		} catch (NoSuchAlgorithmException e) {
			inErrorState = true;
			errorMsg = "Error creating MessageDigest with MD5 algorithm";
			System.out.println("NoSuchAlgorithmException: " + e.getMessage());
			e.printStackTrace();
			file.delete();
			return;
		}
		int chunkSize = 0;
		int written = 0;
		try {
			do {
				Data data = readData();
				if (null == data)
					break;
				if (data.getLen() == 0)
					break;
				if (0 == chunkSize)
					chunkSize = data.getLen();
				md.update(data.getData(), 0, data.getLen());
				fos.write(data.getData(), 0, data.getLen());
				written += data.getLen();
				recvBytes = data.getLen();
			} while ((chunkSize > 0 && recvBytes == chunkSize) && written < fileLength);
		} catch (IOException e) {
			inErrorState = true;
			errorMsg = "I/O error while writing into file '" + target + "'";
			System.out.println("IOException: " + e.getMessage());
			e.printStackTrace();
			file.delete();
			return;
		} finally {
			try {
				fos.close();
			} catch (IOException e) {
				inErrorState = true;
				errorMsg = "Error closing stream on file '" + target + "'";
				System.out.println("IOException: " + e.getMessage());
				e.printStackTrace();
				file.delete();
				return;
			}
		}
		if (written != fileLength) {
			inErrorState = true;
			errorMsg = "Size failure: " + fileLength +
					" (B on source) vs " + written + " (B received)";
			Util.serBashln("File '" + target +"' will not be saved.");
			file.delete();
			return;
		}
		String resultChecksum = Util.calcMD5(md.digest());
		if (! finalChecksum.contentEquals(resultChecksum)) {
			inErrorState = true;
			errorMsg = "Checksum failure: " + finalChecksum +
					" (source) vs " + resultChecksum + " (received)";
			Util.serBashln("File '" + target +"' will not be saved.");
			file.delete();
			return;
		} else {
			Util.serBashln(okMsg + "OK");
			canContinue = false;
		}
	}



	private void deleteFile(String target) {
		if (!isLocalDirOK())
			constructErrorResponseMsg();
		else {
			File file = new File(localDir.getAbsolutePath() + File.separator + target);
			if (file.exists() && file.canWrite() && file.delete()) {
				resp.setMessage("File '" + target + "' deleted.");
				Util.serBashln(okMsg + "OK");
			} else {
				inErrorState = true;
				errorMsg = "Can not delete file '" + target + "'";
				resp.setCode(Codes.ERR);
				resp.setMessage(errorMsg);
				Util.serBashln(okMsg + "ERROR");
			}
		}
	}
	private void listFiles() {
		if (!isLocalDirOK())
			constructErrorResponseMsg();
		else {
			Map<String, String> files = new HashMap<String, String>();
			for (File file : localDir.listFiles()) {
				if (file.isFile()) {
					files.put(file.getName(), Util.calculateFileSize(file));
				}
			}
			resp.setFiles(files);
			Util.serBashln(okMsg + "OK");
		}
	}


	//----MISC METHODS--------------------------------------//
	private void constructErrorResponseMsg() {
		resp = new Response(MsgType.ERR);
		resp.setCode(Codes.ERR);
		resp.setMessage(errorMsg);
	}

	private boolean openOutputStream() {
		if (null == oos ) {
			try {
				oos = new ObjectOutputStream(conn.getOutputStream());
				oos.flush();
				return true;
			} catch (IOException e) {
				Util.serBashln("Error opening output stream to client " + remoteHost);
				System.out.println("IOException: " + e.getMessage());
				e.printStackTrace();
			}
			return false;
		} else
			return true; //already opened
	}
	private void openInputStream() {
		if (null == ois)
			try {
				ois = new ObjectInputStream(conn.getInputStream());
			} catch (IOException e) {
				Util.serBashln("Error opening input stream to client " + remoteHost);
				System.out.println("IOException: " + e.getMessage());
				e.printStackTrace();
				return;
			}
	}
	/**
	 * Checks if there's any error regarding the local folder.
	 * @return true if there
	 */
	private boolean isLocalDirOK() {
		if (null == localDir || ! localDir.exists() || ! localDir.isDirectory()) {
			errorMsg = fatalError + "Local dir not found.";
			inErrorState = true;
			return false;
		}
		if (! localDir.canRead() || ! localDir.canWrite()) {
			errorMsg = fatalError + "Local dir not accessible to read/write.";
			inErrorState = true;
			return false;
		}
		return true;
	}
}
