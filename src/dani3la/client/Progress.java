/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dani3la.client;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.Toolkit;
import java.io.IOException;
import javax.swing.JFrame;
import static javax.swing.JFrame.EXIT_ON_CLOSE;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;

/**
 *
 * @author Stefano Fiordi
 */
public class Progress extends JFrame {

    Container pane;
    Component[] comps;
    GridBagConstraints gbc;

    /**
     * Costruttore che inizializza il frame
     * 
     * @throws HeadlessException 
     */
    public Progress() throws HeadlessException {
        int width = (Toolkit.getDefaultToolkit().getScreenSize().width * 34) / 100;
        int heigth = (Toolkit.getDefaultToolkit().getScreenSize().height * 50) / 100;
        int x = (Toolkit.getDefaultToolkit().getScreenSize().width * 60) / 100;
        int y = (Toolkit.getDefaultToolkit().getScreenSize().height * 20) / 100;
        super.setBounds(x, y, width, heigth);
        super.setMinimumSize(new Dimension(300, 150));
        super.setTitle("Dani3la - progress");
        super.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        super.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                setVisible(false);
            }
        });

        JPanel mPane = new JPanel();
        JScrollPane scroll = new JScrollPane(mPane);
        this.add(scroll);

        pane = mPane;
        pane.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        comps = new Component[0];
    }

    /**
     * Metodo per il riallocamento dell'array di Components
     * 
     * @param length la nuova lunghezza dell'array
     */
    private void realloc(int length) {
        Component[] newArray = new Component[length];
        if (length >= comps.length) {
            System.arraycopy(comps, 0, newArray, 0, comps.length);
        } else {
            System.arraycopy(comps, 0, newArray, 0, length);
        }

        comps = newArray;
    }

    /**
     * Metodo che accoda una nuova riga
     * 
     * @param s la stringa della nuova label
     * @param maxVal valore massimo progress bar
     * @return l'indice dei nuovi componenti
     */
    protected int addRow(String s, int maxVal) {
        if (comps.length != 0) {
            pane.remove(comps[comps.length - 1]);
            pane.remove(comps[comps.length - 2]);

            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.PAGE_START;
            gbc.weighty = 0;
            gbc.weightx = 0;
            gbc.gridy = comps.length / 2;
            gbc.gridwidth = 1;
            gbc.ipady = 10;
            gbc.insets = new Insets(20, 10, 10, 10);
            pane.add(comps[comps.length - 2], gbc);

            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.PAGE_START;
            gbc.weighty = 0;
            gbc.weightx = 50;
            gbc.gridy = comps.length / 2;
            gbc.gridwidth = 2;
            gbc.ipady = 10;
            gbc.insets = new Insets(20, 10, 10, 10);
            pane.add(comps[comps.length - 1], gbc);
        }

        realloc(comps.length + 2);

        comps[comps.length - 2] = new JLabel(s);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.PAGE_START;
        gbc.weighty = 1;
        gbc.weightx = 0;
        gbc.gridy = comps.length / 2;
        gbc.gridwidth = 1;
        gbc.ipady = 10;
        gbc.insets = new Insets(20, 10, 10, 10);
        pane.add(comps[comps.length - 2], gbc);

        comps[comps.length - 1] = new JProgressBar(0, maxVal);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.PAGE_START;
        gbc.weighty = 1;
        gbc.weightx = 50;
        gbc.gridy = comps.length / 2;
        gbc.gridwidth = 2;
        gbc.ipady = 10;
        gbc.insets = new Insets(20, 10, 10, 10);
        pane.add(comps[comps.length - 1], gbc);

        pane.revalidate();
        pane.repaint();

        return comps.length - 2;
    }
    
    /**
     * Metodo che elimina una riga
     * 
     * @param index l'indice dei componenti da eliminare
     */
    protected void delRow(int index) {
        pane.remove(comps[index + 1]);
        pane.remove(comps[index]);
        
        pane.revalidate();
        pane.repaint();
    }
}
