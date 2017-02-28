package com.crazedout.jgazm.editor;

import org.fife.ui.autocomplete.*;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import com.crazedout.jgazm.JGazm;
import com.crazedout.jgazm.Lang;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.dnd.DropTarget;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * Created by NRKFRR on 2016-04-15.
 */
public class Editor extends JFrame {

    private TextArea currentArea;
    private File currentDir = new File(System.getProperty("user.dir"));
    private RSyntaxTextArea console;
    private TextAreaOutputStream out;
    private Editor editor;
    private final static String TITLE = "jGazm Simple Editor";
    private String[] args = new String[0];
    private JCheckBoxMenuItem clean,dump,imp,keep,check,java,show;
    private JSplitPane split;
    private boolean isTempFile=false;
    private JTabbedPane tabs;
    private CardLayout cardLayout;
    private JPanel layoutPanel;
    private JButton runBtn;
    private JMenuItem exp,close;
    private final String EDITOR = "gazmedit.properties";
    private JMenu apiMenu, toolsMenu,libs;

    class TextArea extends RSyntaxTextArea {
        FileSettings s;
        public TextArea(int w, int h){
            super(w,h);
            s = new FileSettings();
        }
        void setFileSettings(FileSettings s){
            this.s = s;
        }
        FileSettings getFileSettings(){
            return this.s;
        }
    }

    class FileSettings {
        public FileSettings(){
        }
        public FileSettings(String name, File currentFile, String currentCode){
            this.name=name;
            this.currentFile=currentFile;
            this.currentCode=currentCode;
        }

        String name;
        File currentFile = null;
        String currentCode = null;

        String getName(){
            if(currentFile!=null){
                return currentFile.getName();
            }else return "Unknown";
        }
    }

    public Editor(){
        this.setTitle(TITLE);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        editor = this;

        try{
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            BufferedImage ico = ImageIO.read(getClass().getResource("/java.jpg"));
            setIconImage(ico);
        }catch(Exception ex){
            ex.printStackTrace();
        }

        currentArea = createTextArea();

        console = new RSyntaxTextArea(20,60);
        console.setCaretPosition(0);
        console.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_XML);

        out = new TextAreaOutputStream(console);
        final PrintStream consolePs = new PrintStream(new TextAreaOutputStream(console));
        System.setOut(consolePs);
        System.setErr(consolePs);

