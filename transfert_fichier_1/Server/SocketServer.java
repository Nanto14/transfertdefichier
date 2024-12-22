import java.io.*;
import java.net.*;
import java.util.Arrays;

public class SocketServer {
    private static final int PORT = 7000;
    private static final String[] SUB_SERVER_HOSTS = {"localhost", "localhost", "localhost"};
    private static final int[] SUB_SERVER_PORTS = {8081, 8082, 8083};

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Main server is running on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                 DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

                String command = in.readUTF(); // "upload", "download", "ls", or "remove"

                switch (command.toLowerCase()) {
                    case "upload":
                        handleUpload(in, out);
                        break;
                    case "download":
                        handleDownload(in, out);
                        break;
                    case "ls":
                        handleListFiles(out);
                        break;
                    case "remove":
                        handleRemoveFile(in, out);
                        break;
                    default:
                        out.writeUTF("Invalid command");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void handleUpload(DataInputStream in, DataOutputStream out) throws IOException {
            String fileName = in.readUTF();
            long fileSize = in.readLong();
            byte[] fileData = new byte[(int) fileSize];
            in.readFully(fileData);

            // Divide the file into three parts
            int partSize = fileData.length / 3;
            byte[] part1 = Arrays.copyOfRange(fileData, 0, partSize);
            byte[] part2 = Arrays.copyOfRange(fileData, partSize, 2 * partSize);
            byte[] part3 = Arrays.copyOfRange(fileData, 2 * partSize, fileData.length);

            // Send parts to sub-servers
            sendToSubServer(part1, 0, fileName + "_part1");
            sendToSubServer(part2, 1, fileName + "_part2");
            sendToSubServer(part3, 2, fileName + "_part3");

            out.writeUTF("Upload successful");
        }

        private void handleDownload(DataInputStream in, DataOutputStream out) throws IOException {
            String fileName = in.readUTF();

            // Retrieve parts from sub-servers
            byte[] part1 = retrieveFromSubServer(0, fileName + "_part1");
            byte[] part2 = retrieveFromSubServer(1, fileName + "_part2");
            byte[] part3 = retrieveFromSubServer(2, fileName + "_part3");

            // Recombine parts
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write(part1);
            outputStream.write(part2);
            outputStream.write(part3);
            byte[] fileData = outputStream.toByteArray();

            // Send the file back to the client
            out.writeLong(fileData.length);
            out.write(fileData);
        }

        private void handleListFiles(DataOutputStream out) throws IOException {
            // Liste des fichiers sur les sous-serveurs
            out.writeUTF("Files on server:");

            // Aller chercher les fichiers sur chaque sous-serveur
            for (int i = 0; i < SUB_SERVER_PORTS.length; i++) {
                try (Socket socket = new Socket(SUB_SERVER_HOSTS[i], SUB_SERVER_PORTS[i]);
                     DataOutputStream subOut = new DataOutputStream(socket.getOutputStream());
                     DataInputStream subIn = new DataInputStream(socket.getInputStream())) {

                    // Demander la liste des fichiers du sous-serveur
                    subOut.writeUTF("ls");

                    // Lire le nombre de fichiers
                    int fileCount = subIn.readInt();
                    out.writeUTF("Sub-server " + (i + 1) + ":");

                    // Lire chaque nom de fichier et l'envoyer au client
                    for (int j = 0; j < fileCount; j++) {
                        out.writeUTF(" - " + subIn.readUTF());
                    }
                }
            }
        }

        private void handleRemoveFile(DataInputStream in, DataOutputStream out) throws IOException {
            String fileName = in.readUTF();
            boolean success = true;

            // Envoyer la commande de suppression à chaque sous-serveur
            for (int i = 0; i < SUB_SERVER_PORTS.length; i++) {
                try (Socket socket = new Socket(SUB_SERVER_HOSTS[i], SUB_SERVER_PORTS[i]);
                     DataOutputStream subOut = new DataOutputStream(socket.getOutputStream());
                     DataInputStream subIn = new DataInputStream(socket.getInputStream())) {

                    subOut.writeUTF("remove");
                    subOut.writeUTF(fileName + "_part" + (i + 1));
                    String response = subIn.readUTF();

                    if (!response.contains("deleted successfully")) {
                        success = false;
                    }
                }
            }

            if (success) {
                out.writeUTF("File " + fileName + " removed successfully.");
            } else {
                out.writeUTF("Failed to remove file " + fileName + " from one or more sub-servers.");
            }
        }

        private void sendToSubServer(byte[] data, int subServerIndex, String fileName) throws IOException {
            try (Socket socket = new Socket(SUB_SERVER_HOSTS[subServerIndex], SUB_SERVER_PORTS[subServerIndex]);
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                out.writeUTF("store");
                out.writeUTF(fileName);
                out.writeInt(data.length);
                out.write(data);
            }
        }

        private byte[] retrieveFromSubServer(int subServerIndex, String fileName) throws IOException {
            try (Socket socket = new Socket(SUB_SERVER_HOSTS[subServerIndex], SUB_SERVER_PORTS[subServerIndex]);
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {

                out.writeUTF("retrieve");
                out.writeUTF(fileName);

                int size = in.readInt();
                byte[] data = new byte[size];
                in.readFully(data);
                return data;
            }
        }
    }
}

// C:/Users/ASUS/Desktop/Prog_Sys/transfert_fichier_1/Client_Files/fde.jpeg

// C:/Users/ASUS/Desktop/Prog_Sys/transfert_fichier_1/Fichier_recomposer/lalaina.jpeg