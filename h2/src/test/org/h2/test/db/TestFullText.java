/*
 * Copyright 2004-2008 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.StringTokenizer;

import org.h2.test.TestBase;

/**
 * Fulltext search tests.
 */
public class TestFullText extends TestBase {

    public void test() throws Exception {
        if (config.memory) {
            return;
        }
        test(false);
        testPerformance(false);
        String luceneFullTextClassName = "org.h2.fulltext.FullTextLucene";
        try {
            Class.forName(luceneFullTextClassName);
            test(true);
            testPerformance(true);
        } catch (ClassNotFoundException e) {
            println("Class not found, not tested: " + luceneFullTextClassName);
            // ok
        }

    }

    private void testPerformance(boolean lucene) throws Exception {
        deleteDb("fullText");
        Connection conn = getConnection("fullText");
        String prefix = lucene ? "FTL" : "FT";
        Statement stat = conn.createStatement();
        String className = lucene ? "FullTextLucene" : "FullText";
        stat.execute("CREATE ALIAS IF NOT EXISTS " + prefix + "_INIT FOR \"org.h2.fulltext." + className + ".init\"");
        stat.execute("CALL " + prefix + "_INIT()");
        stat.execute("DROP TABLE IF EXISTS TEST");
        stat.execute("CREATE TABLE TEST AS SELECT * FROM INFORMATION_SCHEMA.HELP");
        stat.execute("ALTER TABLE TEST ALTER COLUMN ID INT NOT NULL");
        stat.execute("CREATE PRIMARY KEY ON TEST(ID)");
        long time = System.currentTimeMillis();
        stat.execute("CALL " + prefix + "_CREATE_INDEX('PUBLIC', 'TEST', NULL)");
        println("create " + prefix + ": " + (System.currentTimeMillis() - time));
        PreparedStatement prep = conn.prepareStatement("SELECT * FROM " + prefix + "_SEARCH(?, 0, 0)");
        time = System.currentTimeMillis();
        ResultSet rs = stat.executeQuery("SELECT TEXT FROM TEST");
        int count = 0;
        while (rs.next()) {
            String text = rs.getString(1);
            StringTokenizer tokenizer = new StringTokenizer(text, " ()[].,;:-+*/!?=<>{}#@'\"~$_%&|");
            while (tokenizer.hasMoreTokens()) {
                String word = tokenizer.nextToken();
                if (word.length() < 10) {
                    continue;
                }
                prep.setString(1, word);
                ResultSet rs2 = prep.executeQuery();
                while (rs2.next()) {
                    rs2.getString(1);
                    count++;
                }
            }
        }
        println("search " + prefix + ": " + (System.currentTimeMillis() - time) + " count: " + count);
        stat.execute("CALL " + prefix + "_DROP_ALL()");
        conn.close();
    }

