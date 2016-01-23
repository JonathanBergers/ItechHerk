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






        String dir = "root/";
        String file_name = "index.html";

        if(!path.equals("/")){

            if(!path.endsWith("/")){
                // concat slash
                path = path.substring(1);

                if(!path.contains("/")){
                    dir = "root/";
                    file_name = path;

                }else{
                    dir = path.substring(0, path.lastIndexOf("/"));
                    file_name = path.substring(path.lastIndexOf("/"));
                }

                if(!dir.endsWith("/")) dir += "/";
                if(file_name.startsWith("/")) file_name = file_name.substring(1);
            }else{
                // client requested a directory
                // so dir is path and file name is index
                dir = path.substring(1);
            }



        }

        System.out.println("THE DIR : " + dir + " FILE: " + file_name);


        // extra check
        // cant ask no file, or a directory from server
        if(file_name.length() == 0 || path.length() == 0 || file_name.endsWith("/")){
            writeHeader("400", "text/html", out);
            return;
        }

        boolean image = false;

        String content_type = "text/html";
        if(file_name.endsWith(".css")) content_type = "text/css";
        if(file_name.endsWith(".js")) content_type = "application/x-javascript";
        if(file_name.endsWith(".jpg")){
            content_type = "image/jpg";
            image = true;
        }

        HashMap<String, String> htAccess = readAccess(dir);


        // ok, this dir is secured
        if(htAccess != null){

            System.out.println("VALID USERS: " + htAccess);

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


        Path p = Paths.get(dir, file_name);


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
    private static HashMap<String, String> readAccess(final String dir){

        try {
            Path htAccessPath = Paths.get(dir, ".htaccess");

            // no htacess file
            if(!Files.exists(htAccessPath)){
                return null;
            }

            List<String> htaccess_lines = Files.readAllLines(htAccessPath);


            Optional<String> auth_type_line_opt = htaccess_lines.stream().filter(s -> s.contains("AuthType")).findFirst();
            if(!auth_type_line_opt.isPresent()){
                // no auth type defined
                return null;
            }
            String auth_type_line = auth_type_line_opt.get();
            // only support basic auth
            if(!auth_type_line.contains("Basic")){
                return null;
            }


            Optional<String> pass_file_line_opt = htaccess_lines.stream().filter(s -> s.contains("AuthUserFile")).findFirst();
            if(!pass_file_line_opt.isPresent()){
                // wants access but no file defined
                return null;
            }

            if(pass_file_line_opt.get().trim().split(" ").length != 2){
                // no file defined in file
                return null;
            }

            // get the path
            String pass_file_path = pass_file_line_opt.get().trim().split(" ")[1];


            System.out.println("Htpwd file path " + pass_file_path);
            // no htpasswd file
            if(!Files.exists(Paths.get(dir , pass_file_path))){
                return null;
            }


            List<String> required_users = new ArrayList<>();

            // OK htacess and htpass present. get required users from htaccess
            htaccess_lines.forEach(s -> {

                final String req_user_key = "require user ";
                if(s.startsWith(req_user_key)){
                    if(s.length() > (req_user_key.length())){
                        String username = s.substring(req_user_key.length());

                        System.out.println("Found required username in htaccess file: " + username);
                        required_users.add(username.trim().toLowerCase());
                    }

                }
            });

            if(required_users.size() == 0){
                return null;
            }



            // htc passwd
            Path ht_pwd_path = Paths.get(dir, ".htpasswd");

            HashMap<String, String> u_pass_map = new HashMap<>();
            List<String> htpwd_lines = Files.readAllLines(ht_pwd_path);

            // get user credentials
            for (String s : htpwd_lines){

                String[] un_pass_split = s.trim().toLowerCase().split(":");
                System.out.println(Arrays.toString(un_pass_split));
                if(un_pass_split.length== 2){

                    final String username = un_pass_split[0].trim();
                    final String pass = un_pass_split[1].trim();

                    System.out.println("Found user in htpasswd: " + username + " " + pass);

                    if(required_users.contains(username)){
                        u_pass_map.put(username, pass);
                    }


                }

            }

            return u_pass_map;




        }catch (Exception e){
            return null;
        }

    }




}






