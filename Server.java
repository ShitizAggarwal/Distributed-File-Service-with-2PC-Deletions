//Shitiz Kumar Aggarwal
//1001669578
package com.ds.thread;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.FileUtils;

//https://www.geeksforgeeks.org/introducing-threads-socket-programming-java/
public class Server {

	public static boolean CONCENSUS = true;

	// to store the user threads
	public static Map<String, ClientHandler> USER_THREAD = new HashMap<String, ClientHandler>();
	public static final String PATH = "/Users/shitizaggarwal/Desktop/Lab3/";
   //copy the file to all clients
	public static void copyToAllClients(String path, String clientKey) {
		for (Map.Entry<String, ClientHandler> entry : USER_THREAD.entrySet()) {
			if (!entry.getKey().equals(clientKey)) {
				try {
					entry.getValue().copyFile(path);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
  //delete the file after consent from other clients if consent 
  //not given file will revert back in the folder
	public static void deleteWithConsensus(String path, String clientKey) {

		for (Map.Entry<String, ClientHandler> entry : USER_THREAD.entrySet()) {
			if (!entry.getKey().equals(clientKey)) {
				entry.getValue().getConcensus(path);
			}
		}

		try {
			Thread.sleep(10000);
			if (CONCENSUS) {
				for (Map.Entry<String, ClientHandler> entry : USER_THREAD.entrySet()) {
					if (!entry.getKey().equals(clientKey)) {
						entry.getValue().deleteFile((path));
					}
				}

			} else {
				String clientFilePath = null;
				for (Map.Entry<String, ClientHandler> entry : USER_THREAD.entrySet()) {
					if (!entry.getKey().equals(clientKey)) {
						clientFilePath = entry.getValue().getClientFolderName();
						break;
					}

				}

				String newPath = clientFilePath + path.substring(path.lastIndexOf('/'));

				for (Map.Entry<String, ClientHandler> entry : USER_THREAD.entrySet()) {
					if (entry.getKey().equals(clientKey)) {
						try {
							entry.getValue().copyFile((newPath));
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						Server.CONCENSUS = true;
						return;
					}
				}

			}

		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}


	}

	public static void main(String[] args) throws IOException {
		// server is listening on port 5056
		ServerSocket ss = new ServerSocket(5056);

		// running infinite loop for getting
		// client request
		while (true) {
			Socket s = null;

			try {
				// socket object to receive incoming client requests
				s = ss.accept();

				System.out.println("A new client is connected : " + s);

				// obtaining input and out streams
				DataInputStream dis = new DataInputStream(s.getInputStream());
				DataOutputStream dos = new DataOutputStream(s.getOutputStream());

				System.out.println("Assigning new thread for this client");

				String userName = dis.readUTF();
				
				if (USER_THREAD.containsKey(userName)) {
					dos.writeUTF("user is already connected");
					dis.close();
					dos.close();
					System.out.println("connetion for previous client has been closed");
					continue;
				}

				// create a new thread object
				ClientHandler t = new ClientHandler(s, dis, dos, userName);
				USER_THREAD.put(userName, t);

				// Invoking the start() method
				t.start();

			} catch (Exception e) {
				s.close();
				e.printStackTrace();
			}
		}
	}
}

// ClientHandler class
//https://github.com/omkar9999/FileWatcherHandler/
//https://www.codejava.net/java-se/file-io/file-change-notification-example-with-watch-service-api
class ClientHandler extends Thread  {
	DateFormat fordate = new SimpleDateFormat("yyyy/MM/dd");
	DateFormat fortime = new SimpleDateFormat("hh:mm:ss");
	final DataInputStream dis;
	final DataOutputStream dos;
	final Socket s;
	private String userName;
	private String clientFolderName;
	Path path;
	WatchService watcher;

	// Constructor
	public ClientHandler(Socket s, DataInputStream dis, DataOutputStream dos, String userName) {
		this.s = s;
		this.dis = dis;
		this.dos = dos;
		this.userName = userName;
		createClientFolder(this.userName);
		path = Paths.get(clientFolderName);
		registerWatch();

	}

	// https://www.java67.com/2016/09/how-to-copy-file-from-one-location-to-another-in-java.html
	public void copyFile(String path) throws IOException {
		InputStream is = null;
		OutputStream os = null;
		try {
			File src = new File(path);
			if (!src.exists())
				return;
			File dest = new File(clientFolderName + path.substring(path.lastIndexOf('/')));
			if (dest != null && FileUtils.contentEquals(src, dest))
				return;
			is = new FileInputStream(src);
			os = new FileOutputStream(dest, false);

			// buffer size 1K
			byte[] buf = new byte[1024];

			int bytesRead;
			while ((bytesRead = is.read(buf)) > 0) {
				os.write(buf, 0, bytesRead);
			}
		} finally {
			if (is != null)
				is.close();
			if (os != null)
				os.close();
		}
	}
//to watch directory for changes
	private void registerWatch() {
		try {
			watcher = FileSystems.getDefault().newWatchService();
			path.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
			Runnable r = new Runnable() {

				@Override
				public void run() {
					while (true) {

						WatchKey key;
						try {
							key = watcher.take();
						} catch (InterruptedException ex) {
							return;
						}

						for (WatchEvent<?> event : key.pollEvents()) {
							WatchEvent.Kind<?> kind = event.kind();

							@SuppressWarnings("unchecked")
							WatchEvent<Path> ev = (WatchEvent<Path>) event;
							Path fileName = ev.context();

							System.out.println(kind.name() + ": " + fileName);

							if (kind == ENTRY_MODIFY) {
								System.out.println("My source file has changed!!!");
								Server.copyToAllClients(clientFolderName + "/" + fileName, userName);
							}
							if (kind == ENTRY_CREATE) {
								System.out.println("My source file has created!!!");
								Server.copyToAllClients(clientFolderName + "/" + fileName, userName);
							}
							if (kind == ENTRY_DELETE) {
								System.out.println("My Source File has been deleted " + fileName);
								Server.deleteWithConsensus(clientFolderName + "/" + fileName, userName);
							}
						}

						boolean valid = key.reset();
						if (!valid) {
							break;
						}
					}

				}
			};
			new Thread(r).start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void createClientFolder(String path) {
		String clientPath = Server.PATH + path;
		File file = new File(clientPath);
		if (!file.exists()) {
			file.mkdir();
		}
		clientFolderName = clientPath;
	}
//Ask for concensus from all
	public void getConcensus(String fileName) {

		if (!ifExist(fileName))
			return;

		new Thread(new Runnable() {

			@Override
			public void run() {
				// TODO Auto-generated method stub
				try {
					dos.writeUTF(fileName
							+ " has been deleted from the client directory ! \n do you want to commit or abort? \n enter 1 to commit and 0 to abort");
					String decision = dis.readUTF();
					if ("1".equals(decision)) {
						Server.CONCENSUS = Server.CONCENSUS & true;
					} else {
						Server.CONCENSUS = false;
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		}).start();

	}

	public void deleteFile(String path) {
		if (ifExist(path)) {
			File dest = new File(clientFolderName + path.substring(path.lastIndexOf('/')));
			dest.delete();
		}
	}

	private boolean ifExist(String path) {
		File dest = new File(clientFolderName + path.substring(path.lastIndexOf('/')));
		return dest.exists();
	}

	@Override
	public void run() {
		String received;
		String toreturn;
		try {
			dos.writeUTF(userName + " is connected");

				} catch (IOException e) {
			e.printStackTrace();
		}

	}

	

	public String getClientFolderName() {
		return clientFolderName;
	}
}
