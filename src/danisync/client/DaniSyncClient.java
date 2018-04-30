/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package danisync.client;

import java.awt.BorderLayout;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;
import javax.swing.text.DefaultCaret;

/**
 *
 * @author Stefano Fiordi
 */
public class DaniSyncClient extends JFrame {

    JTextArea monitor;
    JScrollPane monitorScroll;
    JTextField folderText;
    JButton chooseButton;
    JButton conButton;
    JButton syncButton;
    File syncFolder;
    Thread thCalcCRC;
    int ChunkSize = 1024 * 1024;
    FileHandlerClient[] Files;

    public DaniSyncClient() throws HeadlessException {
        int width = (Toolkit.getDefaultToolkit().getScreenSize().width * 34) / 100;
        int heigth = (Toolkit.getDefaultToolkit().getScreenSize().height * 20) / 100;
        int x = (Toolkit.getDefaultToolkit().getScreenSize().width * 33) / 100;
        int y = (Toolkit.getDefaultToolkit().getScreenSize().height * 20) / 100;
        super.setBounds(x, y, width, heigth);
        super.setResizable(false);
        super.setTitle("DaniSync - Client");
        super.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        super.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (JOptionPane.showConfirmDialog(DaniSyncClient.this, "Sei sicuro di voler uscire?", "Esci", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                    System.exit(0);
                }
            }
        });
        folderText = new JTextField("\\");
        folderText.setEditable(false);
        this.add(folderText, BorderLayout.PAGE_START);
        monitor = new JTextArea();
        monitor.setEditable(false);
        monitorScroll = new JScrollPane(monitor, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        DefaultCaret caret = (DefaultCaret) monitor.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        this.add(monitorScroll, BorderLayout.CENTER);
        chooseButton = new JButton("Sfoglia...");
        this.add(chooseButton, BorderLayout.LINE_START);
        chooseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser dirChooser = new JFileChooser();
                dirChooser.setDialogTitle("Seleziona cartella");
                dirChooser.setApproveButtonText("Seleziona");
                dirChooser.setApproveButtonToolTipText("Seleziona la cartella");
                dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                dirChooser.setAcceptAllFileFilterUsed(false);
                int result = dirChooser.showOpenDialog(null);
                if (result == JFileChooser.APPROVE_OPTION) {
                    if (dirChooser.getSelectedFile().exists()) {
                        syncFolder = dirChooser.getSelectedFile();
                        folderText.setText(syncFolder.getPath());
                        int nFiles = 0;
                        for (File f : syncFolder.listFiles()) {
                            if (!f.isDirectory()) {
                                nFiles++;
                            }
                        }
                        Files = new FileHandlerClient[nFiles];
                        int i = 0;
                        for (File f : syncFolder.listFiles()) {
                            if (!f.isDirectory()) {
                                try {
                                    Files[i] = new FileHandlerClient(f, ChunkSize);
                                    i++;
                                } catch (IOException | MyExc ex) {
                                    JOptionPane.showMessageDialog(DaniSyncClient.this, "Errore di lettura file");
                                }
                            }
                        }
                    } else {
                        JOptionPane.showMessageDialog(DaniSyncClient.this, "Directory inesistente");
                    }
                }
            }
        });
        conButton = new JButton("Calcola CRC");
        this.add(conButton, BorderLayout.LINE_END);
        conButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (conButton.getText().equals("Annulla")) {
                    if (thCalcCRC != null && thCalcCRC.isAlive()) {
                        try {
                            thCalcCRC.interrupt();
                            monitor.append("Operazione annullata\n");
                            conButton.setText("Calcola CRC");
                        } catch (SecurityException ex) {
                            JOptionPane.showMessageDialog(DaniSyncClient.this, "Impossibile annullare l'operazione");
                        }
                    }
                } else {
                    if (Files != null) {
                        if (Files.length != 0) {
                            calculateCRC();
                            thCalcCRC.start();
                            conButton.setText("Annulla");
                        } else {
                            JOptionPane.showMessageDialog(DaniSyncClient.this, "Nessun file trovato");
                        }
                    } else {
                        JOptionPane.showMessageDialog(DaniSyncClient.this, "Cartella non selezionata o non valida");
                    }
                }
            }
        });
        syncButton = new JButton("Sincronizza");
        this.add(syncButton, BorderLayout.PAGE_END);
    }

    private void calculateCRC() {
        thCalcCRC = new Thread(new Runnable() {
            @Override
            public void run() {
                monitor.append("Inizio indicizzazione...\n");
                if (!new File("Indexes\\").exists()) {
                    new File("Indexes\\").mkdir();
                }
                for (FileHandlerClient f : Files) {
                    monitor.append("Indicizzazione di " + f.ClientFile.getName() + "\n");
                    try {
                        f.FileIndexing();
                        monitor.append("Completata\n");
                    } catch (IOException | MyExc ex) {
                        monitor.append("Errore durante il calcolo\n");
                    }
                }
                monitor.append("Fine indicizzazione\n");
                conButton.setText("Calcola CRC");
            }
        });
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        new DaniSyncClient().setVisible(true);
        /*File newVersion = new File("E:/dati/Download/ubuntu-17.10.1-desktop-amd64.iso");
        try {
            FileHandlerClient fhc = new FileHandlerClient(newVersion, 1024 * 1024);
            fhc.FileIndexing();
            System.out.println("Version: " + fhc.version);
        } catch (IOException | MyExc ex) {
            System.err.println("Error: " + ex.getMessage());
        }

        try {
            Socket socket = new Socket("127.0.0.1", 6365);

        } catch (IOException ex) {
            System.err.println("Error: " + ex.getMessage());
        }*/
    }

}
