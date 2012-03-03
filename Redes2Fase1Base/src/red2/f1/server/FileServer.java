package red2.f1.server;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import red2.f1.util.Util;

public class FileServer {

	private static File localDir;
	private static int port;
	private static ServerSocket serverSocket;
	private static ExecutorService executor;
	
	/**
	 * Main method.
	 * @param args
	 */
	public static void main(String[] args) {
		processArguments(args);
		startServer();
		executor = Executors.newCachedThreadPool();
		try {
			launchServer();
		} catch (IOException e) {
			Util.serBashln("Fatal error: " + e.getMessage());
			System.exit(1);
		} finally {
			executor.shutdown();
		}
	}

	
	private static void launchServer() throws IOException {
		while(true) {
			Socket s = serverSocket.accept();
			//Util.serBashln("Received request from " + s.getRemoteSocketAddress().toString());
			executor.execute(new FileHandler(s, localDir.getAbsolutePath()));
		}
	}

	/**
	 * Instantiates the {@link SocketServer} using the specified port.
	 * Terminates the program's execution in case of error.
	 */
	private static void startServer() {
		try {
			serverSocket = new ServerSocket(port);
		} catch (IOException e) {
			Util.serBashln("Unable to open the server socket.");
			System.out.println("IOException: " + e.getMessage());
			System.exit(1);
		}
		Util.serBashln("Server up and ready, listening on port " + port);
	}
	
	/**
	 * Checks the arguments list to assign values to the fields.
	 * @param args
	 */
	private static void processArguments(String[] args) {
		if (args.length != 2){
			Util.serBashln("Incorrect arguments.");
			Util.serBashln("Usage: java FileServer port localFolder");
			System.exit(1);
		}
		try{
			port = Integer.parseInt(args[0]);
		} catch (NumberFormatException e) {
			Util.serBashln("Number format error for port number: " + args[1]);
			System.exit(1);
		}
		localDir = new File(args[1]);
		if (! localDir.isDirectory()) {
			Util.serBashln("File " + args[1] + " is not a folder.");
			System.exit(-1);
		} else
			Util.serBashln("Incoming files will be stored into " + args[1]);
	}
}
