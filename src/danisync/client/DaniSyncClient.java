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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
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
 * Classe principale che crea e gestisce la GUI, gestisce la cartella da
 * sincronizzare (indicizzazione e sincronizzazione) e gestisce la connessione.
 *
 * @author Stefano Fiordi
 */
public class DaniSyncClient extends JFrame {

    JTextArea monitor;
    JScrollPane monitorScroll;
    JTextField dirText;
    JButton chooseButton;
    JButton calcButton;
    JButton conButton;
    JButton syncButton;
    File syncDir;
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
        super.setTitle("Dani3la");
        super.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        super.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (JOptionPane.showConfirmDialog(DaniSyncClient.this, "Sei sicuro di voler uscire?", "Esci", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                    try {
                        writeConfig();
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(DaniSyncClient.this, "Salvataggio configurazione fallito");
                    }
                    System.exit(0);
                }
            }
        });
        Container pane = this.getContentPane();
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
                                    Files[i] = new FileHandlerClient(f, ChunkSize);
                                    /*if (Files[i].crcIndex.exists()) {
                                        monitor.append("Letto file indice " + Files[i].crcIndex.getName() + " | Codice versione: " + Files[i].version + "\n");
                                    }*/
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
                if (conButton.getText().equals("Connetti")) {
                    if (Files != null) {
                        connect();
                        thConnect.start();
                    } else {
                        JOptionPane.showMessageDialog(DaniSyncClient.this, "Cartella non selezionata o non valida");
                    }
                } else {
                    disconnect();
                }
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
        syncButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (thSync == null || !thSync.isAlive()) {
                    if (Files != null) {
                        if (checkCRCIndexes()) {
                            synchronize();
                            thSync.start();
                        } else {
                            JOptionPane.showMessageDialog(DaniSyncClient.this, "Uno o più file indice mancanti");
                        }
                    } else {
                        JOptionPane.showMessageDialog(DaniSyncClient.this, "Cartella non selezionata o non valida");
                    }
                } else {
                    JOptionPane.showMessageDialog(DaniSyncClient.this, "Sincronizzazione già in corso");
                }
            }
        });

        try {
            readConfig();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(DaniSyncClient.this, "Caricamento configurazione fallito");
        }
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
                        monitor.append("Completata | Codice versione: " + f.version + "\n");
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
                    monitor.append("Host connesso: " + socket.getRemoteSocketAddress() + "\n");
                    conButton.setText("Disconnetti");
                    syncButton.setEnabled(true);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(DaniSyncClient.this, "Errore di connessione");
                }
            }
        });
    }

    private void disconnect() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socket.close();
                    conButton.setText("Connetti");
                    syncButton.setEnabled(false);
                } catch (IOException ex) {
                }
            }
        }).start();
    }

    private void synchronize() {
        thSync = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    createCRCInfoPacket().writeDelimitedTo(socket.getOutputStream());
                    monitor.append("Inizio sincronizzazione...\n");
                    while (true) {
                        crcReq request = crcReq.parseDelimitedFrom(socket.getInputStream());
                        if (request.getCrc().equals("end")) {
                            resp.newBuilder().setRes("ok").build().writeDelimitedTo(socket.getOutputStream());
                            monitor.append("Fine trasferimento file indice\n");
                            break;
                        }
                        monitor.append("Invio " + request.getCrc() + "...\n");
                        for (int i = 0; i < Files.length; i++) {
                            if (Files[i].crcIndex.getName().equals(request.getCrc()) && Files[i].version == request.getVer()) {
                                Files[i].getCRCIndexInfoPacket().writeDelimitedTo(socket.getOutputStream());

                                resp infoRespPacket = resp.parseDelimitedFrom(socket.getInputStream());
                                if (infoRespPacket.getRes().equals("ok")) {
                                    monitor.append("Inizio trasferimento " + Files[i].crcIndex.getName() + "...\n");
                                    int errors = 0, j = 0;
                                    OUTER:
                                    for (; j < Files[i].nCRCIndexPackets; j++) {
                                        try {
                                            Files[i].buildCRCIndexPacket(j).writeDelimitedTo(socket.getOutputStream());
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
                                    if (j == Files[i].nCRCIndexPackets) {
                                        /*monitor.append("Trasferimento completato con successo\n");
                                        Files[i].getInfoPacket().writeDelimitedTo(socket.getOutputStream());

                                        resp fInfoRespPacket = resp.parseDelimitedFrom(socket.getInputStream());
                                        if (fInfoRespPacket.getRes().equals("ok")) {
                                            monitor.append("Inizio trasferimento " + Files[i].ClientFile.getName() + "...\n");
                                            int fErrors = 0, k;
                                            OUTER:
                                            for (k = fInfoRespPacket.getInd(); k < Files[i].nPackets; k++) {
                                                try {
                                                    Files[i].buildPacket(k).writeDelimitedTo(socket.getOutputStream());
                                                    fErrors = 0;
                                                    resp respPacket = resp.parseDelimitedFrom(socket.getInputStream());
                                                    switch (respPacket.getRes()) {
                                                        case "wp":
                                                            k = respPacket.getInd() - 1;
                                                            break;
                                                        case "mrr":
                                                            monitor.append("Errore di trasferimento\n");
                                                            break OUTER;
                                                    }
                                                } catch (MyExc | IOException ex) {
                                                    if (fErrors == 3) {
                                                        monitor.append("Errore di lettura: impossibile trasferire il file\n");
                                                        data.newBuilder().setNum(-1);
                                                        break;
                                                    } else {
                                                        fErrors++;
                                                        k--;
                                                    }
                                                }
                                            }
                                            if (k == Files[i].nPackets) {
                                                monitor.append("Trasferimento completato con successo\n");
                                                resp.newBuilder().setRes("ok").build().writeDelimitedTo(socket.getOutputStream());
                                            } else {
                                                monitor.append("Trasferimento fallito\n");
                                                resp.newBuilder().setRes("not").build().writeDelimitedTo(socket.getOutputStream());
                                            }
                                        }
                                    } else {*/
                                        resp.newBuilder().setRes("ok").build().writeDelimitedTo(socket.getOutputStream());
                                        monitor.append("Trasferimento file crc completato\n");
                                    } else {
                                        resp.newBuilder().setRes("not").build().writeDelimitedTo(socket.getOutputStream());
                                        monitor.append("Trasferimento file crc fallito\n");
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
                                Files[i].getChunkInfoPacket(chunkReqPacket.getInd()).writeDelimitedTo(socket.getOutputStream());

                                resp chunkInfoRespPacket = resp.parseDelimitedFrom(socket.getInputStream());
                                if (chunkInfoRespPacket.getRes().equals("ok")) {
                                    int errors = 0, j;
                                    OUTER:
                                    for (j = 0; j < Files[i].nChunkPackets; j++) {
                                        try {
                                            Files[i].buildChunkPacket(j).writeDelimitedTo(socket.getOutputStream());
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
                                        resp.newBuilder().setRes("ok").build().writeDelimitedTo(socket.getOutputStream());
                                        monitor.append("Trasferimento completato\n");
                                    } else {
                                        resp.newBuilder().setRes("not").build().writeDelimitedTo(socket.getOutputStream());
                                        monitor.append("Trasferimento fallito\n");
                                    }
                                }

                                break;
                            }
                        }
                    }
                    
                    monitor.append("Sincronizzazione completata\n");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(DaniSyncClient.this, "Errore di connessione");
                    conButton.setText("Connetti");
                    syncButton.setEnabled(false);
                }
                /*try {
                    socket.close();
                    conButton.setText("Connetti");
                    syncButton.setEnabled(false);
                } catch (IOException ex) {
                }*/
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
            String s = reader.readLine();
            if (s.contains("Path = ")) {
                syncDir = new File(s.split(" = ")[1]);
                if (!syncDir.exists() || !syncDir.isDirectory()) {
                    syncDir = null;
                } else if (syncDir.exists() && syncDir.isDirectory()) {
                    dirText.setText(syncDir.getAbsolutePath());
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
                                Files[i] = new FileHandlerClient(f, ChunkSize);
                                /*if (Files[i].crcIndex.exists()) {
                                    monitor.append("Letto file indice " + Files[i].crcIndex.getName() + " | Codice versione: " + Files[i].version + "\n");
                                }*/
                                i++;
                            } catch (IOException | MyExc ex) {
                                JOptionPane.showMessageDialog(DaniSyncClient.this, "Errore di lettura file");
                            }
                        }
                    }
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

        new DaniSyncClient().setVisible(true);
    }
}