        tabs = new JTabbedPane();
        tabs.setUI(new CustomTabbedPaneUI(this));
        tabs.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                try{
                    if(tabs.getTabCount()>0){
                        currentArea = (TextArea)((RTextScrollPane)tabs.getSelectedComponent()).getTextArea();
                    }else{
                        handleCardLayout();
                    }
                    //System.out.println(currentArea.getFileSettings().currentFile.getAbsolutePath());
                    //System.out.println(currentArea.getFileSettings().currentCode);
                }catch(Exception ex){ex.printStackTrace();}
            }
        });
        cardLayout = new CardLayout();
        layoutPanel = new JPanel(cardLayout);
        layoutPanel.add("tabs", tabs);
        layoutPanel.add("empty", new EmptyPanel());
        DragDropListener myDragDropListener = new DragDropListener(this);
        new DropTarget(layoutPanel, myDragDropListener);
        split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,layoutPanel,new RTextScrollPane(console));
        split.setDividerLocation(500);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        runBtn = new JButton("  Execute  ");
        runBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    console.setText("");
                    console.setCaretPosition(0);
                    compileAndRun();
                }catch(Exception ex){
                    ex.printStackTrace();
                }
            }
        });
        runBtn.setToolTipText("CTRL+ENTER in code editor executes directly");

        JButton clearBtn = new JButton("Clear console");
        clearBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                console.setText("");
                console.setCaretPosition(0);
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onExit();
            }

        });

        setSize(1024, 800);

        //if(getHome()!=null){
            File f = new File(getHome()!=null?getHome():System.getProperty("user.home"));
            f = new File(f.getAbsolutePath() + File.separatorChar + EDITOR);
            Properties props = new Properties();
            File currentFile,currentDir;
            String currentCode;
            try{
                props.load(new FileInputStream(f));
                if(getProperty(props,"command.line.args")!=null){
                    setArgs(props.getProperty("command.line.args","").split(" "));
                }
                if(getProperty(props,"editor.width")!=null && getProperty(props,"editor.height")!=null){
                    setSize(Integer.parseInt(getProperty(props,"editor.width")),
                            Integer.parseInt(getProperty(props,"editor.height")));
                }
                if(getProperty(props,"editor.divider.location")!=null){
                    split.setDividerLocation(Integer.parseInt(getProperty(props,"editor.divider.location")));
                }
                exportPath = getProperty(props,"jgazm.export.file");

                if(props.getProperty("current.file")!=null){
                    currentFile = new File(props.getProperty("current.file"));
                    currentDir = currentFile.getParentFile();
                    if(currentFile.exists()){
                        String code = new Lang(){
                            public void execute(){}
                        }.readFile(currentFile.getAbsolutePath());
                        currentArea.setText(code);
                        currentArea.setCaretPosition(0);
                        currentCode = code;
                        setTitle(TITLE + " " + currentFile.getAbsolutePath());
                        currentArea.setFileSettings(new FileSettings(currentFile.getName(),currentFile,currentCode));
                        tabs.add(currentFile.getName() + "    ",new RTextScrollPane(currentArea));
                    }else{
                        System.err.println("Can't find file:" + currentFile.getAbsolutePath());
                    }
                }
            }catch(IOException ex){
                //ex.printStackTrace();
            }

       // }
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation(dim.width / 2 - getSize().width / 2, dim.height / 2 - getSize().height / 2);
        setVisible(true);

        addMenubar();
        addToolBar();
        btnPanel.add(clearBtn);
        btnPanel.add(runBtn);
        add("South",btnPanel);
        add("Center", split);
        handleCardLayout();
    }

    public Editor getEditor(){
        return this.editor;
    }

    String dumpFile;

    public void setDumpFile(String file){
        this.dumpFile = file;
    }

    private void onExit(){
            File f = new File(getHome()!=null?getHome():System.getProperty("user.home"));
            f = new File(f.getAbsolutePath() + File.separatorChar + EDITOR);
            Properties props = new Properties();
            if(currentArea!=null && currentArea.getFileSettings()!=null && currentArea.getFileSettings().currentFile!=null){
                props.setProperty("current.file", currentArea.getFileSettings().currentFile.getAbsolutePath());
            }
            props.setProperty("command.line.args",getArgsAsString());
            props.setProperty("editor.width", ""+getSize().width);
            props.setProperty("editor.height", ""+getSize().height);
            props.setProperty("editor.divider.location", ""+split.getDividerLocation());
            String exp = "";
            if(getHome()!=null) exp = getHome() + File.separatorChar + "export" + File.separatorChar + "App.jar";
            props.setProperty("jgazm.export.file",exportPath!=null?exportPath:exp);
            try{
                props.store(new FileOutputStream(f),"");
            }catch(IOException ex){
                ex.printStackTrace();
            }
        checkSaveFile();
    }

    private TextArea createTextArea(){

        final TextArea area = new TextArea(20, 60);
        area.setCaretPosition(0);
        area.setCodeFoldingEnabled(true);
        area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        CompletionProvider provider = createCompletionProvider();

        AutoCompletion ac = new AutoCompletion(provider);
        ac.setAutoActivationDelay(1000);
        ac.setAutoActivationEnabled(true);
        ac.install(area);
        //Font font = area.getFont();
        //area.setFont(new Font(font.getFontName(),Font.PLAIN, 14));
        /*
        LanguageSupportFactory lsf = LanguageSupportFactory.get();
        LanguageSupport support = lsf.getSupportFor(SyntaxConstants.SYNTAX_STYLE_JAVA);
        JavaLanguageSupport jls = (JavaLanguageSupport)support;
        try {
            jls.getJarManager().addCurrentJreClassFileSource();
            //jls.getJarManager().addClassFileSource(new File(System.getProperty("jgazm.bin")));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        lsf.register(area);
        */
        area.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                super.keyPressed(e);
                switch(e.getKeyCode()) {
                    case KeyEvent.VK_ENTER:
                        if (e.isControlDown()) {
                            try {
                                console.setText(null);
                                console.setCaretPosition(0);
                                compileAndRun();
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                        break;
                    case KeyEvent.VK_F12:
                        showLangApi();
                        break;
                }

            }
        });

        area.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                super.mousePressed(e);
                if(e.isControlDown()){
                    int c = area.getCaretPosition();
                    int start = 0;
                    int end = 0;
                    while(c>-1){
                        if(area.getText().charAt(c)=='\n'){
                            start = c;
                            break;
                        }else{
                            c--;
                        }
                    }
                    c = area.getCaretPosition();
                    while(c<area.getText().length()-1){
                        if(area.getText().charAt(c)=='\n'){
                            end = c;
                            break;
                        }else{
                            c++;
                        }
                    }
                    String text = area.getText().substring(start,end);
                    text = text.replace("\"","");
                    if(text.trim().startsWith("@include ")){
                        String f = text.trim().substring("@include ".length());
                        File file = new File(f);
                        if(!file.isAbsolute() && getHome()!=null){
                            File p = new File(System.getProperty("jgazm.home"));
                            file = new File(p.getAbsolutePath() + File.separatorChar + f);
                        }
                        drop(file);
                    }
                }
            }
        });

        DragDropListener myDragDropListener = new DragDropListener(this);
        new DropTarget(area, myDragDropListener);
        return area;
    }

    private String getProperty(Properties props, String key){
        String p = props.getProperty(key);
        if(p==null || p.length()==0) return null;
        return p;
    }

    private void checkSaveFile(){
        //for(int i = 0; i < tabs.getTabCount(); i++){
          //  tabs.setSelectedIndex(i);
            //currentArea = (TextArea)tabs.getTabComponentAt(i);
            if(currentArea==null) return;
            if(currentArea.getFileSettings().currentFile!=null && currentArea.getFileSettings().currentCode!=null){
                if(!currentArea.getFileSettings().currentCode.equals(currentArea.getText())){
                    if(JOptionPane.showConfirmDialog(editor,
                            "Current file has been changed.\nSave file "+tabs.getTitleAt(tabs.getSelectedIndex()).trim() +" now?","Save file?",
                            JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION){
                            if(isTempFile){
                                saveFileAs();
                            }else{
                                saveFile();
                            }
                    }
                }
            }
        //}
    }

    private File getWorkDir(){
        File f = null;
        if(System.getProperty("jgazm.workdir")!=null){
            return new File(System.getProperty("jgazm.workdir"));
        }
        try{
            Properties props = JGazm.getUserProperties();
            if(props.getProperty("jgazm.workdir")!=null){
                f = new File(props.getProperty("jgazm.workdir"));
                if(f.exists()){
                    return f;
                }
            }
        }catch(Exception ex){
            f = new File(System.getProperty("java.io.tmpdir"));
        }
        return f;
    }

    private void compileAndRun() throws Exception {
        String javaFile = null;
        if(getFlags().contains("-java")) {
            if(currentArea.getSelectedText()!=null){
                JOptionPane.showMessageDialog(editor,
                        "In Java mode (-java) the whole file will be compiled.\nSelection of code is not supported.",
                        "Java mode",JOptionPane.INFORMATION_MESSAGE);
            }
            saveFile();
            javaFile = currentArea.getFileSettings().currentFile.getAbsolutePath();
            (new JGazm(this.args)).runJava(javaFile, getFlags());
        }else{
            (new JGazm(this.args)).runScript(new StringBuffer(getText()), getFlags());
            for(String f:getFlags()){
                if(f.startsWith("-dump:")){
                    File file = new File(f.substring("-dump:".length()));
                    if(file.exists()){
                        drop(file);
                    }
                }
            }
        }
    }

    public List<String> getFlags(){


        ArrayList<String> flags = new ArrayList<String>();
        if(dump.isSelected()){
            //if(dumpFile!=null){
            //    flags.add("-dump:" + dumpFile);
            //}else{
                flags.add("-dump");
           // }
        }
        if(clean.isSelected()){
            flags.add("-clean");
        }
        if(keep.isSelected()){
            flags.add("-keepsource");
        }
        if(imp.isSelected()){
            flags.add("-noimport");
        }
        if(check.isSelected()){
            flags.add("-check");
        }
        if(java.isSelected()){
            flags.add("-java");
        }
        if(show.isSelected()){
            flags.add("-showcp");
        }

        return flags;
    }

    private void addToolBar(){
        try{
            JToolBar tb = new JToolBar();
            //
            JButton btn;
            tb.add(btn = new JButton(new ImageIcon(ImageIO.read(getClass().getResourceAsStream("/images/add.png")))));
            btn.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    newFile();
                }
            });
            btn.setToolTipText("New file");

            tb.addSeparator();


            tb.add(btn=new JButton(new ImageIcon(ImageIO.read(getClass().getResourceAsStream("/images/balloonClose.png")))));
            btn.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if(tabs.getTabCount()>0){
                        console.setText("");
                    }
                }
            });
            btn.setToolTipText("Clear console");

            tb.addSeparator();


            tb.add(toolCheckBtn = new JButton(new ImageIcon(ImageIO.read(getClass().getResourceAsStream("/images/autoscrollToSource_dark.png")))));
            toolCheckBtn.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    boolean s = check.isSelected();
                    check.setSelected(true);
                    runBtn.doClick();
                    check.setSelected(s);
                }
            });
            toolCheckBtn.setToolTipText("Compile only (-check)");

            tb.addSeparator();

            tb.add(toolRunBtn = new JButton(new ImageIcon(ImageIO.read(getClass().getResourceAsStream("/images/run@2x.png")))));
            toolRunBtn.setToolTipText("Run");
            toolRunBtn.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    runBtn.doClick();
                }
            });

            Container contentPane = getContentPane();
            contentPane.add(tb, BorderLayout.NORTH);
        }catch(Exception ex){
            ex.printStackTrace();

        }
    }

    JButton toolCheckBtn,toolRunBtn;

    public String getText(){
        return currentArea.getSelectedText()!=null? currentArea.getSelectedText(): currentArea.getText();
    }

    public void drop(File file){
        checkSaveFile();
        currentArea = createTextArea();
        currentArea.getFileSettings().currentFile = file;
        String code = new Lang(){
            public void execute(){}
        }.readFile(file.getAbsolutePath());
        currentArea.getFileSettings().currentCode = code;
        setTitle(TITLE + " " + file.getAbsolutePath());
        currentArea.getFileSettings().name = file.getName();
        currentArea.setText(code);
        currentArea.setCaretPosition(0);
        Component cnt ;
        tabs.add(currentArea.getFileSettings().getName()+ "   ",(cnt = new RTextScrollPane(currentArea)));
        tabs.setSelectedComponent(cnt);
        handleCardLayout();
    }

    public void handleCardLayout(){
        if(tabs.getTabCount()==0){
            cardLayout.show(layoutPanel,"empty");
            runBtn.setEnabled(false);
            close.setEnabled(false);
            exp.setEnabled(false);
            apiMenu.setEnabled(false);
            toolsMenu.setEnabled(false);
            libs.setEnabled(false);
            toolRunBtn.setEnabled(false);
            toolCheckBtn.setEnabled(false);

        }else{
            cardLayout.show(layoutPanel,"tabs");
            runBtn.setEnabled(true);
            close.setEnabled(true);
            exp.setEnabled(true);
            apiMenu.setEnabled(true);
            toolsMenu.setEnabled(true);
            libs.setEnabled(true);
            toolRunBtn.setEnabled(true);
            toolCheckBtn.setEnabled(true);
        }
        tabs.invalidate();
        tabs.validate();
    }

    public void newFile(String code){
        checkSaveFile();
        currentArea = createTextArea();
        try{
            if(getWorkDir()!=null){
                currentArea.getFileSettings().currentFile = getUniqueFile(getWorkDir().getAbsolutePath() + File.separatorChar + "Untitled", ".jgazm");
            }
            currentArea.getFileSettings().currentFile.createNewFile();
            currentArea.getFileSettings().currentCode = code!=null?code:"";
            currentArea.setText(code!=null?code:"");
            currentArea.setCaretPosition(0);
            setTitle(TITLE + " " + currentArea.getFileSettings().currentFile.getAbsolutePath());
            Component cnt ;
            tabs.add(currentArea.getFileSettings().getName() + "   ",(cnt = new RTextScrollPane(currentArea)));
            tabs.setSelectedComponent(cnt);
            handleCardLayout();
        }catch(IOException ex){
            ex.printStackTrace();
        }
    }

    public void newFile(){
        this.newFile(null);
    }

    class EmptyPanel extends JPanel {

        public EmptyPanel(){
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    super.mousePressed(e);
                    newFile();
                }
            });
        }

        String message = "No files are opened.";
        public void paintComponent(Graphics g){
            super.paintComponent(g);
            g.setFont(new Font("Verdana", Font.BOLD, 18));
            Dimension dim = getSize();
            g.setColor(Color.GRAY);
            int w = g.getFontMetrics().stringWidth(message);
            int h = g.getFontMetrics().getHeight();
            g.drawString(message,(dim.width / 2) - (w/2),(dim.height/2)-(h/2));
            message = "Click to open new file";
            g.setFont(new Font("Verdana", Font.BOLD, 12));
            w = g.getFontMetrics().stringWidth(message);
            h = g.getFontMetrics().getHeight();
            g.drawString(message,(dim.width / 2) - (w/2),(dim.height/2)-(h/2) + 60);
        }
    }

    public void closeFile(){
        try{
            checkSaveFile();
            tabs.removeTabAt(tabs.getSelectedIndex());
            handleCardLayout();
            if(tabs.getTabCount()==0) {
                currentArea = null;
            }
            tabs.invalidate();
            tabs.validate();
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    public File getUniqueFile(String name, String ext){
        int i = 0;
        String num = Integer.toString(i);
        File f = new File(name + num + ext);
        while (f.exists()) {
            i++;
            num = Integer.toString(i);
            f = new File(name + num + ext);
        }
        return f;
    }

    public void openFile(){
        JFileChooser fc = new JFileChooser();
        currentArea = createTextArea();
        if(currentDir!=null){
            fc.setCurrentDirectory(currentDir);
        }
        int returnVal = fc.showOpenDialog(this);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            currentArea.getFileSettings().currentFile = fc.getSelectedFile();
            String code = new Lang(){
                @Override
                public void execute(){}
            }.readFile(currentArea.getFileSettings().currentFile.getAbsolutePath());
            currentDir = currentArea.getFileSettings().currentFile.getParentFile();
            currentArea.getFileSettings().currentCode = code;
            currentArea.setText(code);
            currentArea.setCaretPosition(0);
            setTitle(TITLE + " " + currentArea.getFileSettings().currentFile.getAbsolutePath());
            isTempFile=false;
            Component cnt ;
            tabs.add(currentArea.getFileSettings().getName() + "   ", (cnt = new RTextScrollPane(currentArea)));
            tabs.setSelectedComponent(cnt);
            handleCardLayout();
        }
    }

    public void saveFile(){
        if(currentArea.getFileSettings().currentFile!=null){
            if(currentArea.getText().length()>0){
                (new Lang(){
                public void execute(){}
                }).writeFile(currentArea.getFileSettings().currentFile, currentArea.getText());
                currentArea.getFileSettings().currentCode = currentArea.getText();
                isTempFile=false;
            }
        }
    }

    public void saveFileAs(){
        File file = null;
        if(isTempFile){
            file = getUniqueFile(currentDir.getAbsolutePath() + File.separatorChar + "Untitled",".jgazm");
        }else{
            file = currentArea.getFileSettings().currentFile;
        }
        this.saveFileAs(file);
        tabs.setTitleAt(tabs.getSelectedIndex(),file.getName());
    }

    public void saveFileAs(File file){
        JFileChooser fc = new JFileChooser();
        if(file!=null) fc.setSelectedFile(file);
        else if(currentDir!=null){
            fc.setCurrentDirectory(currentDir);
        }
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentArea.getFileSettings().currentFile = fc.getSelectedFile();
            currentDir = currentArea.getFileSettings().currentFile.getParentFile();
            new Lang(){
                public void execute(){}
            }.writeFile(currentArea.getFileSettings().currentFile, currentArea.getText());
            currentArea.getFileSettings().currentCode = currentArea.getText();
            setTitle(TITLE + " " + currentArea.getFileSettings().currentFile.getAbsolutePath());
            isTempFile=false;
        }
    }

    public void setArgs(String[] argv){
        this.args = argv;
    }

    public String getArgsAsString(){
        if(args==null) return "";
        String a = "";
        for(String s:args){
            a += s + " ";
        }
        return a.trim();

    }
    String exportPath = null;
    public void setExport(String path){
        exportPath = path;
    }

    public void addMenubar(){

        JMenuBar menuBar = new JMenuBar();
        this.setJMenuBar(menuBar);

        JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);

        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onExit();
                editor.dispose();
            }
        });

        JMenuItem newf = new JMenuItem("New");
        newf.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                newFile();
            }
        });
        fileMenu.add(newf);
        fileMenu.addSeparator();

        JMenuItem open = new JMenuItem("Open...");
        open.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openFile();
            }
        });
        fileMenu.add(open);
        fileMenu.addSeparator();
        JMenuItem save = new JMenuItem("Save");
        save.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveFile();
            }
        });
        fileMenu.add(save);
        JMenuItem saveAs = new JMenuItem("Save as...");
        saveAs.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveFileAs();
            }
        });
        fileMenu.add(saveAs);

        close = new JMenuItem("Close");
        close.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                    closeFile();
            }
        });
        fileMenu.addSeparator();
        fileMenu.add(close);
        fileMenu.addSeparator();
        fileMenu.add(exit);

        JMenu gazMenu = new JMenu("jGazm");
        dump = new JCheckBoxMenuItem("-dump");
        gazMenu.add(dump);

        clean = new JCheckBoxMenuItem("-clean", true);
        gazMenu.add(clean);

        keep = new JCheckBoxMenuItem("-keepsource");
        gazMenu.add(keep);

        imp = new JCheckBoxMenuItem("-noimport");
        gazMenu.add(imp);

        show = new JCheckBoxMenuItem("-showcp");
        gazMenu.add(show);

        check = new JCheckBoxMenuItem("-check");
        gazMenu.add(check);

        java = new JCheckBoxMenuItem("-java");
        java = new JCheckBoxMenuItem("-java");
        gazMenu.add(java);

        gazMenu.addSeparator();

        exp = new JMenuItem("Export...");
        exp.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String path = JOptionPane.showInputDialog(editor,"Destination path:",exportPath!=null?exportPath:"");
                if(path==null) return;
                if(path.length()==0) {
                    exportPath = null;
                    return;
                }
                exportPath = path;
                System.err.println(path);
                try{
                    List<String> flags = getFlags();
                    flags.add("-export:" + path);
                    (new JGazm(args)).runScript(new StringBuffer(getText()), flags);
                }catch(Exception ex){
                    ex.printStackTrace();
                }
            }
        });
        gazMenu.add(exp);

        JMenuItem dumpFile = new JMenuItem("Dump to file...");
        dumpFile.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                File p = new File(System.getProperty("jgazm.bin")).getParentFile().getParentFile();
                File exp = new File(p.getAbsolutePath() + File.separatorChar + "dump.java");
                String f = JOptionPane.showInputDialog(editor, "File:", exp.getAbsolutePath());
                if(f.length()==0) f = null;
                setDumpFile(f);
            }
        });
        gazMenu.add(dumpFile);

        JMenuItem args = new JMenuItem("Command line arguments...");
        args.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String argv = JOptionPane.showInputDialog(editor, "Input command line args. Space separated.", getArgsAsString());
                if (argv != null) {
                    setArgs(argv.split(" "));
                }
            }
        });
        gazMenu.add(args);
        gazMenu.addSeparator();

        JMenu help = new JMenu("Help");
        JMenuItem web = new JMenuItem("CrazedoutSoft website...");
        web.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try{
                    new Lang(){
                        public void execute(){}
                    }.startWindowsFile("http://www.crazedout.com");
                }catch(IOException ex){
                    ex.printStackTrace();
                }
            }
        });
        help.add(web);
        help.addSeparator();
        JMenuItem rs = new JMenuItem("RSyntaxTextArea...");
        rs.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try{
                    new Lang(){
                        public void execute(){}
                    }.startWindowsFile("https://github.com/bobbylight/RSyntaxTextArea/blob/master/src/main/dist/RSyntaxTextArea.License.txt");
                }catch(IOException ex){
                    ex.printStackTrace();
                }
            }
        });
        help.add(rs);
        help.addSeparator();
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showAboutDialog();
            }
        });
        help.add(about);

        menuBar.add(gazMenu);
        menuBar.add((apiMenu=showLangApi()));

        toolsMenu = new JMenu("Tools");
        menuBar.add(toolsMenu);
        menuBar.add(help);
        libs = new JMenu("Libs");
        if(getHome()!=null){
            File f = new File(System.getProperty("jgazm.home"));
            File lib = new File(f.getAbsolutePath() + File.separatorChar + "lib");
            if(lib.exists()){
                File[] files = lib.listFiles();
                for(final File fi:files){
                    if(fi.getAbsolutePath().toLowerCase().endsWith(".jgazm")){
                        JMenuItem i = new JMenuItem(fi.getName());
                        i.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                                String text = currentArea.getText();
                                currentArea.setText("@include \"./lib/" + fi.getName() + "\"" + Lang.EOL + text);
                            }
                });
                    libs.add(i);
                    }
                }
                toolsMenu.add(libs);
            }
        }

        JMenuItem addCp = new JMenuItem("Add to classpath...");
        addCp.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                File f = new Lang(){
                    public void execute(){}
                }.showOpenFileDialog(editor,currentArea.getFileSettings().currentFile.getParentFile());
                if(f!=null){
                    currentArea.setText("@classpath \"" + f.getAbsolutePath() + "\"" + Lang.EOL +  currentArea.getText());
                }
            }
        });
        toolsMenu.addSeparator();
        toolsMenu.add(addCp);
        currentArea.getPopupMenu().add(showLangApi());
    }

    private String getHome(){
        return System.getProperty("jgazm.home");
    }


    private void showAboutDialog(){
        String version = new JGazm().getVersion();
        String msg = "jGazm " + version + " Copyright CrazedoutSoft 2016." + Lang.EOL;
        msg += "jGazm is a Java Language scriptning tool"+Lang.EOL+"designed to enable lightning fast Java scripting."+Lang.EOL;
        msg += "Everything the JVM has to offer is now at your service"+Lang.EOL+"by scripting Java code and getting instant"+Lang.EOL;
        msg += "compilation at runtime."+Lang.EOL+Lang.EOL;
        msg += "info@crazedout.com"+Lang.EOL;
        msg += "jgazm.home=" + System.getProperty("jgazm.home") + Lang.EOL;
        msg += "jgazm.props=" + System.getProperty("jgazm.props") + Lang.EOL;
        msg += "jgazm.workdir=" + System.getProperty("jgazm.workdir") + Lang.EOL;
        JDialog d = new JDialog(this,"About jGazm",true);
        d.setTitle("About jGazm v." + version);
        JTextArea area = new JTextArea();
        area.setEditable(false);
        d.setLayout(new BorderLayout());
        d.setSize(600, 300);
        area.setText(msg);
        d.add("Center", area);
        d.setLocationRelativeTo(this);
        d.setVisible(true);

    }

    private CompletionProvider createCompletionProvider() {

        // A DefaultCompletionProvider is the simplest concrete implementation
        // of CompletionProvider. This provider has no understanding of
        // language semantics. It simply checks the text entered up to the
        // caret position for a match against known completions. This is all
        // that is needed in the majority of cases.
        DefaultCompletionProvider provider = new DefaultCompletionProvider(){
            @Override
            protected boolean isValidChar(char ch) {
                return ch=='.' || ch == '@' || super.isValidChar(ch);
            }
        };
        // Add completions for all Java keywords. A BasicCompletion is just
        // a straightforward word completion.
        provider.setAutoActivationRules(true, ".");
        /*
        provider.addCompletion(new BasicCompletion(provider, "private "));
        provider.addCompletion(new BasicCompletion(provider, "public "));
        provider.addCompletion(new BasicCompletion(provider, "abstract "));
        provider.addCompletion(new BasicCompletion(provider, "assert"));
        provider.addCompletion(new BasicCompletion(provider, "break;"));
        provider.addCompletion(new BasicCompletion(provider, "case:"));
        provider.addCompletion(new BasicCompletion(provider, "default:"));
        // ... etc ...
        provider.addCompletion(new BasicCompletion(provider, "transient "));
        provider.addCompletion(new BasicCompletion(provider, "void "));
        provider.addCompletion(new BasicCompletion(provider, "volatile "));
        provider.addCompletion(new BasicCompletion(provider, "while("));
        provider.addCompletion(new BasicCompletion(provider, "for"));
        provider.addCompletion(new BasicCompletion(provider, "switch("));

        provider.addCompletion(new BasicCompletion(provider, "Integer.parseInt("));
        provider.addCompletion(new BasicCompletion(provider, "Integer"));
        provider.addCompletion(new BasicCompletion(provider, "Double"));
        provider.addCompletion(new BasicCompletion(provider, "int"));
        provider.addCompletion(new BasicCompletion(provider, "double"));
        provider.addCompletion(new BasicCompletion(provider, "void"));
        */
        provider.addCompletion(new BasicCompletion(provider, "@method"));
        provider.addCompletion(new BasicCompletion(provider, "@class"));
        provider.addCompletion(new BasicCompletion(provider, "@thread"));
        provider.addCompletion(new BasicCompletion(provider, "@import"));
        provider.addCompletion(new BasicCompletion(provider, "@classpath"));
        provider.addCompletion(new BasicCompletion(provider, "@exception"));
        provider.addCompletion(new BasicCompletion(provider, "@string(name=\""));
        provider.addCompletion(new BasicCompletion(provider, "@global"));
        provider.addCompletion(new BasicCompletion(provider, "@include"));
        provider.addCompletion(new BasicCompletion(provider, "@catch"));
        List<String> list = (new JGazm()).getApi("Lang");
        for(String s:list){
            try{
                if (s.endsWith("execute()")) continue;
                String[] split = s.split(" ");
                final String res = split[1];
                int index = s.indexOf(" static ") > -1 ? 3 : 2;
                final String pattern = split[index].substring("com.crazedout.jgazm.Lang.".length());
                String met = ((res.indexOf("java.lang.") > -1 ? res.substring("java.lang.".length()) : res) + " " + pattern).split(" ")[1];
                //provider.addCompletion(new BasicCompletion(provider, met));
                provider.addCompletion(new ShorthandCompletion(provider, met,
                        met.substring(0,met.indexOf("(")+1), met));
            }catch(Exception ex){
                System.err.println("Add Completion: " + ex.getMessage());
            }
        }
        // Add a couple of "shorthand" completions. These completions don't
        // require the input text to be the same thing as the replacement text.
        /*
        provider.addCompletion(new ShorthandCompletion(provider, "syserr",
                "System.err.println(\nJanne", "System.err.println(\nJanne"));
        */
        return provider;

    }

    private JMenu showLangApi(){
        //String cls[] = {"Lang"};
        JMenu main = new JMenu("API");
        //for(String cs:cls) {
            List<String> list = new JGazm().getApi("Lang");
            //JMenu menu = new JMenu(cs);
            for (String s : list) {
                if (s.endsWith("execute()")) continue;
                String[] split = s.split(" ");
                final String res = split[1];
                int index = s.indexOf(" static ") > -1 ? 3 : 2;
                final String pattern = split[index].substring("com.crazedout.jgazm.Lang.".length());
                String met = (res.indexOf("java.lang.") > -1 ? res.substring("java.lang.".length()) : res) + " " + pattern;
                JMenuItem i = new JMenuItem(met);
                i.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        String pat = "";
                        String na = pattern.substring(0, pattern.indexOf("("));
                        String[] arg = pattern.substring(pattern.indexOf("(")).replace("(", "").replace(")", "").split(",");
                        String argvs = "";
                        for (String s : arg) {
                            String obj = s.substring(s.lastIndexOf(".") + 1);
                            argvs += (obj.toLowerCase() + ", ");
                        }
                        String met = na + "(" + argvs.substring(0, argvs.lastIndexOf(",")) + ")";
                        if (res.equals("void")) {
                            pat = met;
                        } else {
                            if (res.indexOf("java.lang.") > -1) {
                                pat = res.substring("java.lang.".length()) + " var = " + met;
                            } else {
                                pat = res + " var = " + met;
                            }
                        }
                        currentArea.append("\n" + pat + ";");
                    }
                });
                main.add(i);
            }
            //main.add(menu);
        //}
        return main;
    }


    public static void main(String argv[]) throws Exception {
        if(System.getProperty("jgazm.home")==null){
            System.setProperty("jgazm.home",
                    System.getProperty("user.dir"));
        }
        if(System.getProperty("jgazm.props")==null){
            File f = new File(System.getProperty("user.dir") + File.separatorChar + "user.properties");
            if(f.exists()){
                System.setProperty("jgazm.props",f.getAbsolutePath());
            }
        }
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Editor e = new Editor();
            }
        });

    }

}