    private void test(boolean lucene) throws Exception {
        deleteDb("fullText");
        Connection conn = getConnection("fullText");
        String prefix = lucene ? "FTL_" : "FT_";
        Statement stat = conn.createStatement();
        String className = lucene ? "FullTextLucene" : "FullText";
        stat.execute("CREATE ALIAS IF NOT EXISTS " + prefix + "INIT FOR \"org.h2.fulltext." + className + ".init\"");
        stat.execute("CALL " + prefix + "INIT()");
        stat.execute("DROP TABLE IF EXISTS TEST");
        stat.execute("CREATE TABLE TEST(ID INT PRIMARY KEY, NAME VARCHAR)");
        stat.execute("INSERT INTO TEST VALUES(1, 'Hello World')");
        stat.execute("CALL " + prefix + "CREATE_INDEX('PUBLIC', 'TEST', NULL)");
        ResultSet rs;
        rs = stat.executeQuery("SELECT * FROM " + prefix + "SEARCH('Hello', 0, 0)");
        rs.next();
        check(rs.getString(1), "\"PUBLIC\".\"TEST\" WHERE \"ID\"=1");
        checkFalse(rs.next());
        rs = stat.executeQuery("SELECT * FROM " + prefix + "SEARCH('Hallo', 0, 0)");
        checkFalse(rs.next());
        stat.execute("INSERT INTO TEST VALUES(2, 'Hallo Welt')");
        rs = stat.executeQuery("SELECT * FROM " + prefix + "SEARCH('Hello', 0, 0)");
        rs.next();
        check(rs.getString(1), "\"PUBLIC\".\"TEST\" WHERE \"ID\"=1");
        checkFalse(rs.next());
        rs = stat.executeQuery("SELECT * FROM " + prefix + "SEARCH('Hallo', 0, 0)");
        rs.next();
        check(rs.getString(1), "\"PUBLIC\".\"TEST\" WHERE \"ID\"=2");
        checkFalse(rs.next());

        stat.execute("CALL " + prefix + "REINDEX()");
        rs = stat.executeQuery("SELECT * FROM " + prefix + "SEARCH('Hello', 0, 0)");
        rs.next();
        check(rs.getString(1), "\"PUBLIC\".\"TEST\" WHERE \"ID\"=1");
        checkFalse(rs.next());
        rs = stat.executeQuery("SELECT * FROM " + prefix + "SEARCH('Hallo', 0, 0)");
        rs.next();
        check(rs.getString(1), "\"PUBLIC\".\"TEST\" WHERE \"ID\"=2");
        checkFalse(rs.next());

        stat.execute("INSERT INTO TEST VALUES(3, 'Hello World')");
        stat.execute("INSERT INTO TEST VALUES(4, 'Hello World')");
        stat.execute("INSERT INTO TEST VALUES(5, 'Hello World')");

        rs = stat.executeQuery("SELECT * FROM " + prefix + "SEARCH('World', 0, 0) ORDER BY QUERY");
        rs.next();
        check(rs.getString(1), "\"PUBLIC\".\"TEST\" WHERE \"ID\"=1");
        rs.next();
        check(rs.getString(1), "\"PUBLIC\".\"TEST\" WHERE \"ID\"=3");
        rs.next();
        check(rs.getString(1), "\"PUBLIC\".\"TEST\" WHERE \"ID\"=4");
        rs.next();
        check(rs.getString(1), "\"PUBLIC\".\"TEST\" WHERE \"ID\"=5");
        checkFalse(rs.next());

        rs = stat.executeQuery("SELECT * FROM " + prefix + "SEARCH('World', 1, 0)");
        rs.next();
        check(rs.getString(1).startsWith("\"PUBLIC\".\"TEST\" WHERE \"ID\"="));
        checkFalse(rs.next());

        rs = stat.executeQuery("SELECT * FROM " + prefix + "SEARCH('World', 0, 2) ORDER BY QUERY");
        rs.next();
        check(rs.getString(1).startsWith("\"PUBLIC\".\"TEST\" WHERE \"ID\"="));
        rs.next();
        check(rs.getString(1).startsWith("\"PUBLIC\".\"TEST\" WHERE \"ID\"="));
        checkFalse(rs.next());

        rs = stat.executeQuery("SELECT * FROM " + prefix + "SEARCH('World', 2, 1) ORDER BY QUERY");
        rs.next();
        check(rs.getString(1).startsWith("\"PUBLIC\".\"TEST\" WHERE \"ID\"="));
        rs.next();
        check(rs.getString(1).startsWith("\"PUBLIC\".\"TEST\" WHERE \"ID\"="));
        checkFalse(rs.next());

        rs = stat.executeQuery("SELECT * FROM " + prefix + "SEARCH('1', 0, 0)");
        rs.next();
        check(rs.getString(1), "\"PUBLIC\".\"TEST\" WHERE \"ID\"=1");
        checkFalse(rs.next());
        conn.close();

        conn = getConnection("fullText");
        stat = conn.createStatement();
        rs = stat.executeQuery("SELECT * FROM " + prefix + "SEARCH('World', 0, 0)");

        stat.execute("CALL " + prefix + "DROP_ALL()");
        rs = stat.executeQuery("SELECT * FROM " + prefix + "SEARCH('World', 2, 1)");
        stat.execute("CALL " + prefix + "DROP_ALL()");

        conn.close();

    }
}
