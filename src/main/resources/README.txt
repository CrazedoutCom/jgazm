JGaz v.0.03

jgazm.cmd -stdin (Read code input from stdin. Single "." + ENTER quits.)
jgazm.cmd  (Read script from file).

// Same as POJO Java
@import my.pack.of.java;

Add classpath directly in the code.
@classpath C:\MyFolder\MyJar.jar

// Include other source file.
@include C:\MyFolder\include.jgaz

@import,@classpath and @include can be put anywhere in the code.

// Global varibles must be declared @global
@global int myNumber = 10;

// All methods must be annotated with @method.
@method
int myFunc(){
 return 1;
}

// Innerclasses must be annotated with @class
@class
public class Test {
 public Test(){
 }
}

@string(name="MyString"){
    Fredriks home dir is: $System.getProperty("user.dir")$
}
Equiv: @global String MyString = "Fredriks home dir is: " + System.getProperty("user.dir") + ";";

Default imported packages are:
java.util.*;
java.io.*;
java.sql.*;
com.crazedout.jgaz.*;
java.net.*
static java.util.Arrays.*;

Use -noimport to turn this off.
