/*
 * Copyright 2004-2008 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.synth;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;

import org.h2.test.TestBase;

/**
 * Standalone recovery test. A new process is started and then killed while it
 * executes random statements.
 */
public class TestKillRestart extends TestBase {

    public void test() throws Exception {
        if (config.networked) {
            return;
        }
        deleteDb("corrupt");
        String url = getURL("corrupt", true);
        // String url = getURL("corrupt;CACHE_SIZE=2048;WRITE_DELAY=0;STORAGE=TEXT", true);
        String user = getUser(), password = getPassword();

        String[] procDef = new String[] { "java", "-cp", "bin", getClass().getName(), "-url", url, "-user", user,
                "-password", password };

        int len = getSize(2, 15);
        for (int i = 0; i < len; i++) {
            Process p = Runtime.getRuntime().exec(procDef);
            // InputStream err = p.getErrorStream();
            InputStream in = p.getInputStream();
            OutputCatcher catcher = new OutputCatcher(in);
            catcher.start();
            while (true) {
                String s = catcher.readLine(5 * 60 * 1000);
                // System.out.println("> " + s);
                if (s == null) {
                    error("No reply from process");
                } else if (!s.startsWith("#")) {
                    // System.out.println(s);
                    error("Expected: #..., got: " + s);
                } else if (s.startsWith("#Running")) {
                    Thread.sleep(100);
                    printTime("killing: " + i);
                    p.destroy();
                    break;
                } else if (s.startsWith("#Fail")) {
                    error("Failed: " + s);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String driver = "org.h2.Driver";
        String url = "jdbc:h2:test", user = "sa", password = "sa";
        for (int i = 0; i < args.length; i++) {
            if ("-url".equals(args[i])) {
                url = args[++i];
            } else if ("-driver".equals(args[i])) {
                driver = args[++i];
            } else if ("-user".equals(args[i])) {
                user = args[++i];
            } else if ("-password".equals(args[i])) {
                password = args[++i];
            }
        }
        System.out.println("#Started; driver: " + driver + " url: " + url + " user: " + user + " password: " + password);
        try {
            Class.forName(driver);
            System.out.println("#Opening...");
            Connection conn = DriverManager.getConnection(url, user, password);
            Statement stat = conn.createStatement();
            stat.execute("CREATE TABLE IF NOT EXISTS TEST(ID IDENTITY, NAME VARCHAR)");
            stat.execute("CREATE TABLE IF NOT EXISTS TEST2(ID IDENTITY, NAME VARCHAR)");
            ResultSet rs = stat.executeQuery("SELECT * FROM TEST");
            while (rs.next()) {
                rs.getLong("ID");
                rs.getString("NAME");
            }
            rs = stat.executeQuery("SELECT * FROM TEST2");
            while (rs.next()) {
                rs.getLong("ID");
                rs.getString("NAME");
            }
            stat.execute("DROP ALL OBJECTS DELETE FILES");
            System.out.println("#Closing with delete...");
            conn.close();
            System.out.println("#Starting...");
            conn = DriverManager.getConnection(url, user, password);
            stat = conn.createStatement();
            stat.execute("DROP ALL OBJECTS");
            stat.execute("CREATE TABLE TEST(ID IDENTITY, NAME VARCHAR)");
            stat.execute("CREATE TABLE TEST2(ID IDENTITY, NAME VARCHAR)");
            stat.execute("CREATE TABLE TEST_META(ID INT)");
            PreparedStatement prep = conn.prepareStatement("INSERT INTO TEST(NAME) VALUES(?)");
            PreparedStatement prep2 = conn.prepareStatement("INSERT INTO TEST2(NAME) VALUES(?)");
            Random r = new Random(0);
//            Runnable stopper = new Runnable() {
//                public void run() {
//                    try {
//                        Thread.sleep(500);
//                    } catch (InterruptedException e) {
//                    }
//                    System.out.println("#Halt...");
//                    Runtime.getRuntime().halt(0);
//                }
//            };
//            new Thread(stopper).start();
            for (int i = 0; i < 2000; i++) {
                if (i == 100) {
                    System.out.println("#Running...");
                }
                if (r.nextInt(100) < 10) {
                    conn.createStatement().execute("ALTER TABLE TEST_META ALTER COLUMN ID INT DEFAULT 10");
                }
                if (r.nextBoolean()) {
                    if (r.nextBoolean()) {
                        prep.setString(1, new String(new char[r.nextInt(30) * 10]));
                        prep.execute();
                    } else {
                        prep2.setString(1, new String(new char[r.nextInt(30) * 10]));
                        prep2.execute();
                    }
                } else {
                    if (r.nextBoolean()) {
                        conn.createStatement().execute("UPDATE TEST SET NAME = NULL");
                    } else {
                        conn.createStatement().execute("UPDATE TEST2 SET NAME = NULL");
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace(System.out);
            System.out.println("#Fail: " + e.toString());
        }
    }

}
