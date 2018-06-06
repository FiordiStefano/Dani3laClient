/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dani3la.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import packet.protoPacket;

/**
 *
 * @author Stefano Fiordi
 */
public class SyncThread implements Runnable {
    
    JTextArea monitor;
    InetAddress address;
    FileHandlerClient[] Files;
    Progress progress;
    int ChunkSize;

    public SyncThread(JTextArea monitor, InetAddress address, FileHandlerClient[] Files, Progress progress, int ChunkSize) {
        this.monitor = monitor;
        this.address = address;
        this.Files = Files;
        this.progress = progress;
        this.ChunkSize = ChunkSize;
    }    
    
    private protoPacket.crcInfo createCRCInfoPacket() {
        protoPacket.crcInfo packet = protoPacket.crcInfo.newBuilder()
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

    @Override
    public void run() {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(address, 6365), 3000);
            monitor.append(LocalDateTime.now() + " | Host connesso: " + socket.getRemoteSocketAddress() + "\n");

            try {
                createCRCInfoPacket().writeDelimitedTo(socket.getOutputStream());
                monitor.append(LocalDateTime.now() + " | " + socket.getRemoteSocketAddress() + ": Inizio sincronizzazione...\n");
                while (true) {
                    protoPacket.crcReq request = protoPacket.crcReq.parseDelimitedFrom(socket.getInputStream());
                    if (request.getCrc().equals("end")) {
                        protoPacket.resp.newBuilder().setRes("ok").build().writeDelimitedTo(socket.getOutputStream());
                        monitor.append(LocalDateTime.now() + " | " + socket.getRemoteSocketAddress() + ": Fine trasferimento file indice\n");
                        break;
                    }
                    monitor.append(LocalDateTime.now() + " | " + socket.getRemoteSocketAddress() + ": Invio " + request.getCrc() + "...\n");
                    synchronized (this) {
                        for (int i = 0; i < Files.length; i++) {
                            if (Files[i].crcIndex.getName().equals(request.getCrc()) && Files[i].version == request.getVer()) {
                                Files[i].getCRCIndexInfoPacket().writeDelimitedTo(socket.getOutputStream());

                                protoPacket.resp infoRespPacket = protoPacket.resp.parseDelimitedFrom(socket.getInputStream());
                                if (infoRespPacket.getRes().equals("ok")) {
                                    monitor.append(LocalDateTime.now() + " | " + socket.getRemoteSocketAddress() + ": Inizio trasferimento " + Files[i].crcIndex.getName() + "...\n");
                                    int errors = 0, j = 0;
                                    OUTER:
                                    for (; j < Files[i].nCRCIndexPackets; j++) {
                                        try {
                                            Files[i].buildCRCIndexPacket(j).writeDelimitedTo(socket.getOutputStream());
                                            errors = 0;
                                            protoPacket.resp respPacket = protoPacket.resp.parseDelimitedFrom(socket.getInputStream());
                                            switch (respPacket.getRes()) {
                                                case "wp":
                                                    j = respPacket.getInd() - 1;
                                                    break;
                                                case "mrr":
                                                    monitor.append(LocalDateTime.now() + " | " + socket.getRemoteSocketAddress() + ": Errore di trasferimento\n");
                                                    break OUTER;
                                            }
                                        } catch (MyExc | IOException ex) {
                                            if (errors == 3) {
                                                monitor.append(LocalDateTime.now() + " | " + socket.getRemoteSocketAddress() + ": Errore di lettura: impossibile trasferire il file\n");
                                                break;
                                            } else {
                                                errors++;
                                                j--;
                                            }
                                        }
                                    }
                                    if (j == Files[i].nCRCIndexPackets) {
                                        protoPacket.resp.newBuilder().setRes("ok").build().writeDelimitedTo(socket.getOutputStream());
                                        monitor.append(LocalDateTime.now() + " | " + socket.getRemoteSocketAddress() + ": Trasferimento file indice completato\n");
                                    } else {
                                        protoPacket.resp.newBuilder().setRes("not").build().writeDelimitedTo(socket.getOutputStream());
                                        monitor.append(LocalDateTime.now() + " | " + socket.getRemoteSocketAddress() + ": Trasferimento file indice fallito\n");
                                    }
                                }

                                break;
                            }
                        }
                    }
                }

                int rowIndex;
                protoPacket.resp ChunksInfoPacket = protoPacket.resp.parseDelimitedFrom(socket.getInputStream());
                protoPacket.resp.newBuilder().setRes("ok").build().writeDelimitedTo(socket.getOutputStream());
                synchronized (this) {
                    progress.setVisible(true);
                    rowIndex = progress.addRow(socket.getInetAddress().getHostAddress(), ChunksInfoPacket.getInd());
                    ((JProgressBar) progress.comps[rowIndex + 1]).setValue(0);
                }

                while (true) {
                    protoPacket.chunkReq chunkReqPacket = protoPacket.chunkReq.parseDelimitedFrom(socket.getInputStream());
                    if (chunkReqPacket.getInd() == -1) {
                        ((JProgressBar) progress.comps[rowIndex + 1]).setValue(ChunksInfoPacket.getInd());
                        break;
                    }
                    synchronized (this) {
                        for (int i = 0; i < Files.length; i++) {
                            if (chunkReqPacket.getNam().equals(Files[i].ClientFile.getName())) {
                                //monitor.append(sockets[p].getRemoteSocketAddress() + ": Invio pezzo n." + chunkReqPacket.getInd() + " del file " + chunkReqPacket.getNam() + "...\n");
                                Files[i].setChunkToSend(chunkReqPacket.getInd());
                                Files[i].getChunkInfoPacket(chunkReqPacket.getInd()).writeDelimitedTo(socket.getOutputStream());

                                protoPacket.resp chunkInfoRespPacket = protoPacket.resp.parseDelimitedFrom(socket.getInputStream());
                                if (chunkInfoRespPacket.getRes().equals("ok")) {
                                    int errors = 0, j;
                                    OUTER:
                                    for (j = 0; j < Files[i].nChunkPackets; j++) {
                                        try {
                                            Files[i].buildChunkPacket(j).writeDelimitedTo(socket.getOutputStream());
                                            errors = 0;
                                            protoPacket.resp respPacket = protoPacket.resp.parseDelimitedFrom(socket.getInputStream());
                                            switch (respPacket.getRes()) {
                                                case "wp":
                                                    j = respPacket.getInd() - 1;
                                                    break;
                                                case "mrr":
                                                    monitor.append(socket.getRemoteSocketAddress() + ": Errore di trasferimento\n");
                                                    break OUTER;
                                            }
                                        } catch (MyExc | IOException ex) {
                                            if (errors == 3) {
                                                monitor.append(LocalDateTime.now() + " | " + socket.getRemoteSocketAddress() + ": Errore di lettura: impossibile trasferire il file\n");
                                                break;
                                            } else {
                                                errors++;
                                                j--;
                                            }
                                        }
                                    }
                                    if (j == Files[i].nChunkPackets) {
                                        protoPacket.resp.newBuilder().setRes("ok").build().writeDelimitedTo(socket.getOutputStream());
                                        ((JProgressBar) progress.comps[rowIndex + 1]).setValue(((JProgressBar) progress.comps[rowIndex + 1]).getValue() + 1);
                                        //monitor.append(sockets[p].getRemoteSocketAddress() + ": Trasferimento completato\n");
                                    } else {
                                        protoPacket.resp.newBuilder().setRes("not").build().writeDelimitedTo(socket.getOutputStream());
                                        //monitor.append(sockets[p].getRemoteSocketAddress() + ": Trasferimento fallito\n");
                                    }
                                }

                                break;
                            }
                        }
                    }
                }

                //progress.delRow(rowIndex);
                monitor.append(LocalDateTime.now() + " | " + socket.getRemoteSocketAddress() + ": Sincronizzazione completata\n");
            } catch (IOException ex) {
                monitor.append(LocalDateTime.now() + " | Errore di connessione: " + address.getHostAddress() + "\n");
            }

            monitor.append(LocalDateTime.now() + " | Host disconnesso: " + socket.getRemoteSocketAddress() + "\n");
            try {
                socket.close();
            } catch (IOException ex) {
            }
        } catch (IOException ex) {
        }
    }
}
