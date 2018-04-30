/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package danisync.client;

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
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
import packet.protoPacket.crcInfo;
import packet.protoPacket.crcReq;
import packet.protoPacket.resp;
import packet.protoPacket.chunkReq;

/**
 *
 * @author Stefano Fiordi
 */
public class DaniSyncClient extends JFrame {

    JTextArea monitor;
    JScrollPane monitorScroll;
    JTextField folderText;
    JButton chooseButton;
    JButton calcButton;
    JButton conButton;
    JButton syncButton;
    File syncFolder;
    Thread thCalcCRC;
    Thread thConnect;
    Thread thSync;
    Socket socket;
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
        Container pane = this.getContentPane();
        pane.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;

        folderText = new JTextField("\\");
        folderText.setEditable(false);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 50;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.ipady = 10;
        pane.add(folderText, gbc);

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

        calcButton = new JButton("Calcola CRC");
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
                if (calcButton.getText().equals("Annulla")) {
                    if (thCalcCRC != null && thCalcCRC.isAlive()) {
                        try {
                            thCalcCRC.interrupt();
                            monitor.append("Operazione annullata\n");
                            calcButton.setText("Calcola CRC");
                        } catch (SecurityException ex) {
                            JOptionPane.showMessageDialog(DaniSyncClient.this, "Impossibile annullare l'operazione");
                        }
                    }
                } else {
                    if (Files != null) {
                        if (Files.length != 0) {
                            calculateCRC();
                            thCalcCRC.start();
                            calcButton.setText("Annulla");
                        } else {
                            JOptionPane.showMessageDialog(DaniSyncClient.this, "Nessun file trovato");
                        }
                    } else {
                        JOptionPane.showMessageDialog(DaniSyncClient.this, "Cartella non selezionata o non valida");
                    }
                }
            }
        });

        conButton = new JButton("Connetti");
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.weighty = 0.0;
        gbc.gridy = 2;
        gbc.ipady = 15;
        gbc.gridwidth = 1;
        pane.add(conButton, gbc);
        conButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                connect();
                thConnect.start();
            }
        });

        syncButton = new JButton("Sincronizza");
        syncButton.setEnabled(false);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.weighty = 0.0;
        gbc.gridy = 2;
        gbc.ipady = 15;
        gbc.gridwidth = 1;
        pane.add(syncButton, gbc);
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
                calcButton.setText("Calcola CRC");
            }
        });
    }

    private void connect() {
        thConnect = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socket = new Socket("127.0.0.1", 6365);
                    syncButton.setEnabled(true);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(DaniSyncClient.this, "Errore di connessione");
                }
            }
        });
    }

    private void synchronize() {
        thSync = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    createCRCInfoPacket().writeDelimitedTo(socket.getOutputStream());

                    while (true) {
                        crcReq request = crcReq.parseDelimitedFrom(socket.getInputStream());
                        if (request.getCrc().equals("end")) {
                            break;
                        }
                        for (int i = 0; i < Files.length; i++) {
                            if (Files[i].crcIndex.getName().equals(request.getCrc())) {
                                Files[i].getInfoPacket().writeDelimitedTo(socket.getOutputStream());

                                resp infoRespPacket = resp.parseDelimitedFrom(socket.getInputStream());
                                if (infoRespPacket.getRes().equals("ok")) {
                                    monitor.append("Inizio trasferimento " + Files[i].ClientFile.getName() + "...\n");
                                    int errors = 0, j;
                                    OUTER:
                                    for (j = infoRespPacket.getInd(); j < Files[i].nPackets; j++) {
                                        try {
                                            Files[i].buildPacket(j);
                                            errors = 0;
                                            resp respPacket = resp.parseDelimitedFrom(socket.getInputStream());
                                            switch (respPacket.getRes()) {
                                                case "wp":
                                                    j = respPacket.getInd() - 1;
                                                    break;
                                                case "mrr":
                                                    monitor.append("Errore di trasferimento\n");
                                                    break OUTER;
                                            }
                                        } catch (MyExc | IOException ex) {
                                            if (errors == 3) {
                                                monitor.append("Errore di lettura: impossibile trasferire il file\n");
                                                break;
                                            } else {
                                                errors++;
                                                j--;
                                            }
                                        }
                                    }
                                    if (j == Files[i].nPackets) {
                                        monitor.append("Trasferimento completato con successo\n");
                                    }
                                }

                                break;
                            }
                        }
                    }

                    while (true) {
                        chunkReq chunkReqPacket = chunkReq.parseDelimitedFrom(socket.getInputStream());
                        if (chunkReqPacket.getInd() == -1) {
                            break;
                        }
                        for (int i = 0; i < Files.length; i++) {
                            if (chunkReqPacket.getNam().equals(Files[i].ClientFile.getName())) {
                                monitor.append("Invio pezzo n." + chunkReqPacket.getInd() + " del file " + chunkReqPacket.getNam() + "...\n");
                                Files[i].setChunkToSend(chunkReqPacket.getInd());
                                int errors = 0, j;
                                OUTER:
                                for (j = 0; j < Files[i].nChunkPackets; j++) {
                                    try {
                                        Files[i].buildChunkPacket(j);
                                        errors = 0;
                                        resp respPacket = resp.parseDelimitedFrom(socket.getInputStream());
                                        switch (respPacket.getRes()) {
                                            case "wp":
                                                j = respPacket.getInd() - 1;
                                                break;
                                            case "mrr":
                                                monitor.append("Errore di trasferimento\n");
                                                break OUTER;
                                        }
                                    } catch (MyExc | IOException ex) {
                                        if (errors == 3) {
                                            monitor.append("Errore di lettura: impossibile trasferire il file\n");
                                            break;
                                        } else {
                                            errors++;
                                            j--;
                                        }
                                    }
                                }
                                if (j == Files[i].nChunkPackets) {
                                    monitor.append("Completato");
                                }
                                break;
                            }
                        }
                    }
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(DaniSyncClient.this, "Errore di connessione");
                }
            }
        });
    }

    private crcInfo createCRCInfoPacket() {
        crcInfo packet = crcInfo.newBuilder()
                .setNum(Files.length)
                .setCsz(ChunkSize)
                .build();

        int i = 0;
        for (FileHandlerClient fhc : Files) {
            packet = packet.toBuilder()
                    .setCrc(i, fhc.crcIndex.getName())
                    .setVer(i, fhc.version)
                    .build();
        }

        return packet;
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
