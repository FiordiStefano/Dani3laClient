/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dani3la.client;

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;
import javax.swing.text.DefaultCaret;
import packet.protoPacket.crcInfo;

/**
 * Classe principale che crea e gestisce la GUI, gestisce la cartella da
 * sincronizzare e gestisce la connessione con i server
 *
 * @author Stefano Fiordi
 */
public class Dani3laClient extends JFrame {

    JTextArea monitor;
    JScrollPane monitorScroll;
    JTextField dirText;
    JButton chooseButton;
    JButton calcButton;
    JButton conButton;
    JButton syncButton;
    JButton progButton;
    JProgressBar indProg;
    Container pane;
    Progress progress;
    //NetChoose netc;
    File syncDir;
    Thread thCalcCRC;
    Thread thConnect;
    Thread thSync;
    int ChunkSize = 1024 * 1024;
    FileHandlerClient[] Files;
    InetAddress netAddress;

    public Dani3laClient() throws HeadlessException {
        int width = (Toolkit.getDefaultToolkit().getScreenSize().width * 34) / 100;
        int heigth = (Toolkit.getDefaultToolkit().getScreenSize().height * 25) / 100;
        int x = (Toolkit.getDefaultToolkit().getScreenSize().width * 33) / 100;
        int y = (Toolkit.getDefaultToolkit().getScreenSize().height * 20) / 100;
        super.setBounds(x, y, width, heigth);
        //super.setResizable(false);
        super.setTitle("Dani3la");
        super.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        super.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (JOptionPane.showConfirmDialog(Dani3laClient.this, "Sei sicuro di voler uscire?", "Esci", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                    try {
                        writeConfig();
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(Dani3laClient.this, "Salvataggio configurazione fallito");
                    }
                    System.exit(0);
                }
            }
        });
        pane = this.getContentPane();
        pane.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;

        dirText = new JTextField("\\");
        dirText.setEditable(false);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 50;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.ipady = 10;
        pane.add(dirText, gbc);

        chooseButton = new JButton("Sfoglia...");
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.gridy = 0;
        gbc.ipady = 4;
        gbc.gridwidth = 1;
        pane.add(chooseButton, gbc);
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
                        syncDir = dirChooser.getSelectedFile();
                        dirText.setText(syncDir.getPath());
                    } else {
                        JOptionPane.showMessageDialog(Dani3laClient.this, "Directory inesistente");
                    }
                }
            }
        });

        monitor = new JTextArea();
        monitor.setEditable(false);
        monitorScroll = new JScrollPane(monitor, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        DefaultCaret caret = (DefaultCaret) monitor.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.gridy = 1;
        gbc.ipady = 0;
        gbc.gridwidth = 3;
        pane.add(monitorScroll, gbc);

        calcButton = new JButton("Genera indice");
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.weighty = 0.0;
        gbc.gridy = 2;
        gbc.ipady = 15;
        gbc.gridwidth = 1;
        pane.add(calcButton, gbc);
        calcButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (syncDir != null) {
                    if (calcButton.getText().equals("Annulla")) {
                        if (thCalcCRC != null && thCalcCRC.isAlive()) {
                            try {
                                thCalcCRC.interrupt();
                                indProg.setVisible(false);
                                monitor.append("Operazione annullata\n");
                                calcButton.setText("Genera indice");
                            } catch (SecurityException ex) {
                                JOptionPane.showMessageDialog(Dani3laClient.this, "Impossibile annullare l'operazione");
                            }
                        }
                    } else {
                        indProg = new JProgressBar(0, 100);
                        gbc.fill = GridBagConstraints.HORIZONTAL;
                        gbc.weightx = 1;
                        gbc.weighty = 0.0;
                        gbc.gridy = 3;
                        gbc.ipady = 15;
                        gbc.gridwidth = 3;
                        pane.add(indProg, gbc);

                        calculateCRC();
                        thCalcCRC.start();
                        calcButton.setText("Annulla");
                    }
                } else {
                    JOptionPane.showMessageDialog(Dani3laClient.this, "Cartella di sincronizzazione non selezionata");
                }
            }
        });

        syncButton = new JButton("Sincronizza");
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.weighty = 0.0;
        gbc.gridy = 2;
        gbc.ipady = 15;
        gbc.gridwidth = 1;
        pane.add(syncButton, gbc);
        syncButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (thSync == null || !thSync.isAlive()) {
                    if (Files != null) {
                        if (checkCRCIndexes()) {
                            synchronize();
                            thSync.start();
                        } else {
                            JOptionPane.showMessageDialog(Dani3laClient.this, "Uno o più file indice mancanti");
                        }
                    } else {
                        JOptionPane.showMessageDialog(Dani3laClient.this, "File indice non generati");
                    }
                } else {
                    JOptionPane.showMessageDialog(Dani3laClient.this, "Sincronizzazione già in corso");
                }
            }
        });

        progButton = new JButton("Progress");
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.weighty = 0.0;
        gbc.gridy = 2;
        gbc.ipady = 15;
        gbc.gridwidth = 1;
        pane.add(progButton, gbc);
        progButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                progress.setVisible(true);
            }
        });

        progress = new Progress();

        try {
            readConfig();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(Dani3laClient.this, "Caricamento configurazione fallito");
        }
    }

    private void calculateCRC() {
        thCalcCRC = new Thread(new Runnable() {
            @Override
            public void run() {
                int nFiles = 0;
                for (File f : syncDir.listFiles()) {
                    if (!f.isDirectory()) {
                        nFiles++;
                    }
                }
                Files = new FileHandlerClient[nFiles];
                int i = 0;
                for (File f : syncDir.listFiles()) {
                    if (!f.isDirectory()) {
                        try {
                            Files[i] = new FileHandlerClient(f, ChunkSize, indProg);
                            i++;
                        } catch (IOException | MyExc ex) {
                            JOptionPane.showMessageDialog(Dani3laClient.this, "Errore di lettura file");
                        }
                    }
                }

                monitor.append(LocalDateTime.now() + " | Inizio indicizzazione...\n");
                if (!new File("Indexes\\").exists()) {
                    new File("Indexes\\").mkdir();
                }
                for (FileHandlerClient f : Files) {
                    if (f.crcIndex.exists()) {
                        if (JOptionPane.showConfirmDialog(Dani3laClient.this, f.crcIndex.getName() + " esiste già. Vuoi generarne uno nuovo?", "Indicizzazione", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                            monitor.append(LocalDateTime.now() + " | Indicizzazione di " + f.ClientFile.getName() + "\n");
                            try {
                                f.FileIndexing();
                                monitor.append(LocalDateTime.now() + " | " + f.crcIndex.getName() + ": Completata | Codice versione: " + f.version + "\n");
                            } catch (IOException | MyExc ex) {
                                monitor.append(LocalDateTime.now() + " | Errore durante il calcolo\n");
                            }
                        } else {
                            try {
                                f.readDigests();
                                monitor.append(LocalDateTime.now() + " | " + f.crcIndex.getName() + ": Completata | Codice versione: " + f.version + "\n");
                            } catch (IOException | MyExc ex) {
                                JOptionPane.showMessageDialog(Dani3laClient.this, "Errore di lettura file indice");
                            }
                        }
                    } else {
                        monitor.append(LocalDateTime.now() + " | Indicizzazione di " + f.ClientFile.getName() + "\n");
                        try {
                            f.FileIndexing();
                            monitor.append(LocalDateTime.now() + " | Completata | Codice versione: " + f.version + "\n");
                        } catch (IOException | MyExc ex) {
                            monitor.append(LocalDateTime.now() + " | Errore durante il calcolo\n");
                        }
                    }
                }
                monitor.append(LocalDateTime.now() + " | Fine indicizzazione\n");
                calcButton.setText("Genera indice");

                pane.remove(indProg);
            }
        });
    }

    private void synchronize() {
        thSync = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (netAddress == null) {
                        netAddress = InetAddress.getLocalHost();
                    }
                    /*try {
                        netc = new NetChoose(NetworkInterface.getNetworkInterfaces());
                        netc.createAndShowGUI();
                    } catch (SocketException ex) {
                        JOptionPane.showMessageDialog(Dani3laClient.this, "Errore ricerca reti");
                    }*/

                    monitor.append(LocalDateTime.now() + " | Indirizzo IP corrente: " + netAddress + "\n");
                    byte[] bAddress = netAddress.getAddress();
                    for (int s = 0; s < 255; s++) {
                        bAddress[3] = new Integer(s).byteValue();
                        InetAddress address = InetAddress.getByAddress(bAddress);
                        Thread t = new Thread(new SyncThread(monitor, address, Files, progress, ChunkSize));
                        t.start();
                    }
                } catch (UnknownHostException ex) {
                    JOptionPane.showMessageDialog(Dani3laClient.this, "Impossible trovare indirizzo IP corrente");
                }
            }
        });
    }

    /**
     * Scrive la configurazione del client sul file cConfig.ini
     *
     * @throws IOException se ci sono errori durante la scrittura
     */
    public void writeConfig() throws IOException {
        if (syncDir != null) {
            BufferedWriter writer = new BufferedWriter(new FileWriter(new File("cConfig.ini"), false));
            String s = "Path = " + syncDir.getAbsolutePath();
            writer.write(s);

            writer.newLine();
            if (netAddress != null && netAddress != InetAddress.getLocalHost()) {
                s = "Address = " + netAddress.getLocalHost();
            } else {
                s = "Address = ";
            }
            writer.write(s);

            writer.close();
        }
    }

    /**
     * Legge la configurazione del client dal file cConfig.ini
     *
     * @throws IOException se ci sono errori durante la lettura
     */
    private void readConfig() throws IOException {
        if (new File("cConfig.ini").exists()) {
            BufferedReader reader = new BufferedReader(new FileReader(new File("cConfig.ini")));
            String s;
            if ((s = reader.readLine()) != null) {
                if (s.contains("Path = ")) {
                    syncDir = new File(s.split(" = ")[1]);
                    if (!syncDir.exists() || !syncDir.isDirectory()) {
                        syncDir = null;
                    } else if (syncDir.exists() && syncDir.isDirectory()) {
                        dirText.setText(syncDir.getAbsolutePath());
                    }
                }
            }
            if ((s = reader.readLine()) != null) {
                if (s.contains("Address = ")) {
                    netAddress = InetAddress.getByName(s.split(" = ")[1]);
                } else {
                    netAddress = InetAddress.getLocalHost();
                }
            }

            reader.close();
        }
    }

    private boolean checkCRCIndexes() {
        for (FileHandlerClient fhc : Files) {
            if (!fhc.crcIndex.exists()) {
                return false;
            }
        }

        return true;
    }

    private crcInfo createCRCInfoPacket() {
        crcInfo packet = crcInfo.newBuilder()
                .setCsz(ChunkSize)
                .build();

        for (FileHandlerClient fhc : Files) {
            packet = packet.toBuilder()
                    .addLen(fhc.ClientFile.length())
                    .addCrc(fhc.ClientFile.getName())
                    .addVer(fhc.version)
                    .addCln(fhc.crcIndex.length())
                    .build();
        }

        return packet;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        new Dani3laClient().setVisible(true);
    }
}
