html = "HTTP 1.1/200 OK\r\n"
     + "Server: Krine Web Server/1.0;\r\n"
     + "Content-Type: text/html;\r\n"
     + "Content-Length: 22;\r\n"
     + "\r\n"
     + "Hello Krine Web Server";

ss = new ServerSocket(8080);
s = null;

println("Listen on 0.0.0.0:8080");

while (true) {
    s = ss.accept();
    println("Accepted new client");
    
    try {
        s.getOutputStream().write(html.getBytes());
    } catch (Exception e) {
        e.printStackTrace();
    } finally {
        if (s != null) {
            s.close();
        }
    }
}
