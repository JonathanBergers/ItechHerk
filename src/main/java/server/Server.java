package server;

import com.sun.imageio.plugins.jpeg.JPEGImageWriter;
import com.sun.imageio.plugins.jpeg.JPEGImageWriterSpi;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by jonathan on 30-12-15.
 */
public class Server extends Thread{


    public static void main(String[] args) {
        new Server().start();
    }

    public void run(){

        ServerSocket serverSocket = null;

        try {
            serverSocket = new ServerSocket(9090);
            System.out.println("ok");


        } catch (IOException e) {
            e.printStackTrace();
        }



        while(true){
            try {
                Socket socket = serverSocket.accept();
                System.out.println("socket accepted");
                read(socket);
                socket.close();

            } catch (IOException e1) {
                    e1.printStackTrace();
            }
        }




    }


    public static void read(Socket socket) throws IOException {


        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintStream out = new PrintStream(socket.getOutputStream());



        List<String> lines = new ArrayList<String>();
        String line = null;
        while ((line = in.readLine()) != null) {
            lines.add(line);
            if (line.isEmpty()) {
                break;
            }
        }


        String type = null, path = null, http_version = null, username = null, password = null;
        boolean authRequest = false;

        System.out.println(lines);
        try{
            String[] request_header = (lines.get(0).split(":"))[0].split(" ");
            String[] host_info = lines.stream().filter(s -> s.startsWith("Host")).map(s -> s.split(":")).findFirst().get();

            String[] accept_headers = lines.stream().filter(s -> s.startsWith("Accept: ")).map(s -> ((s.split(":")[1].split(",")))).findFirst().get();
            String[] accept_encoding = lines.stream().filter(s -> s.startsWith("Accept-Encoding")).map(s -> ((s.split(":")[1].split(",")))).findFirst().get();

            List<String> accept_headers_cleaned = Arrays.asList(accept_headers).stream().map(String::trim).collect(Collectors.toList());
            List<String> accept_encoding_cleaned = Arrays.asList(accept_encoding).stream().map(String::trim).collect(Collectors.toList());

            type = request_header[0];
            path = request_header[1].trim();
            http_version = request_header[2];


            // get auth
            Optional<String> authOpt = lines.stream().filter(s -> s.contains("Authorization: Basic")).findFirst();
            if(authOpt.isPresent()){

                authRequest = true;
                String authHeader = authOpt.get();
                String base64Credentials = authHeader.split(" ")[2];

                String credentials = new String(Base64.getDecoder().decode(base64Credentials),
                        Charset.forName("UTF-8"));

                System.out.println(credentials);

                final String[] values = credentials.split(":",2);
                username = values[0];
                password = values[1];
                System.out.println("Credentials : " + Arrays.toString(values));
            }


            System.out.println("request type: " + type);
            System.out.println("request path: " + path);
            System.out.println("http version: " + http_version);
            System.out.println("accept headers: " + accept_headers_cleaned);
            System.out.println("accept endoing: " + accept_encoding_cleaned);

            // invalid request
        }catch (Exception e){
            writeHeader("400", "text/html", out);
            return;
        }


        String dir = "root";
        String file_name = "index.html";
        if(!path.equals("/")){

            // concat slash
            path = path.substring(1);

            dir = path.substring(0, path.lastIndexOf("/"));
            if(!dir.endsWith("/")) dir += "/";

            System.out.println("The directory requested : " + dir);
            file_name = path.substring(path.lastIndexOf("/"));

            if(file_name.startsWith("/")) file_name = file_name.substring(1);
            System.out.println("File name requested : " + file_name);

        }




        boolean image = false;

        String content_type = "text/html";
        if(file_name.endsWith(".css")) content_type = "text/css";
        if(file_name.endsWith(".js")) content_type = "application/x-javascript";
        if(file_name.endsWith(".jpg")){
            content_type = "image/jpg";
            image = true;
        }


        Path dirPath = Paths.get(dir);

        HashMap<String, String> htAccess = readAccess(dirPath);


        // ok, this dir is secured
        if(htAccess != null){
            // the client requested this file but didnt send his auth headers
            // return the 401 status code so the client knows he has to authenticate
            if(!authRequest){
                writeHeader("401", content_type, out, "WWW-Authenticate: Basic realm=\"Restricted\"");
                return;
            }
            // empty credentials ?
            if((username == null || password == null)){
                writeHeader("400", content_type, out);
                return;
            }

            // user tried to autorizate, check if user is authenticated
            // check for access name and pass
            if(!htAccess.containsKey(username)){
                writeHeader("403", content_type, out);
                return;
            }else{
                if(!htAccess.get(username).equals(password)){
                    writeHeader("403", content_type, out);
                    return;
                }
            }
        }


        Path p = Paths.get(path);


        if(!Files.exists(p)){
            writeHeader("404", content_type, out);
            return;
        }

        writeHeader("200", content_type, out);
        writeBody(p, image, out);






    }



    private static void writeHeader(final String statusCode, final String contentType, PrintStream out, String... extra){

        // write header
        Stream<String> header_response_lines = null;
        try {
            header_response_lines = Files.lines(Paths.get("root/header.txt"));
            header_response_lines.map(s -> s.replaceAll("STATUSCODE", statusCode).replaceAll("CONTENT_TYPE", contentType)).forEach(out::println);

            if(extra != null){
                // print extra's
                Arrays.asList(extra).forEach(out::println);
            }

            out.println();


        } catch (IOException e) {
            e.printStackTrace();
        }





    }

    private static void writeBody(final Path path, boolean image, PrintStream out){

        if(image){
            BufferedImage bi = null;
            try {
                bi = ImageIO.read(Files.newInputStream(path));
                ImageIO.write(bi, "jpg", out);

            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return;

        }

        Stream<String> file_lines = null;
        try {
            file_lines = Files.lines(path);
            file_lines.forEach(out::println);
        }

        catch (IOException e1) {
            e1.printStackTrace();
        }


    }


    /**Reads the htaccess file if there is one.
     * Checks if auth is enabled for dir
     * Reads users for that htacess file (htpasswd)
     *
     * Returns the users in the file as a map with username : pass
     *
     * null if files not found, or no users found, or no auth defined in htaceess
     *
     * @return
     */
    private static HashMap<String, String> readAccess(Path path){




        return null;
    }




}






