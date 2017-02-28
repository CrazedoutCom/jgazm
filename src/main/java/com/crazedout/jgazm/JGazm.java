package com.crazedout.jgazm;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * @author Fredrik Roos
 *
 */

public final class JGazm {

    public List<String> flags = new ArrayList<String>();
    List<String> imports = new ArrayList<String>();
    private StringBuilder methods = new StringBuilder();
    private List<String> classes = new ArrayList<String>();
    private List<String> globals = new ArrayList<String>();
    private List<String> classpaths = new ArrayList<String>();
    private List<String> resources = new ArrayList<String>();
    private BufferedReader reader;
    private URLClassLoader urlCl;
    private String currentTemplate;
    private String[] args;
    private Properties props;
    private Properties userProps;
    private String className = null;
    private String userDir = null;
    private PrintStream dumpPrintStream = null;
    private PrintStream stdout,stderr;

    public static final String JGAZM_HOME = "jgazm.home";
    public static final String JGAZM_PROPS = "jgazm.props";
    public static final String JGAZM_WORKDIR = "jgazm.workdir";

    private String jGazmVersion = "0.0";

    public JGazm(){
        this(new String[0]);
    }

    public JGazm(String argv[]){
        args = argv;
        stdout = System.out;
        stderr = System.err;

        try {
            props = new Properties();
            props.load(getClass().getResourceAsStream("/jgazm.properties"));
            jGazmVersion = props.getProperty("jgazm.version");
            userProps = JGazm.getUserProperties();
            if(userProps!=null){
            String ucp = null;
            if((ucp=userProps.getProperty("jgazm.user.classpath"))!=null){
                    String[] ss = ucp.split(",");
                    for(String s:ss) {
                        addClasspath(s);
                    }
                }
            }
        }catch(IOException ex){
            stderr.println("WARN: Can't read jgazm.properties.");
        }
    }

    public static Properties getUserProperties() throws IOException {

        if(System.getProperty(JGAZM_PROPS)!=null){
            File fi = new File(System.getProperty(JGAZM_PROPS));
            if(fi.exists()) {
                Properties userProps = new Properties();
                userProps.load(new FileInputStream(fi));
                return userProps;
            }
        }
        return null;
    }

    public void runJava(String javaFile, List<String> flags) throws Exception {
                this.flags=flags;
                setInputFile(javaFile);
    }

    public void runScript(StringBuffer code, List<String> flags) throws Exception {
        File temp = createTemp(code);
        this.flags=flags;
        setInputFile(temp.getAbsolutePath());
        compile(temp.getAbsolutePath());
    }

    public void runScript(String code, List<String> flags) throws Exception {
        this.runScript(new StringBuffer(code),flags);
    }

    public String getVersion(){
        return jGazmVersion;
    }

