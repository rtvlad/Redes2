package red2.f1.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import red2.f1.msg.Data;
import red2.f1.msg.MsgType;
import red2.f1.msg.Request;
import red2.f1.msg.Response;
import red2.f1.msg.Response.Codes;
import red2.f1.util.Util;

/**
 * Class that implements the client side in the FileServer application.
 * @author <a href="mailto:radutom.vlad@gmail.com">Radu Tom Vlad</a>
 */
public class FileClient {
	private static enum StreamsDirection {IN, OUT}

	private static String hostName;
	private static int hostPort;
	/** The client's working directory. Must be specified in the args list. */
	private static File localDir;
	private static Socket conn = null;
	private static BufferedReader consoleReader;
	private static ObjectOutputStream oos = null;
	private static ObjectInputStream ois = null;
	private static String opc;
	private static boolean finished = false;
	/** The filename to download|upload|delete */
	private static String target;

	private final static String delimiter = " ";

	/**
	 * Main method.
	 * @param args
	 */
	public static void main(String[] args) {
		processArguments(args);
		while (!finished) {
			readOption();
			processOption();
			closeStreamsAndConnection();
		}
		closeConsoleInputStream();
	}

	//----USER INTERFACE--------------------------------------//
	/**
	 * Displays the menu and reads the user's command.
	 */
	private static void readOption() {
		cliBashln("");
		cliBashln("Choose from one of the following options and push Enter:");
		cliBashln("1. lc (list local folder)");
		cliBashln("2. lr (list remote folder)");
		cliBashln("3. s <file_name> (send file to server)");
		cliBashln("4. r <file_name> (receive file from server)");
		cliBashln("5. dl <file_name> (delete local file)");
		cliBashln("6. dr <file_name> (delete remote file)");
		cliBashln("7. q (exit)");
		cliBash("");
		openConsoleInputStream();
		try {
			opc = consoleReader.readLine().trim();
		} catch (IOException e) {
			cliBashln("Error reading from console. Client will be terminated.");
			System.out.println("IOException: " + e.getMessage());
			opc = "q";
		}
	}
	/**
	 * Modified {@link PrintStream#print(String)} from the {@link System#out} stream. 
	 * @param s
	 */
	private static void cliBash(String s) {
		System.out.print("Cli# " + s);
	}
	/**
	 * Modified {@link PrintStream#println(String)} from the {@link System#out} stream. 
	 * @param s
	 */
	private static void cliBashln(String s) {
		System.out.println("Cli# " + s);
	}
	/**
	 * Prints the message type and the message, if not empty or null.
	 * @param res
	 */
	private static void printUnexpectedResponse(Response res) {
		cliBash("Unexpected response from server (" +
				res.getType().toString() + ")");
		if (null != res.getMessage() && !res.getMessage().trim().isEmpty())
			System.out.println(": " + res.getMessage());
		System.out.println();
		cliBashln("Please try again.");
	}
	//----COMMANDS EXECUTION---------------------------------------------//
	/**
	 * Lists the files inside the local folder together with their sizes.
	 * Any directories will not be included.
	 */
	private static void listLocalDir() {
		int files = 0;
		for (File file : localDir.listFiles()) {
			if (file.isFile()) {
				cliBashln(file.getName() + "\t" + Util.calculateFileSize(file));
				files++;
			}
		}
		cliBashln("----Successfully listed " + files +" local files from '" + localDir.getPath() + "'----");
	}
	/**
	 * Checks if the target file exists in the client's working directory
	 * and deletes it.
	 */
	private static void deleteLocalFile() {
		File file = new File(localDir.getAbsolutePath() + File.separator + target);
		if (file.exists() && file.canWrite() && file.delete()) {
			cliBashln("----Successfully deleted local file '" + file.getPath() + "'----");
		} else {
			cliBashln("----Unable to delete local file '" + file.getPath() + "'----");
		}		
	}
	/**
	 * Sends a {@link MsgType#LR} request to server. 
	 */
	private static void listRemoteDir() {
		Request r = new Request(MsgType.LR);
		if (writeRequest(r)) {
			Response res = readResponse();
			if (null == res)
				return; //error has been displayed by readResponse
			if(0 != res.getType().compareTo(MsgType.LR)) {
				printUnexpectedResponse(res);
				return;
			}
			if (null != res.getFiles() && ! res.getFiles().isEmpty())
				for (String file : res.getFiles().keySet()) {
					cliBashln(file + "\t"+ res.getFiles().get(file));
				}
			else {
				cliBash("--Empty file list from server--");
				if (null != res.getMessage() && !res.getMessage().isEmpty())
					System.out.println(": " + res.getMessage());
				System.out.println();
			}
			cliBashln("----Successfully executed 'lr' command----");
		}
	}
	/**
	 * Delete a file from server.
	 */
	private static void deleteRemoteFile() {
		Request r = new Request(MsgType.DR);
		r.setMessage(target);
		if (writeRequest(r)) {
			Response res = readResponse();
			if (null == res)
				return; //error has been displayed by readResponse
			if(0 != res.getType().compareTo(MsgType.DR)) {
				printUnexpectedResponse(res);
				return;
			}				
			if (null != res.getMessage() && !res.getMessage().isEmpty())
				cliBashln("Remote message: " + res.getMessage());
			cliBashln("----Successfully executed 'dr' command----");
		}
	}
	/**
	 * Receives a file (if available) and stores it into the local folder. 
	 */
	private static void receiveFile() {
		//check if the file already exists in the folder maintained by the client
		if (isLocalDirOK()) {
			FilenameFilter ff = new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.contentEquals(target);
				}
			};
			if (localDir.list(ff).length > 0) {
				cliBashln("File '" + target + "' already exists in local dir");
				return;
			}
		} else
			return;
		Request r = new Request(MsgType.R);
		r.setMessage(target);
		if (! writeRequest(r))
			return;
		Response res = readResponse();
		if (null == res)
			return; //error has been displayed by readResponse
		if(0 != res.getType().compareTo(MsgType.R)) {
			printUnexpectedResponse(res);
			return;
		}
		if (null != res.getMessage() && !res.getMessage().isEmpty())
			cliBashln("Remote message: " + res.getMessage());
		cliBashln("File size: " + res.getFileSize() + " bytes");
		cliBashln("File checksum: " + res.getChecksum());
		//start receiving file chunks
		String finalChecksum = res.getChecksum();
		long fileLength = res.getFileSize();
		int recvBytes = 0;
		File file = new File(localDir  + File.separator + target);
		try {
			file.createNewFile();
		} catch (IOException e) {
			cliBashln("Error creating new file '" + target + "' in local dir");
			System.out.println("IOException: " + e.getMessage());
			e.printStackTrace();
			return;
		}
		if (fileLength == 0) {
			cliBashln("----Successfully executed 's " + target +"' command----");
			return;
		}
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(file);
		} catch (FileNotFoundException e) {
			cliBashln("Error opening out stream on '" + target + "'");
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
			cliBashln("Error creating MessageDigest with MD5 algorithm");
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
			cliBashln("I/O error while writing into file '" + target + "'");
			System.out.println("IOException: " + e.getMessage());
			e.printStackTrace();
			file.delete();
			return;
		} finally {
			try {
				fos.close();
			} catch (IOException e) {
				cliBashln("Error closing stream on file '" + target + "'");
				System.out.println("IOException: " + e.getMessage());
				e.printStackTrace();
				file.delete();
				return;
			}
		}
		if (written != fileLength) {
			cliBashln("----Size failure: " + fileLength +
					" (B on source) vs " + written + " (B downloaded)");
			cliBashln("File '" + target +"' will not be saved.");
			file.delete();
			return;
		}
		String resultChecksum = Util.calcMD5(md.digest());
		if (finalChecksum.contentEquals(resultChecksum)) {
			cliBashln("----Checksums coincide----");
			cliBashln("----Successfully executed 'r " + target +"' command----");
		} else {
			cliBashln("----Checksum failure: " + finalChecksum +
					" (source) vs " + resultChecksum + " (downloaded)");
			cliBashln("File '" + target +"' will not be saved.");
			file.delete();
		}
	}

	/**
	 * Sends a file to server.
	 */
	private static void sendFile() {
		//check if the file really exists on the client's side
		if (isLocalDirOK()) {
			FilenameFilter ff = new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.contentEquals(target);
				}
			};
			if (localDir.list(ff).length == 0) {
				cliBashln("File '" + target + "' not found in local dir");
				return;
			}
		} else
			return;
		File file = new File(localDir.getAbsolutePath() + File.separator + target);
		if (! file.exists() || ! file.canRead()) {
			cliBashln("File '" + target + "' not found in local dir");
			return;
		}
		Request req = new Request(MsgType.S);
		req.setFileSize(file.length());
		req.setChecksum(Util.calcMD5(file, Util.MAX_DATA * Util.MAX_DATA));
		req.setMessage(target);
		if (null == req.getChecksum() || req.getChecksum().isEmpty()) {
			cliBashln("Error creating checksum for '" + target + "'");
			return;
		}
		cliBashln("File checksum on this side: " + req.getChecksum());
		if (! writeRequest(req))
			return;
		Response resp = readResponse();
		if (null == resp)
			return; //error has been displayed by readResponse
		if(0 != resp.getType().compareTo(MsgType.S)) {
			printUnexpectedResponse(resp);
			return;
		}
		if (null != resp.getMessage() && !resp.getMessage().isEmpty())
			cliBashln("Remote message: " + resp.getMessage());
		//can start sending Data
		byte[] buf = null;
		FileInputStream fis = null;
		boolean error = true;
		try {
			fis = new FileInputStream(file);
			cliBashln("Starting sending " + file.length() + " bytes of data to server");
			int readBytes;
			buf = new byte[Util.MAX_DATA];
			long start = System.currentTimeMillis();
			do {
				readBytes = fis.read(buf);
				if (readBytes >= 0) {
					Data dpak = new Data(readBytes);
					dpak.writeData(buf);
					sendData(dpak);
					//Util.serBashln("Sent " + readBytes + " bytes");
				}
			} while (readBytes == Util.MAX_DATA);
			cliBashln("Transfer of '" + file.getName() + 
					"' towards remote host completed in " +
					(System.currentTimeMillis() - start) +
					" miliseconds.");
			error = false;
		} catch (FileNotFoundException e) { 
			//file might have been deleted after calculating the checksum
			cliBashln("Error opening file '" + target + "' for transfer");
			System.out.println("FileNotFoundException: " + e.getMessage());
			e.printStackTrace();
			return;
		} catch (IOException e) {
			cliBashln("I/O error while transfering file '" + target + "'");
			System.out.println("IOException: " + e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				if (null != fis) fis.close();
			} catch (IOException e) {
				Util.serBashln("Unable to close input stream on file " + file.getName());
				System.out.println("IOException: " + e.getMessage());
				e.printStackTrace();
			}
		}
		if (! error)
			cliBashln("----Successfully executed 's " + target +"' command----");
		else //read any msg from server
			readResponse();
	}

	//----INTERNAL PROCESSING--------------------------------------------//
	/**
	 * Checks the arguments list to assign values to the fields.
	 * Terminates the program's execution in case of error.
	 * @param args
	 */
	private static void processArguments(String[] args) {
		if (args.length != 3){
			cliBashln("Incorrect arguments.");
			cliBashln("Usage: java FileClient hostname port localFolder");
			System.exit(1);
		}
		hostName = args[0];
		try{
			hostPort = Integer.parseInt(args[1]);
		} catch (NumberFormatException e) {
			cliBashln("Number format error for port number: " + args[1]);
			System.exit(1);
		}
		localDir = new File(args[2]);
		if (! localDir.isDirectory()) {
			cliBashln("File " + args[2] + " is not a folder.");
			System.exit(1);
		} else
			cliBashln("Incoming files will be stored into " + args[2]);
	}
	/**
	 * Given the <strong>trimmed</strong> option chosen by the user,
	 * analyzes the contents and takes the decisions of what should
	 * be done next.
	 */
	private static void processOption() {
		String[] cmd = opc.split(delimiter, 2);
		opc = cmd[0].toLowerCase();
		if (opc.contentEquals("q")) {
			finished = true;
			return;
		}
		if (opc.contentEquals("lc")) {
			listLocalDir();
			return;
		}
		if (opc.contentEquals("lr")) {
			listRemoteDir();
			return;
		}
		if (cmd.length == 2) {
			target = cmd[1];
			if (opc.contentEquals("s")) {
				sendFile();
				return;
			}
			if (opc.contentEquals("r")) {
				receiveFile();
				return;
			}
			if (opc.contentEquals("dl")) {
				deleteLocalFile();
				return;
			}
			if (opc.contentEquals("dr")) {
				deleteRemoteFile();
				return;
			}
		}
		cliBashln("Unknown command. Try again.");
		return;
	}
	/**
	 * Checks if there's any error regarding the local folder.
	 * @return true if local folder has read/write access, false in any other cases
	 */
	private static boolean isLocalDirOK() {
		if (null == localDir || ! localDir.exists() || ! localDir.isDirectory()) {
			cliBashln("Have lost access to local dir " + localDir.getAbsolutePath());
			return false;
		}
		if (! localDir.canRead() || ! localDir.canWrite()) {
			cliBashln("Can't read/write in local dir"+ localDir.getAbsolutePath());
			return false;
		}
		return true;
	}
	//----STREAMS---------------------------------------------------------//
	/**
	 * Instantiate the {@link BufferedReader} which will be used 
	 * to read from console stream {@link System#in}.
	 */
	private static void openConsoleInputStream() {
		InputStreamReader isr = new InputStreamReader(System.in, Util.SHARED_CHARSET);
		consoleReader = new BufferedReader(isr);
	}
	/**
	 * Closes the reader.
	 */
	private static void closeConsoleInputStream() {
		try {
			if (null != consoleReader) consoleReader.close();
		} catch (IOException e) {
			cliBashln("Error closing console stream.");
			System.out.println("IOException: " + e.getMessage());
		}
	}
	/**
	 * Opens the streams on the opened socket connection.
	 */
	private static void openConnectionStreams(StreamsDirection inOut) {
		if (null == conn) {
			cliBashln("Error opening "+ inOut.toString() + " connection stream:\n" + 
					"Connection to server has not been initiated or has been lost, socket is null.");
			cliBashln("You may want to restart client.");
			return;
		}
		if (0 == inOut.compareTo(StreamsDirection.IN))
			try {
				ois = new ObjectInputStream(conn.getInputStream());
			} catch (IOException e) {
				cliBashln("Error opening input stream to server.");
				System.out.println("IOException: " + e.getMessage());
				e.printStackTrace();
			}
		else
			try {
				oos = new ObjectOutputStream(conn.getOutputStream());
			} catch (IOException e) {
				cliBashln("Error opening output stream to server.");
				System.out.println("IOException: " + e.getMessage());
				e.printStackTrace();
			}
	}
	/**
	 * Closes the streams and the socket.
	 */
	private static void closeStreamsAndConnection() {
		if (null != oos)
			try {
				oos.close();
			} catch (IOException e) {
				cliBashln("Error closing output stream to server.");
				System.out.println("IOException: " + e.getMessage());
			} 
		if (null != ois)
			try {
				ois.close();
			} catch (IOException e) {
				cliBashln("Error closing input stream to server.");
				System.out.println("IOException: " + e.getMessage());
			}
		if (null != conn)
			try {
				conn.close();
			} catch (IOException e) {
				cliBashln("Error closing socket connection to server.");
				System.out.println("IOException: " + e.getMessage());
			}
	}

	//----COMMUNICATION TO REMOTE HOST-----------------------------------------//	
	/**
	 * Connects to server using the provided arguments.
	 * Terminates the program's execution in case of error.
	 * @param hostName name of remote machine
	 * @param hostPort port of remote machine
	 */
	private static void connectToServer() {
		boolean establishing = false;
		if (null == conn) {
			establishing = true;
			cliBash("Trying to establish connection to " + hostName + ":" + hostPort + " ... ");
		}
		try {
			conn = new Socket(hostName, hostPort);
		} catch (UnknownHostException e) {
			if (establishing)
				System.out.println("ERROR");
			System.out.println("UnknownHostException: " + e.getMessage());
			e.printStackTrace();
			conn = null;
		} catch (IOException e) {
			if (establishing)
				System.out.println("ERROR");
			System.out.println("IOException: " + e.getMessage());
			e.printStackTrace();
			conn = null;
		}
		if (null != conn) {
			if (establishing)
				System.out.println("OK");
		} else {
			cliBashln("Impossible to connect to " + hostName + ":" + hostPort);
			System.exit(1);
		}
	}
	/**
	 * Writes a {@link Request} to the socket's output stream.
	 * @param r the object to be written
	 * @return true if the sending finished without error.
	 */
	private static boolean writeRequest(Request r) {
		try {
			connectToServer();
			openConnectionStreams(StreamsDirection.OUT);
			oos.flush();
			oos.writeObject(r);
			oos.flush();
			return true;
		} catch (IOException e) {
			cliBashln("Error sending request to server, might be the connection has been lost.");
			System.out.println("IOException: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}
	/**
	 * Reads a {@link Response} from the socket input stream.
	 * @return the object read from server, null if not a Response or if 
	 * the response included an error code. 
	 */
	private static Response readResponse() {
		Object obj = null;
		try {
			openConnectionStreams(StreamsDirection.IN);
			obj = ois.readObject();
		} catch (IOException e) {
			cliBashln("Error reading response from server.");
			System.out.println("IOException: " + e.getMessage());
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			cliBashln("Error understanding response from server.");
			System.out.println("ClassNotFoundException: " + e.getMessage());
			e.printStackTrace();
		}
		if (null != obj && obj instanceof Response) {
			Response r = (Response)obj;
			if (null != r.getCode() && 0 == r.getCode().compareTo(Codes.ERR)) {
				cliBashln("Error response from server:");
				cliBashln(r.getMessage());
			} else return r; 
		}
		return null;
	}
	/**
	 * Reads a Data object from the opened stream.
	 * It assumes the stream has been opened.
	 * @return
	 */
	private static Data readData() {
		Object obj = null;
		try {
			obj = ois.readObject();
		} catch (IOException e) {
			cliBashln("Error reading data from server.");
			System.out.println("IOException: " + e.getMessage());
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			cliBashln("Error understanding data sent from server.");
			System.out.println("ClassNotFoundException: " + e.getMessage());
			e.printStackTrace();
		}
		if (null != obj)
			if(obj instanceof Data) {
				return (Data)obj;
			} else {
				if (obj instanceof Response) {
					Response r = (Response) obj;
					if (null != r.getCode() && 0 == r.getCode().compareTo(Codes.ERR)) {
						cliBashln("Error response from server:");
						cliBashln(r.getMessage());
					}
				}
			}
		return null;
	}
	/**
	 * Writes data object to output stream //and flushes the stream.
	 * @param data
	 * @throws IOException 
	 */
	private static void sendData(Data data) throws IOException {
		oos.writeObject(data);
		oos.flush();
		oos.reset();
	}
}
