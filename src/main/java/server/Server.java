package server;

import com.sun.imageio.plugins.jpeg.JPEGImageWriter;
import com.sun.imageio.plugins.jpeg.JPEGImageWriterSpi;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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


        String[] request_header = (lines.get(0).split(":"))[0].split(" ");
        String[] host_info = lines.stream().filter(s -> s.startsWith("Host")).map(s -> s.split(":")).findFirst().get();

        String[] accept_headers = lines.stream().filter(s -> s.startsWith("Accept: ")).map(s -> ((s.split(":")[1].split(",")))).findFirst().get();
        String[] accept_encoding = lines.stream().filter(s -> s.startsWith("Accept-Encoding")).map(s -> ((s.split(":")[1].split(",")))).findFirst().get();

        List<String> accept_headers_cleaned = Arrays.asList(accept_headers).stream().map(String::trim).collect(Collectors.toList());
        List<String> accept_encoding_cleaned = Arrays.asList(accept_encoding).stream().map(String::trim).collect(Collectors.toList());

        String type = request_header[0];
        String path = request_header[1].trim();
        String http_version = request_header[2];

        System.out.println("request type: " + type);
        System.out.println("request path: " + path);
        System.out.println("http version: " + http_version);
        System.out.println("accept headers: " + accept_headers_cleaned);
        System.out.println("accept endoing: " + accept_encoding_cleaned);




        System.out.println(lines);

        String file_name = "index.html";
        if(!path.equals("/")){
            file_name = path.substring(path.lastIndexOf("/"), path.length());
        }

        if(path.equals("/")){
            path = "/index.html";
        }
        System.out.println(file_name);

        try{
            Path file_path = Paths.get("root" + path);
            //TODO return 404
        }



        String content_type = "text/html";
        if(path.endsWith(".css")){
            content_type = "text/css";
        }
        if(path.endsWith(".js")){
            content_type = "application/x-javascript";
        }
        if(path.endsWith(".jpg")){
            BufferedImage bi = ImageIO.read(Files.newInputStream(Paths.get("root" +path)));
            ImageIO.write(bi, "jpg", out);
            return;
        }




        final String finalContent_type = content_type;

//        out.flush();


        String status_code = "200 OK";

        Path file_path = Paths.get("root" + path);
        Stream<String> file_lines = null;
        try {
            file_lines = Files.lines(file_path);


        }catch (NoSuchFileException e){
            status_code = "404 NOT FOUND";


        }finally {

            // write header
            Stream<String> header_response_lines = Files.lines(Paths.get("root/header.txt"));
            final String finalStatus_code = status_code;
            header_response_lines.map(s -> s.replaceAll("STATUSCODE", finalStatus_code).replaceAll("CONTENT_TYPE", finalContent_type)).forEach(out::println);

            out.println();


            //write body
            file_lines.forEach(s -> out.println(s));
            out.flush();
        }










    }


    /**Processes the request, checks if the request is valid
     *
     */
    private void processRequest(){



    }

    /**
     *
     */
    private void getFile(){

    }





}






