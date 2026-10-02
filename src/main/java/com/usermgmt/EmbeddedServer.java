package com.usermgmt;

import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;

import java.io.File;

/**
 * Embedded Tomcat 9 Server launcher.
 * Enables running the complete web application with a single command:
 * mvn compile exec:java
 * or directly by executing this class's main method.
 */
public class EmbeddedServer {

    public static void main(String[] args) throws Exception {
        int port = 8080;
        String portProperty = System.getProperty("server.port", System.getenv("PORT"));
        if (portProperty != null && !portProperty.isEmpty()) {
            try {
                port = Integer.parseInt(portProperty);
            } catch (NumberFormatException ignored) {}
        }

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.getConnector(); // Initialize default connector

        String docBase = new File("src/main/webapp").getAbsolutePath();
        StandardContext ctx = (StandardContext) tomcat.addWebapp("", docBase);
        ctx.setParentClassLoader(EmbeddedServer.class.getClassLoader());
        ctx.setReloadable(true);

        // Configure classloader for compiled classes in target/classes
        File additionWebInfClasses = new File("target/classes");
        if (additionWebInfClasses.exists()) {
            WebResourceRoot resources = new StandardRoot(ctx);
            resources.addPreResources(new DirResourceSet(
                    resources,
                    "/WEB-INF/classes",
                    additionWebInfClasses.getAbsolutePath(),
                    "/"
            ));
            ctx.setResources(resources);
        }

        System.out.println("==================================================================");
        System.out.println("  >>> User Management Web Application - Starting Tomcat... <<<   ");
        System.out.println("  Server URL: http://localhost:" + port + "/");
        System.out.println("  Login Page: http://localhost:" + port + "/login.jsp");
        System.out.println("  REST API  : http://localhost:" + port + "/api/users");
        System.out.println("==================================================================");

        tomcat.start();
        tomcat.getServer().await();
    }
}
