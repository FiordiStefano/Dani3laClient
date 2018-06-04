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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
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
import packet.protoPacket.crcReq;
import packet.protoPacket.resp;
import packet.protoPacket.chunkReq;

/**
 * Classe principale che crea e gestisce la GUI, gestisce la cartella da
 * sincronizzare (indicizzazione e sincronizzazione) e gestisce la connessione.
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
    Progress progress;
    File syncDir;
    Thread thCalcCRC;
    Thread thConnect;
    Thread thSync;
    Socket[] sockets;
    int ChunkSize = 1024 * 1024;
    FileHandlerClient[] Files;

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
                                monitor.append("Operazione annullata\n");
                                calcButton.setText("Genera indice");
                            } catch (SecurityException ex) {
                                JOptionPane.showMessageDialog(Dani3laClient.this, "Impossibile annullare l'operazione");
                            }
                        }
                    } else {
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
                if (progress == null) {
                    progress = new Progress();
                }
                progress.setVisible(true);
            }
        });

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
                            Files[i] = new FileHandlerClient(f, ChunkSize);
                            i++;
                        } catch (IOException | MyExc ex) {
                            JOptionPane.showMessageDialog(Dani3laClient.this, "Errore di lettura file");
                        }
                    }
                }

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
                calcButton.setText("Genera indice");
            }
        });
    }

    private void synchronize() {
        thSync = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sockets = new Socket[1];
                    int z = 0;
                    monitor.append("Indirizzo IP corrente: " + InetAddress.getLocalHost() + "\n");
                    byte[] bAddress = InetAddress.getLocalHost().getAddress();
                    for (int s = 1; s < 255; s++) {
                        try {
                            bAddress[3] = new Integer(s).byteValue();
                            InetAddress address = InetAddress.getByAddress(bAddress);
                            sockets[z] = new Socket();
                            sockets[z].connect(new InetSocketAddress(address, 6365), 3000);
                            monitor.append("Host connesso: " + sockets[z].getRemoteSocketAddress() + "\n");
                            final int t = z;
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    int p = t;
                                    try {
                                        createCRCInfoPacket().writeDelimitedTo(sockets[p].getOutputStream());
                                        monitor.append(sockets[p].getRemoteSocketAddress() + ": Inizio sincronizzazione...\n");
                                        while (true) {
                                            crcReq request = crcReq.parseDelimitedFrom(sockets[p].getInputStream());
                                            if (request.getCrc().equals("end")) {
                                                resp.newBuilder().setRes("ok").build().writeDelimitedTo(sockets[p].getOutputStream());
                                                monitor.append(sockets[p].getRemoteSocketAddress() + ": Fine trasferimento file indice\n");
                                                break;
                                            }
                                            monitor.append(sockets[p].getRemoteSocketAddress() + ": Invio " + request.getCrc() + "...\n");
                                            synchronized (this) {
                                                for (int i = 0; i < Files.length; i++) {
                                                    if (Files[i].crcIndex.getName().equals(request.getCrc()) && Files[i].version == request.getVer()) {
                                                        Files[i].getCRCIndexInfoPacket().writeDelimitedTo(sockets[p].getOutputStream());

                                                        resp infoRespPacket = resp.parseDelimitedFrom(sockets[p].getInputStream());
                                                        if (infoRespPacket.getRes().equals("ok")) {
                                                            monitor.append(sockets[p].getRemoteSocketAddress() + ": Inizio trasferimento " + Files[i].crcIndex.getName() + "...\n");
                                                            int errors = 0, j = 0;
                                                            OUTER:
                                                            for (; j < Files[i].nCRCIndexPackets; j++) {
                                                                try {
                                                                    Files[i].buildCRCIndexPacket(j).writeDelimitedTo(sockets[p].getOutputStream());
                                                                    errors = 0;
                                                                    resp respPacket = resp.parseDelimitedFrom(sockets[p].getInputStream());
                                                                    switch (respPacket.getRes()) {
                                                                        case "wp":
                                                                            j = respPacket.getInd() - 1;
                                                                            break;
                                                                        case "mrr":
                                                                            monitor.append(sockets[p].getRemoteSocketAddress() + ": Errore di trasferimento\n");
                                                                            break OUTER;
                                                                    }
                                                                } catch (MyExc | IOException ex) {
                                                                    if (errors == 3) {
                                                                        monitor.append(sockets[p].getRemoteSocketAddress() + ": Errore di lettura: impossibile trasferire il file\n");
                                                                        break;
                                                                    } else {
                                                                        errors++;
                                                                        j--;
                                                                    }
                                                                }
                                                            }
                                                            if (j == Files[i].nCRCIndexPackets) {
                                                                resp.newBuilder().setRes("ok").build().writeDelimitedTo(sockets[p].getOutputStream());
                                                                monitor.append(sockets[p].getRemoteSocketAddress() + ": Trasferimento file indice completato\n");
                                                            } else {
                                                                resp.newBuilder().setRes("not").build().writeDelimitedTo(sockets[p].getOutputStream());
                                                                monitor.append(sockets[p].getRemoteSocketAddress() + ": Trasferimento file indice fallito\n");
                                                            }
                                                        }

                                                        break;
                                                    }
                                                }
                                            }
                                        }

                                        int rowIndex;
                                        resp ChunksInfoPacket = resp.parseDelimitedFrom(sockets[p].getInputStream());
                                        resp.newBuilder().setRes("ok").build().writeDelimitedTo(sockets[p].getOutputStream());
                                        synchronized (this) {
                                            if (progress == null) {
                                                progress = new Progress();
                                            }
                                            progress.setVisible(true);
                                            rowIndex = progress.addRow(sockets[p].getInetAddress().getHostAddress(), ChunksInfoPacket.getInd());
                                            ((JProgressBar) progress.comps[rowIndex + 1]).setValue(0);
                                        }

                                        while (true) {
                                            chunkReq chunkReqPacket = chunkReq.parseDelimitedFrom(sockets[p].getInputStream());
                                            if (chunkReqPacket.getInd() == -1) {
                                                ((JProgressBar) progress.comps[rowIndex + 1]).setValue(ChunksInfoPacket.getInd());
                                                break;
                                            }
                                            synchronized (this) {
                                                for (int i = 0; i < Files.length; i++) {
                                                    if (chunkReqPacket.getNam().equals(Files[i].ClientFile.getName())) {
                                                        //monitor.append(sockets[p].getRemoteSocketAddress() + ": Invio pezzo n." + chunkReqPacket.getInd() + " del file " + chunkReqPacket.getNam() + "...\n");
                                                        Files[i].setChunkToSend(chunkReqPacket.getInd());
                                                        Files[i].getChunkInfoPacket(chunkReqPacket.getInd()).writeDelimitedTo(sockets[p].getOutputStream());

                                                        resp chunkInfoRespPacket = resp.parseDelimitedFrom(sockets[p].getInputStream());
                                                        if (chunkInfoRespPacket.getRes().equals("ok")) {
                                                            int errors = 0, j;
                                                            OUTER:
                                                            for (j = 0; j < Files[i].nChunkPackets; j++) {
                                                                try {
                                                                    Files[i].buildChunkPacket(j).writeDelimitedTo(sockets[p].getOutputStream());
                                                                    errors = 0;
                                                                    resp respPacket = resp.parseDelimitedFrom(sockets[p].getInputStream());
                                                                    switch (respPacket.getRes()) {
                                                                        case "wp":
                                                                            j = respPacket.getInd() - 1;
                                                                            break;
                                                                        case "mrr":
                                                                            monitor.append(sockets[p].getRemoteSocketAddress() + ": Errore di trasferimento\n");
                                                                            break OUTER;
                                                                    }
                                                                } catch (MyExc | IOException ex) {
                                                                    if (errors == 3) {
                                                                        monitor.append(sockets[p].getRemoteSocketAddress() + ": Errore di lettura: impossibile trasferire il file\n");
                                                                        break;
                                                                    } else {
                                                                        errors++;
                                                                        j--;
                                                                    }
                                                                }
                                                            }
                                                            if (j == Files[i].nChunkPackets) {
                                                                resp.newBuilder().setRes("ok").build().writeDelimitedTo(sockets[p].getOutputStream());
                                                                ((JProgressBar) progress.comps[rowIndex + 1]).setValue(((JProgressBar) progress.comps[rowIndex + 1]).getValue() + 1);
                                                                //monitor.append(sockets[p].getRemoteSocketAddress() + ": Trasferimento completato\n");
                                                            } else {
                                                                resp.newBuilder().setRes("not").build().writeDelimitedTo(sockets[p].getOutputStream());
                                                                //monitor.append(sockets[p].getRemoteSocketAddress() + ": Trasferimento fallito\n");
                                                            }
                                                        }

                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                        
                                        progress.delRow(rowIndex);
                                        monitor.append(sockets[p].getRemoteSocketAddress() + ": Sincronizzazione completata\n");
                                    } catch (IOException ex) {
                                        JOptionPane.showMessageDialog(Dani3laClient.this, "Errore di connessione");
                                        //conButton.setText("Connetti");
                                        //syncButton.setEnabled(false);
                                    }

                                    monitor.append("Host disconnesso: " + sockets[p].getRemoteSocketAddress() + "\n");
                                    try {
                                        sockets[p].close();
                                    } catch (IOException ex) {
                                    }
                                }
                            }).start();

                            z++;
                            sockRealloc(z + 1);
                        } catch (IOException ex) {
                        }
                    }
                } catch (UnknownHostException ex) {
                    JOptionPane.showMessageDialog(Dani3laClient.this, "Impossible trovare indirizzo IP corrente");
                }
            }
        });
    }

    private void sockRealloc(int newLength) {
        Socket[] newArray = new Socket[newLength];
        System.arraycopy(sockets, 0, newArray, 0, sockets.length);
        sockets = newArray;
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
