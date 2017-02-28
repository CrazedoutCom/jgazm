package com.crazedout.jgazm;

import com.crazedout.jgazm.JGazm;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.Arrays;

/**
 * Created by Fredrik on 2016-04-13.
 */
public class TestJGazm {

    @Before
    public void init(){
        System.setProperty("jgazm.home","C:\\Fredriks_mapp\\java\\JGazm\\release\\");
        System.setProperty("jgazm.props","C:\\Fredriks_mapp\\java\\JGazm\\release\\bin\\user.properties");
    }

    @Test
    public void testMain() throws Exception {
        String args[] = {"-version","-help"};
        JGazm.main(args);
    }

    @Test
    public void testInputStream() throws Exception {
        String args[] = {"-stdin"};
        JGazm jgaz = new JGazm(args);
        String code = "out(\"Hello\");";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(out));
        jgaz.setInputStream(new ByteArrayInputStream(code.getBytes()));
        //new Lang(){public void execute(){};}.alert(out.toString());
        Assert.assertTrue(out.toString().startsWith("Hello"));
    }

    @Test
    public void test() throws Exception{

        String args[] = {"/?"};
        JGazm jgaz = new JGazm(args);
        jgaz.runScript("System.out.println(\"Fredrik\");", Arrays.asList("/?","-clean"));

    }

}