    public void setInputStream(InputStream stream) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(stream));
        String line = null;
        StringBuffer b = new StringBuffer("");
        while((line=in.readLine())!=null){
            if(line.trim().equals(".")){
                break;
            }
            b.append(line + Lang.EOL);
        }
        in.close();
        File temp = createTemp(b);
        setInputFile(temp.getAbsolutePath());
        compile(temp.getAbsolutePath());
    }

    public void setInputFile(String file) throws IOException {

        if(flags.contains("-java")){
            String f = file;
            File jf = new File(file);
            if(!jf.isAbsolute()){
                f = System.getProperty("user.dir") + File.separatorChar + file;
            }
            javaCompile(f);
            String cs = f.substring(0,f.indexOf(".java"));
            File cf = new File(cs + ".class");
            try{
                if(!flags.contains("-check")){
                    invoke(cf,cf.getName().substring(0,cf.getName().indexOf(".class")),true);
                }
             }catch(Exception ex){
                ex.printStackTrace();
            }
        }else{
        // Parse for @include //
            StringBuilder buffer = new StringBuilder();
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String line = null;
            while((line=reader.readLine())!=null){
                if(line.trim().startsWith("@include ")){
                    String inc = line.substring(9).trim().replace("\"","").replace(";","");
                    doInclude(inc,buffer);
                }else{
                    if(line.trim().endsWith(")")){
                        buffer.append(line);
                    }else{
                        buffer.append(line + Lang.EOL);
                    }
                }
            }
            reader = new BufferedReader(new StringReader(buffer.toString()));
            doImports();
        }
    }


    public void doImports(){
        if(!flags.contains("-noimport")){
            String def = "java.util.*,com.crazedout.jgazm.*,java.net.*,java.lang.*,java.io.*,static java.util.Arrays.*";
            String[] imp = props.getProperty("jgazm.imports",def).trim().split(",");
            for(String s:imp){
                imports.add("import " + s + ";");
            }
            if(userProps!=null){
                String i = userProps.getProperty("jgazm.user.imports","").trim();
                    if(i.length()>0) {
                        String[] is = i.split(",");
                        for (String s : is) {
                            imports.add("import " + s + ";");
                        }
                    }
                }
        }
    }

    public String readFile() throws IOException {


        String line = null;
        StringBuilder buffer = new StringBuilder();

        StringBuilder sbb = removeComments(reader);
        reader = new BufferedReader(new StringReader(sbb.toString()));

        while((line=reader.readLine())!=null){

            if(line.trim().startsWith("@flags ")){
                String flags = line.substring(7).trim();
                String[] f = flags.split(" ");
                for(String s:f){
                    this.flags.add(s.trim());
                }
            }else if(line.trim().startsWith("@exception ")){
                StringBuilder sb = new StringBuilder();
                parseFunction(reader, sb,line.trim().endsWith("{"));
                String code = sb.toString().trim();
                if(code.charAt(0)=='{') code = code.substring(1);
                code = code.substring(0,code.length()-1);
                String str = "@Override\r\npublic void onException(Exception ex){\r\n" +
                        code + "\n\n}\r\n";
                methods.append(str);
            }else if(line.trim().startsWith(">>")){
                buffer.append("out(");
                if(line.trim().endsWith(";")){
                    buffer.append(line.trim().substring(2, line.trim().lastIndexOf(";")));
                }else{
                    buffer.append(line.trim().substring(2));
                }
                buffer.append(");");
            }else if(line.trim().startsWith("!>")){
                buffer.append("alert(");
                if(line.trim().endsWith(";")){
                    buffer.append(line.trim().substring(2, line.trim().lastIndexOf(";")));
                }else{
                    buffer.append(line.trim().substring(2));
                }
                buffer.append(");" + Lang.EOL);
            }else if(line.trim().startsWith("!<")){
                buffer.append("$string = showInputDialog(");
                if(line.trim().endsWith(";")){
                    buffer.append(line.trim().substring(2, line.trim().lastIndexOf(";")));
                }else{
                    buffer.append(line.trim().substring(2));
                }
                buffer.append(");" + Lang.EOL);
            }else if(line.trim().startsWith("@import ")){
                imports.add(line.trim().substring(1));
            }else if(line.trim().startsWith("import ")){
                imports.add(line.trim());
            }else if(line.trim().startsWith("@catch")){
                StringBuilder sb = new StringBuilder();
                parseFunction(reader, sb,line.trim().endsWith("{"));
                String code = sb.toString().trim();
                if(code.charAt(0)=='{') code = code.substring(1);
                code = code.substring(0,code.length()-1);
                buffer.append("try{\r\n");
                buffer.append(code);
                buffer.append("}catch(Exception ex){onException(ex);};\r\n");

            }else if(line.trim().toLowerCase().startsWith("@classpath ")){
                parseClasspath(line);
            }else if(line.trim().toLowerCase().startsWith("@resource ")){
                parseResource(line);
            }else if(line.trim().toLowerCase().startsWith("@method")){
                parseFunction(reader, methods);
            }else if(line.trim().toLowerCase().startsWith("@class")){
                StringBuilder sb = new StringBuilder();
                parseFunction(reader, sb);
                classes.add(sb.toString());
            }else if(line.trim().toLowerCase().startsWith("@global ")){
                line = line.substring(7);
                if(line.trim().endsWith(";")){
                    globals.add(line);
                }else{
                    StringBuilder sb = new StringBuilder();
                    parseFunction(reader, sb,line.trim().endsWith("{"));
                    String code = sb.toString().trim();
                    if(code.charAt(0)=='{') code = code.substring(1);
                    code = code.substring(0,code.length()-1);
                    globals.add(code);
                }
            }else if(line.trim().toLowerCase().startsWith("@thread")){
                parseThread(line, buffer);
            }else if(line.trim().toLowerCase().startsWith("@string(")){
                StringBuilder sb = new StringBuilder();
                String name = line.trim().split("\"")[1];
                if(line.trim().endsWith("}")){
                    parseFunction(new BufferedReader(new StringReader(line.substring(line.indexOf("{") + 1
                    ))), sb, line.trim().endsWith("{"));
                }else {
                    parseFunction(reader, sb, true);
                }
                if(sb.toString().length()>1){
                    String code = makeString(name,sb);
                    globals.add(code);
                }
            }else{
                if(line.trim().endsWith(")")){
                    buffer.append(line + ";");
                }else{
                    buffer.append(line + Lang.EOL);
                }
            }
        }
        reader.close();
        return buffer.toString();
    }

    private void doFlags(String line){
        stderr.println("fl:" + line);
        String[] flags = line.split(" ");
        for(String f:flags){
            stderr.println(f);
        }
    }

    private void parseClasspath(String line){
        String cp1 = line.trim().substring(10).trim();
        if(cp1.contains("\"")){
            cp1 = cp1.replace("\"","").trim();
        }
        String[] cps = cp1.split(System.getProperty("path.separator"));
        for(String cp:cps){
            if(cp.length()<3) continue;
            if(!new File(cp).isAbsolute() && getHome()!=null){
                cp = (new File(System.getProperty(JGAZM_HOME)).getAbsolutePath()) +
                        File.separatorChar + cp;
            }
            classpaths.add(cp.replace(";",""));
        }
    }

    private void parseResource(String line){
        String cp1 = line.trim().substring(9).trim();
        if(cp1.contains("\"")){
            cp1 = cp1.replace("\"","").trim();
        }
        String[] cps = cp1.split(System.getProperty("path.separator"));
        for(String cp:cps){
            if(cp.length()<3) continue;
            if(!new File(cp).isAbsolute() && getHome()!=null){
                cp = (new File(System.getProperty(JGAZM_HOME)).getAbsolutePath()) +
                        File.separatorChar + cp;
            }
            resources.add(cp.replace(";",""));
        }
    }

    private void readClasspathsOnly() throws IOException {

        String line = null;
        StringBuilder buffer = new StringBuilder();

        while((line=reader.readLine())!=null) {
            if (line.trim().toLowerCase().startsWith("@classpath ")) {
                parseClasspath(line);
            } else if (line.trim().toLowerCase().startsWith("@resource ")) {
                parseResource(line);
            }
        }
        reader.close();
    }

    private StringBuilder removeComments(Reader reader) throws IOException{

        StringBuilder code = new StringBuilder();
        int c = 0;
        int prev = 0;
        int temp = 0;
        boolean comment1=false;
        boolean comment2=false;
        boolean stringOn = false;
        boolean string2On = false;

        while((c=reader.read())!=-1){

            if(c=='\"' &&  !comment1 && !comment2 && !string2On) {
                stringOn = !stringOn;
            }
            if(c=='\'' && prev!='\\' && !comment1 && !comment2 && !stringOn) {
                string2On = !string2On;
            }

            if(c == '\n' && !comment1 && comment2 && !stringOn && !string2On){
                comment2 = false;
                code.append((char)c);
                prev=c;
                temp=0;
                continue;
            }

            if(temp=='/' && c=='/' && !comment1 && !comment2 && !stringOn && !string2On) {
                comment2 = true;
            }

            if(temp=='/' && c!='/' && !comment1 && !comment2) {
                //out(":" + (char)c + " " + (char)prev);
                if(c!='*') code.append((char)temp);
                temp = 0;
            }

            if(c=='/' && !comment1 && !comment2 && !stringOn && !string2On) {
                temp = c;
            }

            if(c=='*' && prev=='/' && !comment1 && !comment2 && !stringOn && !string2On){
                temp=c;
                comment1 = true;
            }

            if(c=='/' && prev=='*' && comment1 && !comment2 && !stringOn && !string2On){
                temp=0;
                comment1 = false;
                continue;
            }


            if(!comment1 && !comment2 && temp==0){
                code.append((char)c);
            }
            prev = c;
        }
        return code;
    }

    private void parseThread(String line, StringBuilder buffer){
        StringBuilder sb = new StringBuilder();
        parseFunction(reader, sb, line.trim().endsWith("{"));
        String code = sb.toString().trim();
        if(code.charAt(0)=='{') code = code.substring(1);
        code = code.substring(0,code.length()-1);
        buffer.append("\t(new Thread(new Runnable(){\n" +
                "\t\tpublic void run(){\n" +
                "\t\t\ttry{ \n");
        buffer.append(code);
        buffer.append("\t}catch(Exception ex){\n" +
                "\t\t\t\tonException(ex);\n" +
                "\t\t\t}\n" +
                "\t\t}\n" +
                "\t})).start();");
    }

    private String makeString(String name, StringBuilder buffer){
        String str = buffer.toString().substring(1);
        String res = "";
        boolean escape = false;
        for(int i = 0; i < str.length(); i++){
            if(str.charAt(i)=='$') {
                if(!escape) res += "\" +";
                else if(escape) res += " + \"";
                escape = !escape;
            }
            else if(str.charAt(i) == '\\' && !escape){}
            else if(str.charAt(i)=='\n' && !escape) res += "\\r\\n";
            else if(str.charAt(i)=='\r' && !escape) res += "";
            else if(str.charAt(i)=='\t' && !escape) res += "\\t";
            else if(str.charAt(i)=='\b' && !escape) res += "\\b";
            else if(str.charAt(i)=='\f' && !escape) res += "\\f";
            else if(str.charAt(i)=='\'' && !escape) res += "\\'";
            else if(str.charAt(i)=='\"' && !escape) res += "\\\"";
            else if(str.charAt(i)=='\\' && !escape) res += "\\\\";
            else if(str.charAt(i)=='\n' && escape) res+=" ";
            else if(str.charAt(i)=='\r' && escape) res+="";
            else res += str.charAt(i);
        }
        res = res.replace("\r","").replace("\n","");
        int in = res.lastIndexOf("}");
        if(in>-1) res = res.substring(0,in);
        return "String " + name + " = \"" + res + "\";";
    }

    private void doInclude(String file, StringBuilder buffer){
        File f = new File(file);
        if(!f.isAbsolute() && getHome()!=null){
            File tmp = new File(System.getProperty(JGAZM_HOME));
            f = new File(tmp.getAbsolutePath() + File.separatorChar + file);
        }
        buffer.append(new Lang() {
            public void execute() {
            };
        }.readFile(f));
    }

    public static String getHome(){
        return System.getProperty(JGAZM_HOME);
    }


    private void parseFunction(BufferedReader r, StringBuilder buffer) {
        parseFunction(r,buffer,false);
    }

    private void addThreadCode(StringBuilder buffer, StringBuilder sb){
        buffer.append("\t(new Thread(new Runnable(){\n" +
                "\t\tpublic void run(){\n" +
                "\t\t\ttry{ \n");
        buffer.append(sb.toString().replace("}",""));
        buffer.append("\t}catch(Exception ex){\n" +
                "\t\t\t\tonException(ex);\n" +
                "\t\t\t}\n" +
                "\t\t}\n" +
                "\t})).start();");
    }



    private void parseFunction(BufferedReader r, StringBuilder buffer, boolean openFunc) {

        buffer.append("\n");
        try {
            int c = 0;
            int n = 0;
            int prev = 0;
            boolean hit = openFunc;
            boolean stringOn = false;
            boolean annot = false;
            if(hit) n++;

            while ((c = r.read()) != -1) {
                if((c=='@' || annot) && prev != '\\' && !stringOn){
                    int i = 0;
                    int j = 0;
                    char[] word = new char[255];
                    while((i=r.read())!=-1 && j < 255){
                        word[j++] = (char)i;
                        if(i=='{' || i == '\n') break;
                    }
                    if(new String(word).toLowerCase().trim().startsWith("thread")){
                        StringBuilder sb = new StringBuilder();
                        parseFunction(r, sb, true);
                        addThreadCode(buffer,sb);
                    }else if(new String(word).toLowerCase().trim().startsWith("catch")){
                        StringBuilder sb = new StringBuilder();
                        parseFunction(r, sb, true);
                        buffer.append("try{\r\n");
                        buffer.append(sb.toString());
                        buffer.append("catch(Exception ex){onException(ex);};\r\n");
                    }
                    annot = false;
                    continue;
                }
                if(c=='/' && prev=='/'){
                    buffer.append((char)c);
                    int v = 0;
                    while((v=r.read())!=-1){
                        buffer.append((char)v);
                        if(v=='\n') break;
                    }
                    continue;
                }
                if(c=='*' && prev=='/'){
                    buffer.append((char)c);
                    int v = 0;
                    while((v=r.read())!=-1){
                        buffer.append((char)v);
                        if(prev=='*' && v=='/') break;
                        prev = v;
                    }
                    continue;
                }
                if((c=='\'' || c == '\"') && prev!='\\'){
                    stringOn = !stringOn;
                }

                if (((char) c) == '{' && !stringOn) {
                    n++;
                    hit = true;
                } else if (((char) c) == '}' && !stringOn) {
                    n--;
                }
                buffer.append((char) c);
                if (hit && n == 0) {
                    buffer.append("\n");
                    return;
                }
                prev = c;
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    public String setTempDir(){
        tempDir = System.getProperty("java.io.tmpdir");
        if(System.getProperty(JGAZM_WORKDIR)!=null){
            tempDir = System.getProperty(JGAZM_WORKDIR);
        }else if(userProps!=null){
            String dir = null;
            if((dir=userProps.getProperty(JGAZM_WORKDIR))!=null){
                if(dir.length()>0) {
                    File u = new File(userProps.getProperty(JGAZM_WORKDIR));
                    if(!(u).isDirectory() || !(u).exists()){
                        stderr.println("Bad file: user.dir=" + userProps.getProperty(JGAZM_WORKDIR) + " using default.");
                    }else{
                        tempDir = userProps.getProperty(JGAZM_WORKDIR);
                        System.setProperty(JGAZM_WORKDIR,tempDir);
                    }
                }
            }
        }
    return tempDir;
    }

    String tempDir;
    File javaFile;
    public void compile(String file) throws Exception {

        setTempDir();
        String name = getFileName(file);

        if(!new File(tempDir + File.separatorChar + "jgazm").exists()){
            new File(tempDir + File.separatorChar + "jgazm").mkdirs();
        }

        File classFile = new File(tempDir + File.separatorChar + "jgazm" + File.separatorChar + name + ".class");
        File sourceFile = new File(file);
        String className = classFile.getName();
        className = className.substring(0,className.indexOf("."));

        if(classFile.lastModified()<sourceFile.lastModified()){

            String script = readFile();

            javaFile = new File(tempDir + File.separatorChar + "jgazm" + File.separatorChar + name + ".java");
            if(!flags.contains("-keepsource")){
                javaFile.deleteOnExit();
            }

            StringBuilder templ = new StringBuilder();
            if(!flags.contains("-noimport")){
                for(String s:imports){
                    templ.append(s + Lang.EOL);
                }
            }
            templ.append(Lang.EOL+"/* Code auto generated by jGazm v." + jGazmVersion + "*/"+Lang.EOL+Lang.EOL);
            templ.append(Lang.EOL+"public class " +  className + " extends com.crazedout.jgazm.Lang {"+Lang.EOL+Lang.EOL);

            for(String s:globals){
                templ.append(s + Lang.EOL);
            }

            templ.append("String $string = \"\";" + Lang.EOL);

            if(classes!=null && classes.size()>0) {
                for(String c:classes) {
                    templ.append(c.toString() + Lang.EOL);
                }
            }
                templ.append("/* main() arguments */" + Lang.EOL);
                if(args!=null && args.length>0) {
                    templ.append("final String[] ARGS = {");
                    for (String s : args) {
                        templ.append("\"" + s + "\",");
                    }
                    templ.deleteCharAt(templ.length()-1);
                    templ.append("};" + Lang.EOL);
                }else{
                    templ.append("final String[] ARGS = new String[0];" + Lang.EOL);
                }

            templ.append("  public static void main(String argv[]) {"+Lang.EOL);
            String driver = null;
            if(userProps!=null && (driver=userProps.getProperty("jgazm.user.jdbc.driver"))!=null){
                if(driver.length()>0) {
                    templ.append("\ttry{" + Lang.EOL);
                    templ.append("\t\tClass.forName(\"" + driver + "\");" + Lang.EOL);
                    templ.append("\t}catch(ClassNotFoundException ex){" + Lang.EOL);
                    templ.append("\t\tex.printStackTrace();" + Lang.EOL);
                    templ.append("\t}");
                }
            }
            templ.append("    new " + className + "().execute();"+Lang.EOL);
            templ.append("  }"+Lang.EOL);
            templ.append("@Override"+Lang.EOL+"public void execute() {"+Lang.EOL + Lang.EOL);
            templ.append("\ttry{"+Lang.EOL);
            templ.append(Lang.EOL + "/* JAVA */" + Lang.EOL + script + Lang.EOL+"\t}catch(Exception ex){"+
                    Lang.EOL+"\t\tonException(ex);"+Lang.EOL+"\t}"+Lang.EOL+"}" + Lang.EOL);
            templ.append(methods + Lang.EOL);

            templ.append("}");

            this.currentTemplate=templ.toString();

                for(String f:flags){
                    if(f.startsWith("-dump:")){
                        File fi = new File(f.substring("-dump:".length()));
                        dump(new PrintStream(fi));
                    }else if(f.trim().equals("-dump")){
                        dump(System.out);
                    }
                }

            FileOutputStream fos = new FileOutputStream(javaFile);
            fos.write(templ.toString().getBytes());
            fos.flush();
            fos.close();

            String fileToCompile = javaFile.getAbsolutePath();
            JavaCompiler compiler = getCompiler();
            if(compiler==null){
                stderr.println("No compiler was found. Use JDK 1.7 or above.\nHint: Some JRE may work if [JAVA_HOME]/lib/tools.jar is in classpath.");
            }

            String cps = System.getProperty("java.class.path");
            for(String cp:classpaths){
                if(cp.trim().length()>0) {
                    cps += System.getProperty("path.separator") + cp;
                }
            }

            cps += ";" + exportLang();

            int compilationResult = compiler.run(null, null, null, "-classpath", cps, fileToCompile);
            if(compilationResult == 0){
                //stdout.println("Compilation ok.");
            }else{
                throw new RuntimeException("Compilation failed.");
            }

        }else{
            readClasspathsOnly();
        }
        if(flags.contains("-showcp")) {
            for (URL u : getClasspaths(classFile)) {
                stdout.println(u.toString());
            }
        }
        if(flags.contains("-showapi")) {
            showApi();
        }

        boolean run = true;
        if(flags.contains("-check")){
            run = false;
        }
        for(String e:flags){
            if(e.startsWith("-export")){
                run = false;
                if(javaFile==null){
                    stderr.println("Source must be recompiled for export. Use -clean..");
                    return;
                }
                if(e.indexOf(":")==-1){
                    stderr.println("'-export' flag must be followed by destination path: -export:<my_file_path>/MyJar.jar");
                }
                else {
                    File dest = new File(e.substring("-export:".length()));
                    if(!dest.isAbsolute()){
                        File exportDir = new File(getHome()!=null?getHome():System.getProperty("user.dir"));
                        dest = new File(exportDir.getAbsolutePath() + File.separatorChar + "export" + File.separatorChar + dest.toString());
                    }
                    try{
                        dest.createNewFile();
                    }catch(IOException ex){
                        stderr.println("ERROR: '" + dest.getAbsolutePath() + "' can not be created..");
                        stderr.println(ex.getMessage());
                        return;
                    }
                    if (dest.exists() && !dest.isDirectory()) {
                        export(classFile, dest);
                    } else {
                        stderr.println("Export error: '" + dest.getAbsolutePath() + "' is not a valid file.");
                    }
                }
            }
        }
            invoke(classFile,className,run);
    }

    private void javaCompile(String fileToCompile){

        JavaCompiler compiler = getCompiler();
        if(compiler==null){
            stderr.println("No compiler was found. Use JDK 1.7 or above.\nHint: Some JRE may work if [JAVA_HOME]/lib/tools.jar is in classpath.");
        }

        String cps = System.getProperty("java.class.path");
        int compilationResult = compiler.run(null, null, null, "-classpath", cps, fileToCompile);
        if(compilationResult == 0){
            //stderr.println("Compilation ok.");
        }else{
            throw new RuntimeException("Compilation failed.");
        }
    }

    private void invoke(File classFile, String className, boolean run) throws Exception {
        urlCl = new URLClassLoader(getClasspaths(classFile),
                System.class.getClassLoader());
        Class c = urlCl.loadClass(className);
        Method meth = c.getMethod("main", String[].class);
        String[] params = null;
        if(flags.contains("-clean")){
            clean(classFile.getParentFile().getAbsolutePath(),className);
        }
        if(run) meth.invoke(null,(Object)params);
    }

    private void clean(String path, String name)  {
        if(reader!=null){
            try{
                reader.close();
            }catch(IOException ex){
                ex.printStackTrace();
            }
        }
        File[] files = new File(path).listFiles();
        try{
        String fn = name.substring(0, name.indexOf("_java"));
        for(File f:files){
            if(f.getName().startsWith(fn) && !f.isDirectory()){
                f.deleteOnExit();
            }
        }
        }catch(Exception ex){
            System.out.println("clean:" + ex.getMessage() + " may not be an error...");
        }
    }

    public List<File> getInnerClasses(String dir, String javaFile){
        List<File> names = new ArrayList<File>();

        File dirFile = new File(dir);
        File[] files = dirFile.listFiles();
        String name = javaFile.substring(0,javaFile.indexOf(".java"));
        for(File f:files){
            String cs = f.getName();
            if(cs.endsWith(".class")){
                String cn = null;
                if(cs.indexOf("$")>-1){
                    cn = cs.substring(0,cs.indexOf("$"));
                }else {
                    cn = cs.substring(0, cs.indexOf(".class"));
                }
                if(cn.equals(name)){
                    names.add(f);
                }
            }
        }
        return names;
    }

    public JavaCompiler getCompiler(){
        return ToolProvider.getSystemJavaCompiler();
    }

    private void cleanClasspath(){
        List<String> temp = new ArrayList<String>();
        for(String s:classpaths){
            if(s.length()>1) temp.add(s);
        }
        classpaths = temp;
    }

    public void export(File classFile, File jarFile){
        File dest = jarFile.getParentFile();
        String cmd = "@echo off\r\n";
        String cp = "-classpath \"" + System.getProperty("path.separator") + jarFile.getName() + System.getProperty("path.separator");
        File lib = new File(dest.getAbsolutePath() + File.separatorChar + "lib");
        cleanClasspath();
        String jarCp = classpaths.size()>0?"Class-Path: ":"";
        if(classpaths.size()>0) lib.mkdirs();
        for(String c:classpaths){
            File cpf = new File(c);
            if(cpf.exists() && !cpf.isDirectory()){
                File cpy = new File(lib.getAbsolutePath() + File.separatorChar + "lib" + cpf.getName());
                try {
                    copyFile(cpf, cpy);
                    stdout.println("Copying:" + cpf + " to " + cpy);
                    cp += cpy.getAbsolutePath() + System.getProperty("path.separator");
                    jarCp += "lib/" + cpy.getName() + " ";
                }catch(IOException ex){
                    ex.printStackTrace();
                }
            }
        }
        cp += "\" ";
        cmd += "\"" + "%JAVA_HOME%" + File.separatorChar + "\\bin\\java\" " + cp +
                classFile.getName().substring(0,classFile.getName().lastIndexOf(".")) + "\r\n";
        cmd += "set /p DUMMY=Hit ENTER to exit...\r\n";
        try {
            JarOutputStream fout = new JarOutputStream(new FileOutputStream(dest.getAbsolutePath() +
                    File.separatorChar + jarFile.getName()));
            fout.putNextEntry(new ZipEntry("META-INF/"));
            fout.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            fout.write(("Manifest-Version: 1.0\r\nMain-Class: " +
                    javaFile.getName().substring(0,javaFile.getName().indexOf(".java")) +
                    "\r\n" + jarCp.trim() + "\r\n").getBytes());
            fout.closeEntry();

            FileOutputStream fos = new FileOutputStream(new File(dest.getAbsolutePath() +
                    File.separatorChar + "runner.bat"));
            fos.write(cmd.getBytes());
            fos.close();
            InputStream is = getClass().getResourceAsStream("/com/crazedout/jgazm/Lang.class");
            fout.putNextEntry(new ZipEntry("com/crazedout/jgazm/"));
            fout.putNextEntry(new ZipEntry("com/crazedout/jgazm/Lang.class"));
            int b = 0;
            while((b=is.read())>-1){
                fout.write(b);
            }
            is.close();

            for(File s:getInnerClasses(tempDir + File.separatorChar + "jgazm", javaFile.getName())){
                fout.putNextEntry(new ZipEntry(s.getName()));
                Path path = Paths.get(s.getAbsolutePath());
                byte[] data = Files.readAllBytes(path);
                fout.write(data);
            }
            fout.close();
            for(String r:resources){
                stderr.println(r);
                //copyFile(new File(r), new File(jarFile.getParentFile().getAbsolutePath()));
            }
            stdout.println("Exported to:" + jarFile.getAbsolutePath());

        }catch(IOException ex){
            ex.printStackTrace();
        }
    }

    private static void copyFile(File sourceFile, File destFile) throws IOException {
        if(!destFile.exists()) {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        }
        finally {
            if(source != null) {
                source.close();
            }
            if(destination != null) {
                destination.close();
            }
        }
    }

    public void setDumpStream(PrintStream printStream){
        dumpPrintStream = printStream;
    }

    public void dump(PrintStream printStream){
        if(dumpPrintStream==null){
            printStream.println(this.currentTemplate!=null?this.currentTemplate:"");
        }else{
            dumpPrintStream.println(this.currentTemplate!=null?this.currentTemplate:"");
        }
    }

    @SuppressWarnings("deprecation")
    private URL[] getClasspaths(File classFile) throws Exception {

        List<URL> ulist = new ArrayList<URL>();
        ulist.add(new File(exportLang()).toURL());
        ulist.add(new File(classFile.getParent()).toURL());
        for(String s:classpaths){
            if(s.startsWith("-cp:")){
                String[] arr = s.trim().substring(4).split(";");
                for(String sa:arr){
                    ulist.add(new File(sa).toURL());
                }
            }else{
                ulist.add(new File(s.trim()).toURL());
            }
        }
        return ulist.toArray(new URL[ulist.size()]);
    }

    private String exportLang() throws IOException {
        File temp = new File(tempDir + File.separatorChar + "jgazm" +
                File.separatorChar + getVersion() +
                File.separatorChar + "/com/crazedout/jgazm/");
        if(!temp.exists()){
            temp.mkdirs();
            String classes[] = {"Lang.class","JGazm.class","JGazm$1.class","JGazm$2.class"};
            for(String s:classes){
                File cls = new File(temp.getAbsolutePath() + File.separatorChar + s);
                    if(!cls.exists()){
                        InputStream in = getClass().getResourceAsStream("/com/crazedout/jgazm/" + s);
                        FileOutputStream fos = new FileOutputStream(cls);
                        int b = 0;
                        while((b=in.read())!=-1){
                            fos.write(b);
                        }
                        fos.flush();
                        fos.close();
                        in.close();
                    }
                }
            }
        temp = new File(tempDir + File.separatorChar + "jgazm" +
                File.separatorChar + getVersion());
        File cls = new File(temp.getAbsolutePath() + File.separatorChar + "jgazm.properties");
        if(!cls.exists()){
            InputStream in = getClass().getResourceAsStream("/jgazm.properties");
            FileOutputStream fos = new FileOutputStream(cls);
            int b = 0;
            while((b=in.read())!=-1){
                fos.write(b);
            }
            fos.flush();
            fos.close();
            in.close();
        }


        File ret = new File(tempDir + File.separatorChar + "jgazm" +
                File.separatorChar + getVersion());

        return ret.getAbsolutePath();
    }

    public void addClasspath(String cp){
        this.classpaths.add(cp);
    }

    private String getFileName(String f){
        if(className!=null){
            return className;
        }else {
            File file = new File(f);
            return file.getName().replace(".", "_");
        }
    }

    private File createTemp(StringBuffer b) throws IOException {

        setTempDir();
        if(!new File(tempDir + File.separatorChar + "jgazm").exists()){
            new File(tempDir + File.separatorChar + "jgazm").mkdirs();
        }

        File temp = new File(tempDir + File.separatorChar + "jgazm" + File.separatorChar + "defaultjgazm" + System.currentTimeMillis() + ".java");
        temp.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(temp);
        fos.write(b.toString().getBytes());
        fos.flush();
        fos.close();
        return temp;
    }

    public List<String> showClassMethods(String clazz){

        String cz = null;
        List<String> ms = new ArrayList<String>();
        for(String i:imports) {
            if(i.trim().endsWith(".*;")){
                cz = i.substring(6).trim();
                cz = cz.substring(0,cz.length()-3) + "." + clazz;
            }else{
                cz = i.substring(6).trim();
                cz = cz.replace(";"," ").trim();
                if(!cz.endsWith(clazz)){
                    continue;
                }
            }
            try {
                Class c = getClass().getClassLoader().loadClass(cz);
                Constructor[] allConstructors = c.getDeclaredConstructors();
                for (Constructor ctor : allConstructors) {
                    if(ctor.toString().endsWith("()")) continue;
                    String[] split = ctor.toString().split(" ");
                    String cs = split[0] + " void ";
                    for(int j = 1; j < split.length; j++){
                        if(split[j].equals("throws")) break;
                        cs += split[j];
                    }
                }
                Method[] methods = c.getMethods();
                for(Method m:methods){
                    if(m.toString().indexOf(cz)>-1) {
                        ms.add(m.toString());
                    }
                }
            } catch (ClassNotFoundException ex) {
                //ex.printStackTrace();
            }
        }
        return ms;
    }

    public static void usage(){

        System.out.println("CrazedoutSoft (c) 2016.");
        System.out.println("Usage: jgazm {options} [file] %arg1 %arg2 ...");
        System.out.println("Options:");
        System.out.println("-stdin\t\t\t\tRead input directly from System.in (Single \".\" + ENTER quits)");
        System.out.println("-dump\t\t\t\tdump script as POJO to System.out.");
        System.out.println("-dump:<file>\t\tdump script as POJO to <file>.");
        System.out.println("-clean\t\t\t\tRemove all temp files generated.");
        System.out.println("-export:<dest_jar>  export the works as one java runnable package.");
        System.out.println("-java\t\t\t\tInput file is pure Java. Can not be used with -stdin.");
        System.out.println("\t\t\t\t\t-dump,-clean,-noimport and -keepsource has no meaning when -java in used.");
        System.out.println("\t\t\t\t\twill be set by default if file ends width '.java'.");
        System.out.println("-keepsource\t\t\tDo not delete generated POJO code.");
        System.out.println("-noimport\t\t\tDo not import any packages by default.");
        System.out.println("-version\t\t\tPrint jgazm version.");
        System.out.println("-check\t\t\t\tCompile only.");
        System.out.println("-showapi\t\t\tPrint methods of com.crazedout.jgazm.Lang.");
        System.out.println("-help or /?\t\t\tPrints this message.");
    }

    public List<String> getApi(String clazz){
        imports.add("import com.crazedout.jgazm.*;");
        List<String> api = showClassMethods(clazz);

        Collections.sort(api, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                    String[] split = o1.split(" ");
                    String pattern = null;
                    try {
                        pattern = split[2].substring("com.crazedout.jgazm.Lang.".length());
                    }catch(Exception ex){
                        pattern = split[3].substring("com.crazedout.jgazm.Lang.".length());
                    }
                String na1 = pattern;
                try {
                    na1 = pattern.substring(0, pattern.indexOf("("));
                }catch(Exception ex){
                    ex.printStackTrace();
                }

                    split = o2.split(" ");
                    try {
                        pattern = split[2].substring("com.crazedout.jgazm.Lang.".length());
                    }catch(Exception ex){
                        pattern = split[3].substring("com.crazedout.jgazm.Lang.".length());
                    }
                    String na2 = pattern;
                    try {
                        na2 = pattern.substring(0, pattern.indexOf("("));
                    }catch(Exception ex){
                        ex.printStackTrace();
                    }
                    return na1.compareTo(na2);
            }
        });

        return api;
    }

    public void showApi(){
        List<String> api = getApi("Lang");
        System.out.println("JGazm API: (com.crazedout.jgazm.Lang)");
        for(String s:api){
            System.out.println(s);
        }
    }

    public void setOutputStream(PrintStream out){
        stdout = out;
    }

    public void setErrorStream(PrintStream out){
        stderr = out;
    }

    public static void main(String argv[]) throws Exception {

        if(System.getProperty(JGAZM_PROPS)==null){
            File f = new File(System.getProperty("user.dir") +
                    File.separatorChar + "user.properties");
            if(f.exists()){
                System.setProperty(JGAZM_PROPS,f.getAbsolutePath());
            }
        }

        if(System.getProperty(JGAZM_WORKDIR)==null &&
                getUserProperties() !=null){
            System.setProperty(JGAZM_WORKDIR,
                    getUserProperties().getProperty(JGAZM_WORKDIR));
        }

        ArrayList<String> args = new ArrayList<String>();
        for(String s:argv){
            if(s.startsWith("%")){
                args.add(s.substring(1));
            }
        }
        JGazm r = null;
        if(args.size()>0) {
            r = new JGazm(args.toArray(new String[args.size()]));
        }else{
            r = new JGazm();
        }
        String file = "no infile";

        for(String s:argv){
            if((s.startsWith("-") || s.startsWith("/")) && s.length()>1){
                r.flags.add(s);
            }else if(!s.startsWith("%")){
                file = s;
                if(file.trim().endsWith(".java")){
                    r.flags.add("-java");
                }
            }
        }

        for(String s:r.flags){
            if(s.startsWith("-classname:")){
                String[] ns = s.split(":");
                r.className = ns[1];
            }
        }

        if(r.flags.contains("/?") || r.flags.contains("-help")){
            usage();
            return;
        }

        if(r.flags.contains("-version")){
            System.out.println("version=" + r.jGazmVersion);
            System.out.println("compiler=" + (r.getCompiler()!=null?r.getCompiler().getClass():"No compiler found. Make sure to use JDK 1.7 or higher."));
            System.out.println("jgazm.home=" + JGazm.getHome());
            String jb = System.getProperty(JGAZM_PROPS);
            System.out.println("jgazm.props=" + (jb!=null?jb:"null"));
            r.setTempDir();
            System.out.println("workdir=" + r.tempDir);
            return;
        }

        if(r.flags.contains("-showapi")){
            r.showApi();
            return;
        }

        if(r.flags.contains("-stdin")){
            if(r.flags.contains("-java")){
                System.out.println("ERROR: -java and -stdin can not be used together.");
                return;
            }
            if(!r.flags.contains("-clean")){
                r.flags.add("-clean");
            }
            r.setInputStream(System.in);
        }else{
            r.setInputFile(file);
            if(!r.flags.contains("-java")) {
                r.compile(file);
            }
        }
    }


}
