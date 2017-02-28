package com.crazedout.jgazm.editor;

/**
 * Created by NRKFRR on 2016-04-15.
 */

import javax.swing.*;
import java.io.IOException;
import java.io.OutputStream;


public class TextAreaOutputStream extends OutputStream
{

    private final JTextArea textArea;

    private final StringBuilder sb = new StringBuilder();

    public TextAreaOutputStream(final JTextArea textArea)
    {
        this.textArea = textArea;
    }

    @Override
    public void flush()
    {
    }

    @Override
    public void close()
    {
    }

    @Override
    public void write(int b) throws IOException
    {

        if (b == '\r')
            return;

        if (b == '\n')
        {
            final String text = sb.toString();
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    textArea.append(text);
                }
            });
            sb.setLength(0);
        }
        sb.append((char) b);
    }
}